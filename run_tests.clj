#!/usr/bin/env bb
(require '[clojure.test :as t])

(def test-namespaces
  '[
    release-on-push-action.core-test
    release-on-push-action.github-test
    ])

(doseq [namespace test-namespaces]
  (require namespace))


(let [{:keys [:fail :error]} (apply t/run-tests test-namespaces)]
  (System/exit (+ fail error)))
