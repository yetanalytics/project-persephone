(ns com.yetanalytics.util
  (:require [cheshire.core :as cheshire])
  (:import [com.jayway.jsonpath
            Configuration
            JsonPath]))

(defn cond-on-key
  "Ensure that predicate is only considered when the key actually exists.
  Otherwise ignore the predicate by always returning true."
  [keyed-map k pred-fn]
  (if (contains? keyed-map k)
    pred-fn
    (constantly true)))

(defn cond-on-val
  "Ensure that predicate is only considered when the value is not nil.
  Otherwise ignore the predicate by always returning true.
  Same overall functionality as cond-on-key."
  [v pred-fn]
  (if (some? v)
    pred-fn
    (constantly true)))

(defn edn-to-json
  "Convert an EDN data structure to JSON."
  [edn]
  (cheshire/generate-stream edn))

(defn read-json
  "Evaluate a JSONPath query given a JSONPath string and JSON.
  Always returns a vector of values."
  [json json-path]
  (if (string? json)
    (let [values (JsonPath/read json json-path)]
      (if (coll? values) values [values]))
    (read-json (edn-to-json json) json-path)))

(defn get-value-map
  "Given a key, return corresponding values from a vector of maps."
  [map-vec k]
  (mapv #(get % k) map-vec))
