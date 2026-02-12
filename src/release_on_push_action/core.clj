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
  (when-not (contains? #{"major" "minor" "patch" "keep" "norelease"} bump-version-scheme)
    (throw (ex-info (str "Invalid bump-version-scheme. Expected one of major|minor|patch|keep|norelease. Got: " bump-version-scheme) {:bump-version-scheme bump-version-scheme})))
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
   :input/tag-suffix    (System/getenv "INPUT_TAG_SUFFIX") ;defaults to "", see default in action.yml
   :input/use-prerelease (or (System/getenv "INPUT_USE_PRERELEASE") "auto")
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

(defn extract-suffix-from-labels
  "Extracts tag suffix from PR labels.

  Looks for labels in format 'tag-suffix:VALUE' where VALUE is the suffix.
  Examples: 'tag-suffix:rc2' -> 'rc2', 'tag-suffix:-alpha' -> '-alpha'

  Returns nil if no tag-suffix label found."
  [labels]
  (some (fn [label]
          (when (str/starts-with? label "tag-suffix:")
            (subs label (count "tag-suffix:"))))
        labels))

(defn get-effective-tag-suffix
  "Gets the effective tag suffix, prioritizing PR labels over context.

  Priority: PR label > context input"
  [context related-data]
  (let [labels (get-labels (:related-prs related-data))
        label-suffix (extract-suffix-from-labels labels)]
    (or label-suffix (:input/tag-suffix context))))

(defn bump-version-scheme [context related-data]
  (let [labels (get-labels (:related-prs related-data))]
    (cond
      (contains? labels "release:major") :major
      (contains? labels "release:minor") :minor
      (contains? labels "release:patch") :patch
      (contains? labels "release:keep")  :keep
      :else (keyword (:bump-version-scheme context)))))

(defn get-tagged-version [latest-release]
  (let [tag      (get latest-release :tag_name "0.0.0")
        [prefix] (str/split tag #"\d+\.\d+\.\d+")] ;this strips any leading characters before the semver string
    (subs tag (count prefix))))

(defn extract-suffix-from-tag
  "Extracts the suffix from a tag name (e.g., 'v2.0.0-rc1' -> '-rc1', 'v2.0.0' -> '')"
  [tag-name]
  (if-let [match (re-find #"\d+\.\d+\.\d+(.*)" tag-name)]
    (second match)
    ""))

(defn safe-inc [n]
  (inc (or n 0)))

(defn semver-bump [version bump]
  (let [;; Extract just the semver portion (major.minor.patch) stripping any suffix
        ;; Handles both "2.0.0-rc1" and "2.0.0rc1" formats
        base-version (or (re-find #"^\d+\.\d+\.\d+" version) version)
        [major minor patch] (map #(Integer/parseInt %) (str/split base-version #"\."))
        next-version (condp = bump
                       :major [(safe-inc major) 0 0]
                       :minor [major (safe-inc minor) 0]
                       :patch [major minor (safe-inc patch)]
                       :keep  [major minor patch])]
    (str/join "." next-version)))

(defn should-mark-prerelease?
  "Determines if a release should be marked as a pre-release.

  Rules:
  - 'true' -> always true
  - 'false' -> always false
  - 'auto' -> true if tag-suffix is non-empty, false otherwise
  "
  [context related-data]
  (let [use-prerelease (:input/use-prerelease context)
        effective-suffix (get-effective-tag-suffix context related-data)]
    (case use-prerelease
      "true"  true
      "false" false
      "auto"  (boolean (seq effective-suffix))
      ;; default: treat invalid values as auto
      (boolean (seq effective-suffix)))))

(defn validate-keep-bump-scheme
  "Validates that 'keep' bump scheme is used correctly.

  Requirements:
  1. Current tag_suffix must be present (non-empty)
  2. Previous version must have a suffix
  3. New suffix must be different from previous suffix

  Returns nil if valid, or an error message string if invalid."
  [context related-data]
  (let [current-suffix (get-effective-tag-suffix context related-data)
        previous-tag   (get-in related-data [:latest-release :tag_name])
        previous-suffix (when previous-tag (extract-suffix-from-tag previous-tag))]
    (cond
      ;; No current suffix provided
      (or (nil? current-suffix) (empty? current-suffix))
      "Cannot use 'keep' bump scheme without a tag_suffix. Please provide a suffix (e.g., -rc2, -beta)."

      ;; No previous release exists
      (nil? previous-tag)
      "Cannot use 'keep' bump scheme for the first release. Use 'minor', 'major', or 'patch' instead."

      ;; Previous version has no suffix
      (or (nil? previous-suffix) (empty? previous-suffix))
      (format "Cannot use 'keep' bump scheme because previous version '%s' has no suffix. Use 'minor', 'major', or 'patch' to bump the version first." previous-tag)

      ;; Current suffix same as previous suffix
      (= current-suffix previous-suffix)
      (format "Cannot use 'keep' bump scheme with the same suffix. Previous version '%s' already uses suffix '%s'. Please provide a different suffix." previous-tag previous-suffix)

      ;; All validations passed
      :else nil)))

(defn norelease-reason [context related-data]
  (let [scheme (bump-version-scheme context related-data)]
    (cond
      (= :norelease scheme)
      "Skipping release, no version bump found."

      (str/includes? (github/commit-title (:commit related-data)) "[norelease]")
      "Skipping release. Reason: git commit title contains [norelease]"

      (contains? (get-labels (get-in related-data [:related-prs])) "norelease")
      "Skipping release. Reason: related PR has label norelease"

      ;; Validate 'keep' bump scheme
      (= :keep scheme)
      (validate-keep-bump-scheme context related-data))))

(defn generate-new-release-data [context related-data]
  (let [bump-version-scheme (bump-version-scheme context related-data)
        current-version     (get-tagged-version (:latest-release related-data))
        next-version        (semver-bump current-version bump-version-scheme)
        base-commit         (get-in related-data [:latest-release-commit :sha])
        effective-suffix    (get-effective-tag-suffix context related-data)
        tag-name            (str (:input/tag-prefix context) next-version effective-suffix)

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
     :prerelease             (should-mark-prerelease? context related-data)
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
