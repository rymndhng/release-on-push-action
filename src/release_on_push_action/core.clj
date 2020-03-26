(ns release-on-push-action.core
  (:require [babashka.curl :as curl]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn content-from-env [args]
  {:token                     (System/getenv "GITHUB_TOKEN")
   :repo                      (System/getenv "GITHUB_REPOSITORY")
   :sha                       (System/getenv "GITHUB_SHA")
   :input-bump-version-scheme (System/getenv "INPUT_BUMP_VERSION_SCHEME")
   :dry-run                   (contains? (set args) "--dry-run")})

(defn fetch-related-prs
  "See https://developer.github.com/v3/pulls/#list-pull-requests"
  [context]
  (let [resp (curl/get "https://api.github.com/search/issues"
                       {:headers      {"Authorization" (str "token " (:token context))}
                        :query-params {"q" (format "repo:%s type:pr is:closed is:merged SHA:%s"
                                                   (:repo context)
                                                   (:sha context))}})]
    (json/parse-string resp true)))

(defn fetch-commit
  "See https://developer.github.com/v3/repos/commits/"
  [context]
  (let [resp (curl/get
              (format "https://api.github.com/repos/%s/commits/%s" (:repo context) (:sha context))
              {:headers {"Authorization" (str "token " (:token context))}})]
    (json/parse-string resp true)))

(defn fetch-latest-release
  "See https://developer.github.com/v3/repos/releases/#get-the-latest-release"
  [context]
  (let [resp (curl/get
              (format "https://api.github.com/repos/%s/releases/latest" (:repo context))
              {:headers {"Authorization" (str "token " (:token context))}})]
    (json/parse-string resp true)))

(defn fetch-related-data [context]
  {:related-prs    (fetch-related-prs context)
   :commit         (fetch-commit context)
   :latest-release (fetch-latest-release context)})

(defn get-labels [related-prs]
  (->> related-prs :items (map :labels) flatten (map :name) set))

(defn bump-version-scheme [context related-data]
  (let [labels (get-labels (:related-prs related-data))]
    (cond
      (contains? labels "release:major") :major
      (contains? labels "release:minor") :minor
      (contains? labels "release:patch") :patch
      :default (keyword (:input-bump-version-scheme context)))))

(defn get-tagged-version [latest-release]
  (let [tag (get latest-release :tag-name "0.0.0")]
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
    (= "norelease" (bump-version-scheme context related-data))
    "Skipping release, no version bump found."

    (contains? (get-in related-data [:commit :message]) "[norelease]")
    "Skipping release. Reason: git commit title contains [norelease]"

    (contains? (get-labels (get-in related-data [:related-prs])) "norelease")
    "Skipping release. Reason: related PR has label norelease"))

(defn generate-new-release-data [context related-data]
  ;; TODO: handle case when bump version scheme is invalid
  (let [bump-version-scheme (bump-version-scheme context related-data)
        current-version     (get-tagged-version (:latest-release context))
        next-version        (semver-bump current-version bump-version-scheme)]
    {:tag_name (str "v" next-version)
     :target_commitish (:sha context)
     :name next-version
     :body (str "Version " next-version)
     :draft false
     :prerelease true}))

(defn create-new-release! [context new-release-data]
  (curl/post (format "https://api.github.com/repos/%s/releases" (:repo context))
             {:body    (json/generate-string new-release-data)
              :headers {"Authorization" (str "token " (:token context))}}))

(defn -main [& args]
  (println "Starting process")
  (let [context      (content-from-env args)
        related-data (fetch-related-data context)]
    (when-let [reason (norelease-reason context related-data)]
      (println "Skipping release: " reason)
      (System/exit 0))

    (let [release-data (generate-new-release-data context related-data)]
      (if (:dry-run context)
        (do
          (println "Dry Run. Not performing release\n" (json/generate-string release-data {:pretty true})))
        (do
          (println "Executing Release\n" (json/generate-string release-data {:pretty true}))
          ;; (println (create-new-release! context release-data))

          )))))
