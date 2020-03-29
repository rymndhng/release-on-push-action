(ns release-on-push-action.github-test
  (:require [clojure.test :refer [deftest is are]]
            [release-on-push-action.github :as sut]))

(deftest link-header->map-test
  (is (= {:next "https://api.github.com/repositories/217744078/commits?page=2"
          :last "https://api.github.com/repositories/217744078/commits?page=2"}
         (sut/link-header->map "<https://api.github.com/repositories/217744078/commits?page=2>; rel=\"next\", <https://api.github.com/repositories/217744078/commits?page=2>; rel=\"last\"")
       )))

(deftest list-commits-to-base)
