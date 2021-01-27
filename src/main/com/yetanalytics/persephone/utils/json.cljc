(ns com.yetanalytics.persephone.utils.json
  (:require [clojure.string :as string]
            #?(:clj [clojure.data.json :as json])
            #?(:cljs [jsonpath])
            #_[cheshire.core :as cheshire]
            #_[json-path :as jpath])
  #?(:clj (:import [com.jayway.jsonpath
                    Configuration
                    JsonPath
                    Option
                    Predicate]
                   [com.jayway.jsonpath.spi.json
                    GsonJsonProvider])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON-EDN conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn json-to-edn
  [json-str]
  #?(:clj (json/read-str json-str)
     :cljs (->> json-str (.parse js/JSON) js->clj)))

(defn edn-to-json
  [edn-data]
  #?(:clj (json/write-str edn-data)
     :cljs (->> edn-data clj->js (.stringify js/JSON))))

#_(defn json-to-edn
    "Convert a JSON data structure to EDN."
    [js]
    (cheshire/parse-string js true))

#_(defn edn-to-json
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

;; Need to manually customize the JSON Provider:
;; https://stackoverflow.com/questions/63644711/expected-to-find-an-object-with-property-xyz-in-path-but-found-org-json-j

#?(:clj (def json-opts-list [Option/ALWAYS_RETURN_LIST
                             Option/SUPPRESS_EXCEPTIONS]))

#?(:clj (def json-config (atom nil)))

#?(:clj
   (defn- new-json-config [_]
     (-> (Configuration/builder)
         (.jsonProvider (GsonJsonProvider.))
         (.options (into-array Option json-opts-list))
         (.build))))

#?(:clj
   (defn- read-json-java [json json-path]
     (let [config (if-not (deref json-config)
                    (swap! json-config new-json-config)
                    (deref json-config))]
       (-> (JsonPath/using config)
           (.parse (edn-to-json json))
           (.read json-path (into-array Predicate []))))))

#?(:clj
   (defn- gson-to-edn [obj]
     (cond
       (.isJsonArray obj)
       (->> obj seq (mapv gson-to-edn))
       (.isJsonObject obj)
       (reduce (fn [acc e] (assoc acc (.getKey e) (-> e .getValue gson-to-edn)))
               {}
               (.entrySet obj))
       (and (.isJsonPrimitive obj) (.isBoolean obj))
       (.getAsBoolean obj)
       (and (.isJsonPrimitive obj) (.isNumber obj))
       (.getAsLong obj)
       (and (.isJsonPrimitive obj) (.isString obj))
       (.getAsString obj)
       (.isJsonNull obj)
       nil)))

(defn read-json
  [json json-path]
  #?(:clj (let [res (read-json-java json json-path)]
            res)
     :cljs (js->clj
            (.query jsonpath (clj->js json) json-path))))

(gson-to-edn (read-json [{"foo" [1 2 3]} {"baz" "qux"}] "$.[0,1].baz"))

;; TODO read-json clj lib is a piece of garbage and only a temp solution;
;; eventually we will be moving to a more robust solution like Jayway.
#_(defn read-json
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

#_(defn read-json-2
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
