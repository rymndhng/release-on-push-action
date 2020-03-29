(ns release-on-push-action.core
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.string :as str]

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
  "Creates a context from environment variables and arguments to the main function."
  [args]
  {:token               (getenv-or-throw "GITHUB_TOKEN")
   :repo                (getenv-or-throw "GITHUB_REPOSITORY")
   :sha                 (getenv-or-throw "GITHUB_SHA")
   :bump-version-scheme (assert-valid-bump-version-scheme
                         (try
                           (getenv-or-throw "INPUT_BUMP_VERSION_SCHEME")
                           (catch Exception ex
                             ;; support the old and poorly documented name: strategy
                             (if (input-strategy-set?)
                               (getenv-or-throw "INPUT_STRATEGY")
                               (throw ex)))))
   :dry-run             (contains? (set args) "--dry-run")})

;; -- Version Bumping Logic  ---------------------------------------------------
(defn fetch-related-data [context]
  {:related-prs    (:body (github/fetch-related-prs context))
   :commit         (:body (github/fetch-commit context))
   :latest-release (:body (github/fetch-latest-release context))})

(defn get-labels [related-prs]
  (->> related-prs :items (map :labels) flatten (map :name) set))

(defn bump-version-scheme [context related-data]
  (let [labels (get-labels (:related-prs related-data))]
    (cond
      (contains? labels "release:major") :major
      (contains? labels "release:minor") :minor
      (contains? labels "release:patch") :patch
      :default (keyword (:bump-version-scheme context)))))

(defn get-tagged-version [latest-release]
  (let [tag (get latest-release :tag_name "0.0.0")]
    (if (.startsWith tag "v")
      (subs tag 1)
      tag)))

(defn safe-inc [n]
  (inc (or n 0)))

(defn semver-bump [version bump]
  (let [[major minor patch] (map #(Integer/parseInt %) (str/split version #"\."))
        next-version (condp = bump
                       :major [(safe-inc major) minor patch]
                       :minor [major (safe-inc minor) patch]
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

        ;; assumption: target_commitish is always a sha and not a reference
        summary-since-last-release (->> (github/list-commits-to-base context (:target_commitish current-version))
                                        (map github/commit-summary)
                                        (str/join "\n"))]

    {:tag_name         (str "v" next-version)
     :target_commitish (:sha context)
     :name             next-version
     :body             (format "Version %s\n\n### Commits\n\n%s" next-version summary-since-last-release)
     :draft            false
     :prerelease       true}))

(defn create-new-release! [context new-release-data]
  (curl/post (format "https://api.github.com/repos/%s/releases" (:repo context))
             {:body    (json/generate-string new-release-data)
              :headers {"Authorization" (str "token " (:token context))}}))

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
          (println "Dry Run. Not performing release\n" (json/generate-string release-data {:pretty true})))
        (do
          (println "Executing Release\n" (json/generate-string release-data {:pretty true}))
          (println (create-new-release! context release-data))
          )))))
