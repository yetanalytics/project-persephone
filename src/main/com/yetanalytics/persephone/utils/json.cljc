(ns com.yetanalytics.persephone.utils.json
  (:require [clojure.string :as string]
            #?(:clj [clojure.data.json :as json])
            #?(:cljs [jsonpath]))
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
  "Convert a JSON data structure to EDN. By default, keys remain strings, but
   passing \"true\" for \"kwd\" will result in keyword strings (which is not
   recommended for IRI keys)."
  [json-str & {:keys [kwd] :or {kwd false}}]
  #?(:clj (if kwd
            (json/read-str json-str)
            (json/read-str json-str :key-fn keyword))
     :cljs (js->clj (.parse js/JSON json-str) :keywordize-keys kwd)))

(defn edn-to-json
  "Convert an EDN data structure to JSON."
  [edn-data]
  #?(:clj (json/write-str edn-data)
     :cljs (->> edn-data clj->js (.stringify js/JSON))))

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
    (->> (string/split json-paths split-regex)
         #?(:clj identity
            :cljs (filterv some?)))))

;; Clojure

#?(:clj (def json-opts-list [Option/ALWAYS_RETURN_LIST
                             Option/SUPPRESS_EXCEPTIONS]))

#?(:clj (def json-config (atom nil)))

;; Need to manually customize the JSON Provider to Gson:
;; https://stackoverflow.com/questions/63644711/expected-to-find-an-object-with-property-xyz-in-path-but-found-org-json-j

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

;; ClojureScript

#?(:cljs
   (defn- read-json-js [json json-path]
     (letfn [(not-found-error?
               [err]
               (re-find #"Lexical error on line \d*\. Unrecognized text\."
                        (ex-message err)))]
       (try (js->clj (.query jsonpath (clj->js json) json-path))
            (catch js/Error.
                   e
              (if (not-found-error? e) [] (throw e)))))))

;; Putting it together

(defn read-json
  [json json-path]
  #?(:clj (read-json-java json json-path)
     :cljs (read-json-js json json-path)))