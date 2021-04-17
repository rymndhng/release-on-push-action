(ns release-on-push-action.core-test
  (:require [clojure.test :refer [deftest is are testing]]
            [release-on-push-action.core :as sut]))

(deftest get-tagged-version
  (testing "with default prefix"
    (are [expected tag-name] (= expected (sut/get-tagged-version "v" {:tag_name tag-name}))
      "0.0.0" "v0.0.0"
      "0.0.0" "0.0.0"
      "1.0.0" "v1.0.0"
      "1.0.0" "1.0.0"))

  (testing "with no prefix"
    (are [expected tag-name] (= expected (sut/get-tagged-version "" {:tag_name tag-name}))
      "v0.0.0" "v0.0.0"
      "0.0.0" "0.0.0"
      "v1.0.0" "v1.0.0"
      "1.0.0" "1.0.0")))

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
