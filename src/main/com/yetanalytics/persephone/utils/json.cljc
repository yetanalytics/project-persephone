(ns com.yetanalytics.persephone.utils.json
  (:require [com.yetanalytics.pathetic :as pathetic]
            [com.yetanalytics.pan.utils.json :refer [convert-json]]
            #?(:clj [clojure.data.json :as json])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON-EDN conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn json->edn
  "Convert a JSON data structure to EDN. By default, keys will
   remain strings. If `keywordize?` is true then all keys are
   turned into keywords, with all instances `@` replaced with
   `_` (this is not recommended for IRI keys)."
  [json-str & {:keys [keywordize?] :or {keywordize? false}}]
  (if keywordize?
    (convert-json json-str "_")
    #?(:clj (json/read-str json-str)
       :cljs (js->clj (.parse js/JSON json-str)))))

(defn edn->json
  "Convert an EDN data structure to a JSON string."
  [edn-data]
  #?(:clj (json/write-str edn-data)
     :cljs (->> edn-data clj->js (.stringify js/JSON))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common JSON-to-EDN coercion functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn coerce-statement
  "If `statement` is a JSON string, coerce it to EDN with string keys."
  [statement]
  (if (string? statement)
    (json->edn statement)
    statement))

(defn coerce-template
  "If `template` is a JSON string, coerce it to EDN with keyword keys."
  [template]
  (if (string? template)
    (json->edn template :keywordize? true)
    template))

(defn coerce-profile
  "If `profile` is a JSON string, coerce it to EDN with keyword keys."
  [profile]
  (if (string? profile)
    (json->edn profile :keywordize? true)
    profile))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath operations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Parsing is an expensive operation, and many JSONPath strings are repeated in
;; a given Profile, so we call `memoize` to cache already parsed paths.

(def parse-jsonpath
  "Parse a single arg `path-str` and return a vector of parsed
   JSONPaths."
  (memoize pathetic/parse-paths))

;; Wrapper for pathetic/get-values*
;; We don't use `memoize` here because `json` Statements are usually different.

(def opts-map {:return-missing? true})

(defn get-jsonpath-values
  "Given `json` and parsed JSONPaths `paths`, return a vector of JSON
   valuess."
  [json paths]
  (pathetic/get-values* json paths opts-map))
