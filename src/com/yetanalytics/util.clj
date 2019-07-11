(ns com.yetanalytics.util
  (:require [clojure.string :as string]
            [cheshire.core :as cheshire])
  (:import [com.jayway.jsonpath
            Configuration
            JsonPath
            Predicate]
           [java.util Date]))

(defn cond-on-val
  "Ensure that predicate is only considered when the value is not nil.
  Otherwise ignore the predicate by always returning true."
  [v pred-fn]
  (if (some? v)
    pred-fn
    (constantly true)))

(defn value-map
  "Given an array of keys (each corresponding to a level of map nesting),
  return corresponding values from a vector of maps."
  [map-vec & ks]
  (mapv #(get-in % ks) map-vec))

(defn predicate
  "Create a predicate from a 2-arg function and its first argument; the
  predicate is ignored if said argument is nil."
  [func value]
  (cond-on-val value (partial func value)))

(defn json-to-edn
  "Convert a JSON data structure to EDN."
  [js]
  (cheshire/parse-string js true))

(defn edn-to-json
  "Convert an EDN data structure to JSON."
  [edn]
  (cheshire/generate-string edn))

;; TODO: Eventually we would like to create an in-house set of functions (or
;; even a lib) that will evaulate EDN data structures with JSONPath directly
;; (instead of having to convert back to JSON).

;; TODO: Fix project-pan's axiom functions so that it matches split-regex here.
;; Right now it doesn't because we need to account for pipes in odd locations,
;; namely in brackets (eg. ['b|ah']).
;; Right now project-pan utilizes lookahead and escape chars, which is awk.
(defn split-json-path
  "Split JSONPath strings that are separated by pipe (ie. '|') characters."
  [json-paths]
  (let [split-regex #"\s*\|\s*"]
    (string/split json-paths split-regex)))

(defn read-json
  "Evaluate a JSONPath query given a JSONPath string and JSON.
  Always returns a vector of values."
  [json json-path]
  (if (string? json)
    (let [preds (into-array Predicate [])
          values (JsonPath/read json json-path preds)]
      (if (coll? values) values [values]))
    (read-json (edn-to-json json) json-path)))
