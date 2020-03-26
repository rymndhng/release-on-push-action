#!/usr/bin/env bb
(require '[clojure.test :as t]
         '[release-on-push-action.core-test])

(let [{:keys [:fail :error]} (t/run-tests 'release-on-push-action.core-test)]
  (System/exit (+ fail error)))
