(ns com.yetanalytics.persephone.utils.json
  (:require [clojure.string :as string]
            #?(:clj [clojure.data.json :as json])
            #?(:cljs [jsonpath])
            #_[cheshire.core :as cheshire])
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

;; Need to manually customize the JSON Provider to Gson:
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
   (defn- gson->edn [obj]
     (cond
       (.isJsonArray obj)
       (->> obj seq (mapv gson->edn))
       (.isJsonObject obj)
       (reduce (fn [acc e] (assoc acc (.getKey e) (-> e .getValue gson->edn)))
               {}
               (.entrySet obj))
       (and (.isJsonPrimitive obj) (.isBoolean obj))
       (.getAsBoolean obj)
       (and (.isJsonPrimitive obj) (.isString obj))
       (.getAsString obj)
       (and (.isJsonPrimitive obj) (.isNumber obj))
       (if (->> obj .getAsString (re-matches #".*\..*"))
         (.getAsDouble obj)
         (.getAsLong obj))
       (.isJsonNull obj)
       nil)))

#?(:clj
   (defn- read-json-java [json json-path]
     (let [config (if-not (deref json-config)
                    (swap! json-config new-json-config)
                    (deref json-config))]
       (-> (JsonPath/using config)
           (.parse (edn-to-json json))
           (.read json-path (into-array Predicate []))
           gson->edn))))

(defn read-json
  [json json-path]
  #?(:clj (let [res (read-json-java json json-path)]
            res)
     :cljs (js->clj
            (.query jsonpath (clj->js json) json-path))))