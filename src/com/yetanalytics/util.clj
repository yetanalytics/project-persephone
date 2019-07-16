(ns com.yetanalytics.util
  (:require [clojure.string :as string]
            [cheshire.core :as cheshire]
            [json-path :as jpath])
  (:import [com.jayway.jsonpath
            DocumentContext
            Configuration
            JsonPath
            Criteria
            Filter
            Predicate
            PathNotFoundException]
           [java.util Date]))

(defn cond-on-val
  "Ensure that predicate is only considered when the value is not nil.
  Otherwise ignore the predicate by always returning true."
  [pred-fn v]
  (if (some? v)
    pred-fn
    (constantly true)))

(defn partial-on-val
  "Compose Clojure's partial function with util/cond-on-val.
  The predicate is only considered when its first argument isn't nil."
  [pred-fn v]
  (partial (cond-on-val v pred-fn) v))

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

;; We require the lookahead group "(?!([^\[]*\]))" to avoid separating along
;; pipes within brackets. The "\]" detects a closing bracket, but the "[^\[]"
;; ensures we avoid brackets that aren't the ones enclosing the pipe.
(defn split-json-path
  "Split JSONPath strings that are separated by pipe (ie. '|') characters.
  Returns a vector of JSONPaths."
  [json-paths]
  (let [split-regex #"\s*\|\s*(?!([^\[]*\]))"]
    (string/split json-paths split-regex)))

(defn prepare-path
  "Prepare path to allow bracket notation to be used by json-path"
  [json-path]
  (string/replace json-path #"\[\s*'([^\]]*)'\s*\]" ".$1"))

;; TODO read-json clj lib is a piece of garbage and only a temp solution;
;; eventually we will be moving to a more robust solution like Jayway.
(defn read-json
  "Read an EDN data structure using a JSONPath string.
  Queries that cannot be matched will evaluate to a nil value (or values)."
  [json json-path]
  (let [result-map (try (jpath/query (prepare-path json-path) json)
                        (catch Exception e nil))]
    (if (seq? result-map)
      (mapv :value result-map)
      [(:value result-map)])))

;(defn read-json
;  "Evaluate a JSONPath query given a JSONPath string and JSON.
;Always returns a vector of values."
;  [json json-path]
;  (doto (JsonPath/parse json)
;    (.read json-path))
;  #_(if (string? json)
;      (let [compiled (JsonPath/compile json-path (into-array Predicate []))]
;        (first (if-let [ret
;                        (try (.read compiled json)
;                             (catch PathNotFoundException pnfe
;                               nil))]
;        ;; normalize to vector
;                 (if (coll? ret) ret [ret])
;                 []))
;      ; (if (instance? java.util.Collection values)
;      ;   (vec (seq values))
;      ;   [values])
;      ;values
;        (read-json (edn-to-json json) json-path))))
