(ns com.yetanalytics.util
  (:require [cheshire.core :as cheshire])
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
  "Given a key, return corresponding values from a vector of maps."
  [map-vec k]
  (mapv #(get % k) map-vec))

(defn value-map-double
  "Given two keys, return a vector of corresponding values from a vector of
  maps that themselves have a nested map."
  [map-vec k1 k2]
  (mapv #(-> % k1 k2) map-vec))

(defn json-to-edn
  "Convert a JSON data structure to EDN."
  [js]
  (cheshire/parse-string js true))

(defn edn-to-json
  "Convert an EDN data structure to JSON."
  [edn]
  (cheshire/generate-string edn))

(defn read-json
  "Evaluate a JSONPath query given a JSONPath string and JSON.
  Always returns a vector of values."
  [json json-path]
  (if (string? json)
    (let [preds (into-array Predicate [])
          values (JsonPath/read json json-path preds)]
      (if (coll? values) values [values]))
    (read-json (edn-to-json json) json-path)))
