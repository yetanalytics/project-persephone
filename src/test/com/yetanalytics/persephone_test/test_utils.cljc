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
                             :refer [persephone-syms-var]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Printerr to string
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defmacro with-err-str
     "Identical to `with-out-str` except captures output from stderr
      instead of not stdout."
     [& body]
     `(let [s# (new java.io.StringWriter)]
        (binding [~'*err* s#]
          ~@body
          (str s#)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resource reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; https://stackoverflow.com/questions/38880796/how-to-load-a-local-file-for-a-clojurescript-test

#?(:cljs
   (defn slurp
     "ClojureScript drop-in replacement for `clojure.core/slurp`. Only works
      for reading local files."
     [path]
     (let [fs (js/require "fs")]
       (.readFileSync fs path "utf8"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON Parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: remove and replace with `coerce-*` fns after instrumentation merge
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

;; cljs mode needs a compile-time var since `instrument` in cljs a macro
;; (unlike clj mode where `instrument` is a runtime function).

#?(:clj
   (def persephone-syms-var
     (persephone-syms)))

(def instrumentation-fixture
  "Fixture to instrument, run tests, then unstrument."
  #?(:clj
     (fn [f]
       (otest/instrument (persephone-syms))
       (f)
       (otest/unstrument (persephone-syms)))
     :cljs
     {:before #(otest/instrument persephone-syms-var)
      :after #(otest/unstrument persephone-syms-var)}))
