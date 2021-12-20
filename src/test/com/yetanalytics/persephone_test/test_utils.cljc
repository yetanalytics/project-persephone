(ns com.yetanalytics.persephone-test.test-utils
  (:require [com.yetanalytics.persephone]
            [com.yetanalytics.pan.utils.json :refer [convert-json]]
            [clojure.test.check.properties :include-macros true]
            #?@(:clj [[clojure.data.json :as json]
                      [clojure.spec.test.alpha :as stest]
                      [orchestra.spec.test     :as otest]])
            #?@(:cljs [;; To make instrumentation work in cljs
                       [clojure.test.check]
                       [clojure.test.check.generators]
                       [orchestra-cljs.spec.test :as otest :include-macros true]]))
  #?(:cljs (:require-macros [com.yetanalytics.persephone-test.test-utils
                             :refer [persephone-syms-macro]])))

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

#?(:clj
   (defn- persephone-sym-filter
     [sym]
     (->> sym
          namespace
          (re-matches #"com\.yetanalytics\.persephone.*"))))

#?(:clj
   (defn- persephone-syms
     []
     (->> (stest/instrumentable-syms)
          (filter persephone-sym-filter)
          set)))

;; cljs mode needs a macro since `instrument` in cljs is itself a macro
;; (unlike clj mode where `instrument` is a function)

#?(:clj
   (defmacro persephone-syms-macro
     []
     `(->>(stest/instrumentable-syms)
           (filter #(->> % namespace (re-matches #"com\.yetanalytics\.persephone.*")))
           set)))

(comment
  ;; TODO: Add the instrumentation as a fixture
  ;; (including in ClojureScript, somehow)
  (otest/instrument #?(:clj (persephone-syms) :cljs (persephone-syms-macro)))
  (otest/unstrument #?(:clj (persephone-syms) :cljs (persephone-syms-macro))))
