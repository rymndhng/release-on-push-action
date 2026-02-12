(ns release-on-push-action.core-test
  (:require [clojure.test :refer [deftest is are testing]]
            [clojure.string :as str]
            [release-on-push-action.core :as sut]))

(deftest get-tagged-version
  (are [expected tag-name] (= expected (sut/get-tagged-version {:tag_name tag-name}))
    "0.0.0" "v0.0.0"
    "0.0.0" "0.0.0"
    "1.0.0" "v1.0.0"
    "1.0.0" "1.0.0"
    "1.0.0" "whatever1.0.0"
    "1.0.0" "foo1-1.0.0"
    "1.0.0" "111-1.0.0"))

(deftest prepare-key-value
  (are [expected input] (= expected (sut/prepare-key-value "foo" input "EOF"))
    "foo<<EOF\nhello world\nEOF"   "hello world"
    "foo<<EOF\nhello%20world\nEOF" "hello%20world"
    "foo<<EOF\nhello\nworld\nEOF"  "hello\nworld"
    "foo<<EOF\nhello\rworld\nEOF"  "hello\rworld"))

(deftest semver-bump
  (testing "patch bump"
    (are [expected input] (= expected (sut/semver-bump input :patch))
        "0.0.1" "0.0.0"
        "0.0.2" "0.0.1"
        "0.1.1" "0.1.0"
        "1.1.1" "1.1.0"))

  (testing "minor bump"
    (are [expected input] (= expected (sut/semver-bump input :minor))
        "0.1.0" "0.0.0"
        "0.1.0" "0.0.1"
        "0.2.0" "0.1.0"
        "1.2.0" "1.1.0"))

  (testing "major bump"
    (are [expected input] (= expected (sut/semver-bump input :major))
      "1.0.0" "0.0.0"
      "1.0.0" "0.0.1"
      "1.0.0" "0.1.0"
      "2.0.0" "1.1.0"))

  (testing "keep version"
    (are [expected input] (= expected (sut/semver-bump input :keep))
      "0.0.0" "0.0.0"
      "1.2.3" "1.2.3"
      "2.0.0" "2.0.0"))

  (testing "bump with suffixed versions"
    (testing "strips suffix before bumping (with dash)"
      (are [expected input bump] (= expected (sut/semver-bump input bump))
        "2.0.0" "2.0.0-rc1"   :keep
        "3.0.0" "2.0.0-alpha" :major
        "2.1.0" "2.0.0-beta"  :minor
        "2.0.1" "2.0.0-rc2"   :patch))
    (testing "strips suffix before bumping (without dash)"
      (are [expected input bump] (= expected (sut/semver-bump input bump))
        "2.0.0" "2.0.0rc1"    :keep
        "3.0.0" "2.0.0alpha"  :major
        "2.1.0" "2.0.0beta"   :minor
        "2.0.1" "2.0.0rc2"    :patch))))

(deftest extract-suffix-from-tag
  (testing "extract suffix from various tag formats"
    (are [expected tag] (= expected (sut/extract-suffix-from-tag tag))
      "-rc1"   "v2.0.0-rc1"
      "-alpha" "v1.0.0-alpha"
      "-beta"  "2.0.0-beta"
      ""       "v2.0.0"
      ""       "1.0.0"
      "-rc2"   "prefix2.0.0-rc2"
      "rc1"    "v2.0.0rc1"    ; without dash
      "alpha"  "1.0.0alpha"   ; without dash
      "beta2"  "2.0.0beta2")))

(deftest validate-keep-bump-scheme
  (testing "missing current suffix"
    (is (string? (sut/validate-keep-bump-scheme
                  {:input/tag-suffix ""}
                  {:latest-release {:tag_name "v1.0.0-rc1"}}))))

  (testing "no previous release"
    (is (string? (sut/validate-keep-bump-scheme
                  {:input/tag-suffix "-rc2"}
                  {:latest-release nil}))))

  (testing "previous version has no suffix"
    (is (string? (sut/validate-keep-bump-scheme
                  {:input/tag-suffix "-rc1"}
                  {:latest-release {:tag_name "v1.0.0"}}))))

  (testing "same suffix as previous (with dash)"
    (is (string? (sut/validate-keep-bump-scheme
                  {:input/tag-suffix "-rc1"}
                  {:latest-release {:tag_name "v1.0.0-rc1"}}))))

  (testing "valid: different suffix (with dash)"
    (is (nil? (sut/validate-keep-bump-scheme
               {:input/tag-suffix "-rc2"}
               {:latest-release {:tag_name "v1.0.0-rc1"}}))))

  (testing "same suffix as previous (without dash)"
    (is (string? (sut/validate-keep-bump-scheme
                  {:input/tag-suffix "rc1"}
                  {:latest-release {:tag_name "v1.0.0rc1"}}))))

  (testing "valid: different suffix (without dash)"
    (is (nil? (sut/validate-keep-bump-scheme
               {:input/tag-suffix "rc2"}
               {:latest-release {:tag_name "v1.0.0rc1"}})))))

(deftest extract-suffix-from-labels
  (testing "extracts suffix from tag-suffix: label"
    (are [expected labels] (= expected (sut/extract-suffix-from-labels labels))
      "rc2"    #{"release:minor" "tag-suffix:rc2"}
      "-alpha" #{"tag-suffix:-alpha" "bug"}
      "beta"   #{"tag-suffix:beta"}
      nil      #{"release:major" "bug"}
      nil      #{})))

(deftest get-effective-tag-suffix
  (testing "prioritizes label over context"
    (is (= "rc2"
           (sut/get-effective-tag-suffix
            {:input/tag-suffix "-rc1"}
            {:related-prs [{:labels [{:name "tag-suffix:rc2"}]}]}))))

  (testing "falls back to context when no label"
    (is (= "-rc1"
           (sut/get-effective-tag-suffix
            {:input/tag-suffix "-rc1"}
            {:related-prs []}))))

  (testing "returns nil when neither label nor context"
    (is (= nil
           (sut/get-effective-tag-suffix
            {:input/tag-suffix nil}
            {:related-prs []})))))

(deftest should-mark-prerelease?
  (let [empty-related-data {:related-prs []}]
    (testing "explicit true"
      (is (= true (sut/should-mark-prerelease?
                   {:input/use-prerelease "true" :input/tag-suffix ""}
                   empty-related-data))))

    (testing "explicit false"
      (is (= false (sut/should-mark-prerelease?
                    {:input/use-prerelease "false" :input/tag-suffix "-alpha"}
                    empty-related-data))))

    (testing "auto mode with suffix"
      (is (= true (sut/should-mark-prerelease?
                   {:input/use-prerelease "auto" :input/tag-suffix "-alpha"}
                   empty-related-data))))

    (testing "auto mode without suffix"
      (is (= false (sut/should-mark-prerelease?
                    {:input/use-prerelease "auto" :input/tag-suffix ""}
                    empty-related-data))))

    (testing "auto mode with label-based suffix"
      (is (= true (sut/should-mark-prerelease?
                   {:input/use-prerelease "auto" :input/tag-suffix ""}
                   {:related-prs [{:labels [{:name "tag-suffix:rc2"}]}]}))))))

(def base-ctx
  {:token               (System/getenv "GITHUB_TOKEN") ;use Github Actions Token as test token
   :github/api-url      "https://api.github.com"
   :input/max-commits   5
   :input/release-body  ""
   :input/tag-prefix    ""
   :input/tag-suffix    ""
   :input/use-prerelease "auto"
   :input/use-github-release-notes false
   :input/release-name  "<RELEASE_TAG>"
   :bump-version-scheme "minor"
   :dry-run             true})

;; -- Integration Tests  -------------------------------------------------------
(defmacro def-fixture
  "Creates a Repository fixture whose value can be resolved to a pair of [ctx related-data]"
  [name & ctx-args]
  `(def ~name
     (let [ctx# (merge base-ctx (hash-map ~@ctx-args))]
       (delay [ctx# (sut/fetch-related-data ctx#)]))))

;; This project has 11 commits and does not have any tags or releases.
;; https://github.com/release-on-push-action/test-project-without-releases
(def-fixture fixture-project-without-release
  :repo "release-on-push-action/test-project-without-releases"
  :sha  "946550e635d4bae50cb5cec434678b439b59c659")

;; This project has 11 commits and has a release tag: v0.1.0 @ 536a71ab35383e0a2e8d8e3a1518eec6bc8b2cdc
;; https://github.com/release-on-push-action/test-project-with-release/releases/latest
(def-fixture fixture-project-with-release
  :repo "release-on-push-action/test-project-with-release"
  :sha "946550e635d4bae50cb5cec434678b439b59c659")

(deftest ^:integration generate-new-release-data-from-new-project
  (let [[ctx related-data] @fixture-project-without-release]
    (testing "preconditions"
      (is (= 0 (count (get-in related-data [:related-prs]))))
      (is (= "Commit 10" (get-in related-data [:commit :commit :message])))
      (is (nil? (:latest-release related-data)) "has no latest release"))

    (testing "generate-new-release-data"
      (let [release-data (sut/generate-new-release-data ctx related-data)]
        (are [key expected] (= expected (get release-data key))
          :tag_name "0.1.0"
          :body     "Version 0.1.0

### Commits

- [946550e6] Commit 10
- [de6b1b7a] Commit 9
- [2af2e1d6] Commit 8
- [74ffa7bf] Commit 7
- [536a71ab] Commit 6
")))

    (testing "tag_name"
      (testing ":bump-version-scheme"
        (are [scheme expected] (= expected (-> (assoc ctx :bump-version-scheme scheme)
                                               (sut/generate-new-release-data related-data)
                                               (get :tag_name)))
          "major" "1.0.0"
          "minor" "0.1.0"
          "patch" "0.0.1"))
      (testing "with prefix"
        (are [scheme expected] (= expected (-> (assoc ctx :bump-version-scheme scheme
                                                          :input/tag-prefix "v")
                                               (sut/generate-new-release-data related-data)
                                               (get :tag_name)))
          "major" "v1.0.0"
          "minor" "v0.1.0"
          "patch" "v0.0.1"))
      (testing "with suffix"
        (are [suffix expected] (= expected (-> (assoc ctx :input/tag-suffix suffix)
                                               (sut/generate-new-release-data related-data)
                                               (get :tag_name)))
          "-alpha" "0.1.0-alpha"
          "-rc1"   "0.1.0-rc1"
          "-beta"  "0.1.0-beta"
          ""       "0.1.0"))
      (testing "with prefix and suffix"
        (are [prefix suffix expected] (= expected (-> (assoc ctx
                                                             :input/tag-prefix prefix
                                                             :input/tag-suffix suffix)
                                                      (sut/generate-new-release-data related-data)
                                                      (get :tag_name)))
          "v" "-alpha" "v0.1.0-alpha"
          "v" "-rc1"   "v0.1.0-rc1"
          "v" ""       "v0.1.0"
          ""  "-beta"  "0.1.0-beta")))

    (testing "prerelease field"
      (testing "auto-detect with suffix"
        (is (= true (-> (assoc ctx :input/tag-suffix "-alpha")
                        (sut/generate-new-release-data related-data)
                        (get :prerelease)))))
      (testing "auto-detect without suffix"
        (is (= false (-> ctx
                         (sut/generate-new-release-data related-data)
                         (get :prerelease)))))
      (testing "explicit true overrides"
        (is (= true (-> (assoc ctx :input/use-prerelease "true")
                        (sut/generate-new-release-data related-data)
                        (get :prerelease)))))
      (testing "explicit false overrides"
        (is (= false (-> (assoc ctx
                               :input/tag-suffix "-alpha"
                               :input/use-prerelease "false")
                         (sut/generate-new-release-data related-data)
                         (get :prerelease))))))

    (testing "release_name"
      (are [template expected] (= expected (-> (assoc ctx
                                                      :input/tag-prefix "v"
                                                      :input/release-name template)
                                               (sut/generate-new-release-data related-data)
                                               (get :name)))
        "<RELEASE_TAG>"                           "v0.1.0"
        "<RELEASE_VERSION>"                       "0.1.0"
        "Release <RELEASE_VERSION>"               "Release 0.1.0"
        "Release <RELEASE_TAG> <RELEASE_VERSION>" "Release v0.1.0 0.1.0"))

    (testing "body"
      (testing ":input/release-body"
        (is (= "Version 0.1.0

Hello World

### Commits

- [946550e6] Commit 10
- [de6b1b7a] Commit 9
- [2af2e1d6] Commit 8
- [74ffa7bf] Commit 7
- [536a71ab] Commit 6
"
               (-> (assoc ctx :input/release-body "Hello World")
                   (sut/generate-new-release-data related-data)
                   (get :body)))))

      (testing ":input/max-commits"
        (are [max-commits ends-with] (= ends-with (-> (assoc ctx :input/max-commits max-commits)
                                                      (sut/generate-new-release-data related-data)
                                                      (get :body)
                                                      (str/split-lines)
                                                      (last)))
          1   "- [946550e6] Commit 10"
          5   "- [536a71ab] Commit 6"
          10  "- [8c0eef57] Commit 1"
          11  "- [2db43d3a] Initial commit"
          100 "- [2db43d3a] Initial commit" ;larger number than what's available
          )))))

(deftest ^:integration generate-new-release-data-from-existing-release
  (let [[ctx related-data] @fixture-project-with-release]
    (testing "preconditions"
      (is (= 0 (count (get-in related-data [:related-prs]))))
      (is (= "Commit 10" (get-in related-data [:commit :commit :message])))
      (is (= "v0.1.0" (get-in related-data [:latest-release :tag_name])) "has release v0.1.0"))

    (testing "generate-new-release-data"
      (let [release-data (sut/generate-new-release-data ctx related-data)]
        (are [key expected] (= expected (get release-data key))
          :tag_name "0.2.0"

          ;; note that commit 6 is not included here
          :body "Version 0.2.0

### Commits

- [946550e6] Commit 10
- [de6b1b7a] Commit 9
- [2af2e1d6] Commit 8
- [74ffa7bf] Commit 7
")))

    (testing "tag_name"
      (testing ":bump-version-scheme"
        (are [scheme expected] (= expected (-> (assoc ctx :bump-version-scheme scheme)
                                               (sut/generate-new-release-data related-data)
                                               (get :tag_name)))
          "major" "1.0.0"
          "minor" "0.2.0"
          "patch" "0.1.1"))
      (testing "with prefix"
        (are [scheme expected] (= expected (-> (assoc ctx :bump-version-scheme scheme
                                                      :input/tag-prefix "v")
                                               (sut/generate-new-release-data related-data)
                                               (get :tag_name)))
          "major" "v1.0.0"
          "minor" "v0.2.0"
          "patch" "v0.1.1"))
      (testing "with suffix"
        (are [suffix expected] (= expected (-> (assoc ctx :input/tag-suffix suffix)
                                               (sut/generate-new-release-data related-data)
                                               (get :tag_name)))
          "-alpha" "0.2.0-alpha"
          "-rc1"   "0.2.0-rc1"
          ""       "0.2.0"))
      (testing "with prefix and suffix"
        (are [prefix suffix expected] (= expected (-> (assoc ctx
                                                             :input/tag-prefix prefix
                                                             :input/tag-suffix suffix)
                                                      (sut/generate-new-release-data related-data)
                                                      (get :tag_name)))
          "v" "-alpha" "v0.2.0-alpha"
          "v" "-rc1"   "v0.2.0-rc1"
          ""  "-beta"  "0.2.0-beta")))

    (testing "prerelease field"
      (testing "auto-detect with suffix"
        (is (= true (-> (assoc ctx :input/tag-suffix "-alpha")
                        (sut/generate-new-release-data related-data)
                        (get :prerelease)))))
      (testing "auto-detect without suffix"
        (is (= false (-> ctx
                         (sut/generate-new-release-data related-data)
                         (get :prerelease)))))
      (testing "explicit true overrides"
        (is (= true (-> (assoc ctx :input/use-prerelease "true")
                        (sut/generate-new-release-data related-data)
                        (get :prerelease)))))
      (testing "explicit false overrides"
        (is (= false (-> (assoc ctx
                               :input/tag-suffix "-alpha"
                               :input/use-prerelease "false")
                         (sut/generate-new-release-data related-data)
                         (get :prerelease))))))

    (testing "body"
      (testing ":input/release-body"
        (is (= "Version 0.2.0

Hello World

### Commits

- [946550e6] Commit 10
- [de6b1b7a] Commit 9
- [2af2e1d6] Commit 8
- [74ffa7bf] Commit 7
"
               (-> (assoc ctx :input/release-body "Hello World")
                   (sut/generate-new-release-data related-data)
                   (get :body)))))
      (testing ":input/max-commits"
        (are [max-commits ends-with] (= ends-with (-> (assoc ctx :input/max-commits max-commits)
                                                      (sut/generate-new-release-data related-data)
                                                      (get :body)
                                                      (str/split-lines)
                                                      (last)))
          1   "- [946550e6] Commit 10"
          5   "- [74ffa7bf] Commit 7" ;commits are limited to range *after* 536a71ab
          10  "- [74ffa7bf] Commit 7"
          11  "- [74ffa7bf] Commit 7"
          100 "- [74ffa7bf] Commit 7" ;larger number than what's available
          )))))

(deftest ^:integration generate-new-release-data-with-github-generated-release-notes
  (let [[ctx related-data] @fixture-project-with-release
        ctx                (assoc ctx :input/use-github-release-notes true)
        release-data       (sut/generate-new-release-data ctx related-data)]

    (testing "sets options to enable Github Generated Release Notes"
      (is (= true (:generate_release_notes release-data)))
      (is (= "Version 0.2.0\n\n" (:body release-data))))))
