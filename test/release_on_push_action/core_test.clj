(ns release-on-push-action.core-test
  (:require [clojure.test :refer [deftest is are testing]]
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

(deftest set-output-escape
  (are [expected input] (= expected (sut/set-output-escape input))
    "hello world"     "hello world"
    "hello%2520world" "hello%20world"
    "hello%0Aworld"   "hello\nworld"
    "hello%0Dworld"   "hello\rworld"))

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
      "2.0.0" "1.1.0")))
