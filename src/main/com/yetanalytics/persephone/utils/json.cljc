(ns com.yetanalytics.persephone.utils.json
  (:require [clojure.string :as string]
            [cheshire.core :as cheshire]
            [json-path :as jpath]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON-EDN conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn json-to-edn
  "Convert a JSON data structure to EDN."
  [js]
  (cheshire/parse-string js true))

(defn edn-to-json
  "Convert an EDN data structure to JSON."
  [edn]
  (cheshire/generate-string edn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                        ;; TODO: I think this try/catch is whats preventing
                        ;;       spec from saying more in its error messages.
                        ;;       - all other information is lost once the catch returns nil
                        (catch Exception e nil))]
    (if (seq? result-map)
      (mapv :value result-map)
      [(:value result-map)])))

#_(defn read-json
    "Evaluate a JSONPath query given a JSONPath string and JSON.
Always returns a vector of values."
    [json json-path]
    (doto (JsonPath/parse json)
      (.read json-path))
    #_(if (string? json)
        (let [compiled (JsonPath/compile json-path (into-array Predicate []))]
          (first (if-let [ret
                          (try (.read compiled json)
                               (catch PathNotFoundException pnfe
                                 nil))]
        ;; normalize to vector
                   (if (coll? ret) ret [ret])
                   []))
      ; (if (instance? java.util.Collection values)
      ;   (vec (seq values))
      ;   [values])
      ;values
          (read-json (edn-to-json json) json-path))))
