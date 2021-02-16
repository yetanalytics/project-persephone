(ns com.yetanalytics.persephone.utils.json
  (:require [com.yetanalytics.pan.utils.json :refer [convert-json]]
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
