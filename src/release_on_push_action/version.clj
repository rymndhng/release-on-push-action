(ns release-on-push-action.version
  (:require [clojure.string :as str]))

(defn safe-inc [n]
  (inc (or n 0)))

(defn semver-bump [version {:keys [bump]}]
  (let [[major minor patch] (map #(Integer/parseInt %) (str/split version #"\."))
        next-version (condp = bump
                       :major [(safe-inc major) 0 0]
                       :minor [major (safe-inc minor) 0]
                       :patch [major minor (safe-inc patch)])]
    (str/join "." next-version)))

;; see zoneid: https://docs.oracle.com/javase/8/docs/api/java/time/ZoneId.html
(defn pad-number [number]
  (format "%02d" number))

(defn calver-bump [version {:keys [template timezone calendar]}]
  (let [now   (java.time.ZonedDateTime/now (java.time.ZoneId/of (or timezone "UTC")))
        year  (.getYear now)
        month (.getMonthValue now)
        week  (.get now java.time.temporal.IsoFields/WEEK_OF_WEEK_BASED_YEAR)
        day   (.getDayOfMonth now)]
    (-> template
        (str/replace "YYYY" (str year))
        (str/replace "YY"   (str (mod year 100)))
        (str/replace "0Y"   (pad-number (mod year 100)))
        (str/replace "MM"   (str month))
        (str/replace "0M"   (pad-number month))
        (str/replace "WW"   (str week))
        (str/replace "0W"   (pad-number week))
        (str/replace "DD"   (str day))
        (str/replace "0D"   (pad-number day)))))

;; compare to current version & decide if
;;
