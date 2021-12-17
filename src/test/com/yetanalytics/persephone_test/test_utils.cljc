(ns com.yetanalytics.persephone-test.test-utils
  (:require [clojure.spec.test.alpha :as stest]
            [orchestra.spec.test     :as otest]
            [com.yetanalytics.pan.utils.json :refer [convert-json]]
            #?(:clj [clojure.data.json :as json])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Instrumentation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- persephone-sym-filter
  [sym]
  (->> sym
       namespace
       (re-matches #"com\.yetanalytics\.persephone.*")))

(defn- persephone-syms
  []
  (->> (stest/instrumentable-syms)
       (filter persephone-sym-filter)
       set))

(defn instrument-persephone
  "Instrument all instrumentable functions defined in persephone."
  []
  (otest/instrument (persephone-syms)))

(defn unstrument-persephone
  "Instrument all instrumentable functions defined in persephone."
  []
  (otest/unstrument (persephone-syms)))

(defn instrumentation-fixture
  [f]
  (instrument-persephone)
  (f)
  (unstrument-persephone))

(comment
  ;; Keep instrumentation off by default since
  ;; 1. some tests will fail (e.g. because they test invalid inputs on purpose)
  ;; 2. it is insanely slow
  (instrument-persephone)
  (unstrument-persephone))
