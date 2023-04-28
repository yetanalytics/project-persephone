(ns com.yetanalytics.persephone.utils.json
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.pathetic :as pathetic]
            [com.yetanalytics.pan.objects.profile :as pan-profile]
            [com.yetanalytics.pan.utils.json :refer [convert-json]]
            #?(:clj [clojure.data.json :as json])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON to EDN
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- json->edn
  "Convert a JSON data structure to EDN. By default, keys will
   remain strings. If `keywordize?` is true then all keys are
   turned into keywords, with all instances `@` replaced with
   `_` (this is not recommended for IRI keys)."
  [json-str keywordize?]
  (if keywordize?
    (convert-json json-str "_")
    #?(:clj (json/read-str json-str)
       :cljs (js->clj (.parse js/JSON json-str)))))

(s/fdef coerce-profile
  :args string?
  :ret ::pan-profile/profile)

(defn coerce-profile
  "Coerce a Profile JSON string into EDN, keywordizing keys and 
   converting \"@\" prefixes into `:_`."
  [profile-json]
  (json->edn profile-json true))

(s/fdef coerce-statement
  :args string?
  :ret ::xs/statement)

(defn coerce-statement
  "Coerce a Statement or Statement array JSON string into EDN, 
   stringifying keys. Converts array JSON into "
  [statement-json]
  (json->edn statement-json false))

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
