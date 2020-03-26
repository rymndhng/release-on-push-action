(ns release-on-push-action.core-test
  (:require [clojure.test :refer [deftest is are]]
            [release-on-push-action.core :as sut]))

(deftest get-tagged-version
  (are [expected tag-name] (= expected (sut/get-tagged-version {:tag-name tag-name}))
    "0.0.0" "v0.0.0"
    "0.0.0" "0.0.0"))
