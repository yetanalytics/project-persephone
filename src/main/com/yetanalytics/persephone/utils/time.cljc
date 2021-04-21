(ns com.yetanalytics.persephone.utils.time
  "Functions for date and time."
  (:require #?(:clj [clojure.instant :as instant]
               :cljs [cljs.reader :as cljs-reader])))

(defn parse-timestamp
  "Parse the ISO 8601 timestamp `s`."
  [s]
  #?(:clj (instant/read-instant-date s)
     :cljs (cljs-reader/parse-timestamp s)))

(defn compare-timestamps*
  "Same as `compare-timestamps` but assumes `t1` and `t2` are
   already parsed."
  [t1 t2]
  #?(:clj
     (.compareTo t1 t2)
     :cljs
     (cond
       (= t1 t2) 0
       (< t1 t2) -1
       :else 1)))

(defn compare-timestamps
  "Returns a negative number if `t1` occurs before `t2`, a positive
   number if `t1` occurs after `t2`, and 0 otherwise. `t1` and `t2`
   must be ISO 8601 timestamp strings."
  [t1 t2]
  (compare-timestamps* (parse-timestamp t1) (parse-timestamp t2)))
