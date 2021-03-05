(ns com.yetanalytics.persephone.utils.json
  (:require [com.yetanalytics.pathetic :refer [parse-paths get-values*]]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath operations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Parsing is an expensive operation, and many JSONPath strings are repeated in
;; a given Profile, so we cache already-parsed ones in a map from unparsed to
;; parsed paths.

(def path-cache (atom {}))

(defn parse-jsonpath
  "Parse `path-str` and return a vector of parsed JSONPaths."
  [path-str]
  (if-let [parsed-path (get (deref path-cache) path-str)]
    parsed-path
    (let [parsed-path (parse-paths path-str)]
      (swap! path-cache (fn [m] (assoc m path-str parsed-path)))
      parsed-path)))

;; Wrapper for pathetic/get-values*

(def opts-map {:return-missing? true})

(defn get-jsonpath-values
  "Given `json` and parsed JSONPaths `paths`, return a vector of JSON
   valuess."
  [json paths]
  (get-values* json paths opts-map))
