(ns release-on-push-action.core
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [release-on-push-action.github :as github]))

;; -- Configuration Parsing  ---------------------------------------------------
(defn getenv-or-throw [name]
  (let [val (System/getenv name)]
    (when (empty? val)
      (throw (ex-info (str "Expected environment variable to be set: " name)
                      {:env/name name})))
    val))

(defn input-strategy-set? []
  (if (System/getenv "INPUT_STRATEGY")
    (do
      (println "WARNING: the action property `strategy` has been renamed `bump_version_scheme`. Support for `strategy` will be removed in the future. See the rymndhng/release-on-push-action README for the current configuration")
      true)
    false))

(defn assert-valid-bump-version-scheme [bump-version-scheme]
  (when-not (contains? #{"major" "minor" "patch" "norelease"} bump-version-scheme)
    (throw (ex-info (str "Invalid bump-version-scheme. Expected one of major|minor|patch|norelease. Got: " bump-version-scheme) {:bump-version-scheme bump-version-scheme})))
  bump-version-scheme)

(defn context-from-env
  "Creates a context from environment variables and arguments to the main function.

  See https://docs.github.com/en/actions/reference/environment-variables.
  "
  [args]
  {:token               (getenv-or-throw "GITHUB_TOKEN")
   :repo                (getenv-or-throw "GITHUB_REPOSITORY")
   :sha                 (getenv-or-throw "GITHUB_SHA")
   :github/api-url      (getenv-or-throw "GITHUB_API_URL")
   :github/output       (System/getenv "GITHUB_OUTPUT")
   :input/max-commits   (Integer/parseInt (getenv-or-throw "INPUT_MAX_COMMITS"))
   :input/release-body  (System/getenv "INPUT_RELEASE_BODY")
   :input/tag-prefix    (System/getenv "INPUT_TAG_PREFIX") ;defaults to "v", see default in action.yml
   :input/release-name  (System/getenv "INPUT_RELEASE_NAME") ;defaults to "<RELEASE_TAG>", see default in action.yml
   :input/use-github-release-notes (Boolean/parseBoolean (System/getenv "INPUT_USE_GITHUB_RELEASE_NOTES"))
   :bump-version-scheme (assert-valid-bump-version-scheme
                         (try
                           (getenv-or-throw "INPUT_BUMP_VERSION_SCHEME")
                           (catch Exception ex
                             ;; support the old and poorly documented name: strategy
                             (if (input-strategy-set?)
                               (getenv-or-throw "INPUT_STRATEGY")
                               (throw ex)))))
   :dry-run             (or (Boolean/parseBoolean (System/getenv "INPUT_DRY_RUN"))
                            (contains? (set args) "--dry-run"))})

;; -- Version Bumping Logic  ---------------------------------------------------
(defn fetch-related-data [context]
  (let [latest-release (:body (github/fetch-latest-release context))]
    {:related-prs           (:body (github/fetch-related-prs context))
     :commit                (:body (github/fetch-commit context))
     :latest-release        latest-release
     :latest-release-commit (when-let [tag (:tag_name latest-release)]
                              (:body (github/fetch-commit (assoc context :sha tag))))}))

(defn get-labels [related-prs]
  (->> related-prs (map :labels) flatten (map :name) set))

(defn bump-version-scheme [context related-data]
  (let [labels (get-labels (:related-prs related-data))]
    (cond
      (contains? labels "release:major") :major
      (contains? labels "release:minor") :minor
      (contains? labels "release:patch") :patch
      :else (keyword (:bump-version-scheme context)))))

(defn get-tagged-version [latest-release]
  (let [tag      (get latest-release :tag_name "0.0.0")
        [prefix] (str/split tag #"\d+\.\d+\.\d+")] ;this strips any leading characters before the semver string
    (subs tag (count prefix))))

(defn safe-inc [n]
  (inc (or n 0)))

(defn semver-bump [version bump]
  (let [[major minor patch] (map #(Integer/parseInt %) (str/split version #"\."))
        next-version (condp = bump
                       :major [(safe-inc major) 0 0]
                       :minor [major (safe-inc minor) 0]
                       :patch [major minor (safe-inc patch)])]
    (str/join "." next-version)))

(defn norelease-reason [context related-data]
  (cond
    (= :norelease (bump-version-scheme context related-data))
    "Skipping release, no version bump found."

    (str/includes? (github/commit-title (:commit related-data)) "[norelease]")
    "Skipping release. Reason: git commit title contains [norelease]"

    (contains? (get-labels (get-in related-data [:related-prs])) "norelease")
    "Skipping release. Reason: related PR has label norelease"))

(defn generate-new-release-data [context related-data]
  (let [bump-version-scheme (bump-version-scheme context related-data)
        current-version     (get-tagged-version (:latest-release related-data))
        next-version        (semver-bump current-version bump-version-scheme)
        base-commit         (get-in related-data [:latest-release-commit :sha])
        tag-name            (str (:input/tag-prefix context) next-version)

        ;; this is a lazy sequence
        commits-since-last-release (->> (github/list-commits-to-base context base-commit)
                                        (take (:input/max-commits context))
                                        (map github/commit-summary))

        body (with-out-str
               (printf "Version %s\n\n" next-version)
               (when-let [body (not-empty (:input/release-body context))]
                 (println body)
                 (println))

               ;; Do not include our custom commit summary if using Github Release Notes
               (when-not (:input/use-github-release-notes context)
                 (printf "### Commits\n\n")
                 (doseq [commit commits-since-last-release]
                   (println commit))))]
    {:tag_name               tag-name
     :target_commitish       (:sha context)
     :name                   (-> (:input/release-name context)
                                 (str/replace "<RELEASE_VERSION>" next-version)
                                 (str/replace "<RELEASE_TAG>" tag-name))
     :body                   body
     :draft                  false
     :prerelease             false
     :generate_release_notes (:input/use-github-release-notes context)}))

(defn create-new-release! [context new-release-data]
  ;; Use a file because the release data may be too large for an inline curl arg
  (let [file (java.io.File/createTempFile "release" ".json")]
    (.deleteOnExit file)
    (json/encode-stream new-release-data (io/writer file))
    (curl/post (format "%s/repos/%s/releases" (:github/api-url context) (:repo context))
               {:body    file
                :headers (github/headers context)})))

(def EOL (System/getProperty "line.separator"))

(defn prepare-key-value
  "Escapes text for the set-output command in Github Actions.

  See https://github.community/t/set-output-truncates-multiline-strings/16852
  and https://github.com/actions/toolkit/blob/ffb7e3e14ed5e28ae00e9c49ba02b2764d57a6b7/packages/core/src/file-command.ts#L28
  "
  ([key value]
   (prepare-key-value key value (format "delimiter_%s" (random-uuid))))
  ([key value delimiter]
   (str/join "" [key "<<" delimiter EOL value EOL delimiter])))

(defn set-output-parameters!
  "Sets output parameters for additional tasks to consume.

  See https://help.github.com/en/actions/reference/workflow-commands-for-github-actions#setting-an-output-parameter
  "
  [context release-data]
  (let [out (if-let [output (not-empty (:github/output context))]
              (-> output io/file io/writer)
              (do
                (println "[set-output-parmaeters] simulated writing to file:")
                *out*))]
    (binding [*out* out]
      (println (prepare-key-value "tag_name" (:tag_name release-data)))
      (println (prepare-key-value "version" (:name release-data)))
      (println (prepare-key-value "upload_url" (:upload_url release-data)))
      (println (prepare-key-value "body" (:body release-data))))))

(defn -main [& args]
  (let [_            (println "Starting process...")
        context      (context-from-env args)
        _            (println "Received context" context) ; in github actions the secrets are printed as '***'
        _            (println "Fetching related data...")
        related-data (fetch-related-data context)]
    (when-let [reason (norelease-reason context related-data)]
      (println "Skipping release: " reason)
      (System/exit 0))

    (println "Generating release...")
    (let [release-data (generate-new-release-data context related-data)]
      (if (:dry-run context)
        (do
          (println "Dry Run. Not performing release\n" (json/generate-string release-data {:pretty true}))
          (println "Release Body")
          (println (:body release-data)))
        (do
          (println "Executing Release\n" (json/generate-string release-data {:pretty true}))
          (println (create-new-release! context release-data))))
      (set-output-parameters! context release-data))))
