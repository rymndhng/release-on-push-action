(ns release-on-push-action.github
  (:require [babashka.curl :as curl]
            [clojure.string :as str]
            [cheshire.core :as json]))

;; -- Generic HTTP Helpers  ----------------------------------------------------
(defn link-header->map
  "Converts link header into a map of rel -> link. This implementation is not standards compliant.

  See https://tools.ietf.org/html/rfc5988"
  [link-header]
  (reduce (fn [acc [_ link rel]]
            (assoc acc (keyword rel) link))
          {}
          (re-seq #"<([^>]+)>; rel=\"([^\"]+)\",?" link-header)))

(defn with-links [response]
  (if-let [val (get-in response [:headers "link"])]
    (assoc response :links (link-header->map val))
    response))

(defn parse-response [resp]
  (-> resp
      (with-links)
      (update :body json/parse-string true)))

;; -- Pagination helpers using token  ------------------------------------------
(defn follow-link [context link]
  (parse-response
   (curl/get link
             {:headers {"Authorization" (str "token " (:token context))}})))

(defn paginate
  "Paginate a resopnse with a context object"
  [context response]
  (if-let [next-link (get-in response [:links :next])]
    (cons response
          (lazy-seq
           (paginate context (follow-link context next-link))))
    [response]))

;; -- Github PRs API  ----------------------------------------------------------
(defn fetch-related-prs
  "See https://developer.github.com/v3/pulls/#list-pull-requests"
  [context]
  (parse-response
   (curl/get "https://api.github.com/search/issues"
             {:headers      {"Authorization" (str "token " (:token context))}
              :query-params {"q" (format "repo:%s type:pr is:closed is:merged SHA:%s" (:repo context) (:sha context))}})))

;; -- Github Releases API  -----------------------------------------------------
(defn fetch-latest-release
  "Gets the latest commit. Returns nil when there is no release.

  See https://developer.github.com/v3/repos/releases/#get-the-latest-release"
  [context]
  (try
    (parse-response
     (curl/get
      (format "https://api.github.com/repos/%s/releases/latest" (:repo context))
      {:headers {"Authorization" (str "token " (:token context))}}))
    (catch clojure.lang.ExceptionInfo ex
      (cond
        ;; No previous release created
        (= 404 (:status (ex-data ex))) nil

        :else (throw ex)))))

;; -- Github Commit API  -------------------------------------------------------
(defn fetch-commit
  "See https://developer.github.com/v3/repos/commits/"
  [context]
  (parse-response
   (curl/get (format "https://api.github.com/repos/%s/commits/%s" (:repo context) (:sha context))
             {:headers {"Authorization" (str "token " (:token context))}})))

(defn list-commits
  "Gets all commits between two commit shas.

  See https://developer.github.com/v3/repos/commits/"
  [context]
  (parse-response
   (curl/get (format "https://api.github.com/repos/%s/commits" (:repo context))
             {:headers      {"Authorization" (str "token " (:token context))}
              :query-params {"sha" (:sha context)}})))

(defn list-commits-to-base
  "Returns a lazy sequence of commits from :sha of context to base. Similar to git log base.. (:sha context)

  If base is nil, will return all commits since all time.
  "
  [context base]
  (->> (paginate context (list-commits context))
       (map #(-> % :body))
       (flatten)
       (take-while #(not= base (:sha %)))))

;; -- Formatting  --------------------------------------------------------------
(defn commit-title [commit]
  (-> (get-in commit [:commit :message] "")
      (str/split #"\n")
      first))

(defn commit-summary [commit]
  (format "- [%s] %s" (subs (:sha commit) 0 8) (commit-title commit)))

(comment
  ;; used for testing
  (def context {:repo "rymndhng/release-on-push-action"
                :sha "167c690247d0933acde636d72352bcd67e33724b"})

  ;; this should match
  ;; ❯ git log --oneline 7b6741..167c69
  (println (str/join "\n" (map commit-summary (list-commits-to-base context "7b67416b182074bad5a5d1103d48a8da463a30ec")))))
