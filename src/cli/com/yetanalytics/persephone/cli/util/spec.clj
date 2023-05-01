(ns com.yetanalytics.persephone.cli.util.spec
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.pan.axioms :as ax]
            [com.yetanalytics.pan.errors :as perr]
            [com.yetanalytics.persephone.utils.asserts :as assert]
            [com.yetanalytics.persephone.cli.util.args :refer [printerr]]))

(defn iri? [x] (s/valid? ::ax/iri x))

(def iri-err-msg "Must be a valid IRI.")

;; Super-basic profile check; most work is done during compilation
(defn profile? [p] (map? p))

(defn profile-err-msg [_] "Must be a valid JSON object.")

(defn statement? [s] (s/valid? ::xs/statement s))

(defn statement-err-msg [s] (s/explain-str ::xs/statement s))

(defn statements? [s]
  (s/or :single   (s/valid? ::xs/statement s)
        :multiple (s/valid? ::xs/statements s)))

(defn statements-err-msg [s]
  (if (vector? s)
    (s/explain-str ::xs/statements s)
    (s/explain-str ::xs/statement s)))

(defn handle-asserts
  "Handle all possible assertions given an ExceptionInfo `ex` thrown from
   the `persephone.utils.asserts` namespace."
  [ex]
  (case (or (-> ex ex-data :kind)
            (-> ex ex-data :type))
    ::assert/invalid-profile
    (printerr "Profile errors are present."
              (-> ex ex-data :errors perr/errors->string))
    ::assert/invalid-template
    (printerr "Template errors are present."
              (-> ex ex-data :errors s/explain-printer with-out-str))
    ::assert/no-templates
    (printerr "Compilation error: no Statement Templates to validate against")
    ::assert/no-patterns
    (printerr "Compilation error: no Patterns to match against, or one or more Profiles lacks Patterns")
    ::assert/non-unique-profile-ids
    (printerr "ID error: Profile IDs are not unique")
    ::assert/non-unique-template-ids
    (printerr "ID error: Template IDs are not unique")
    ::assert/non-unique-pattern-ids
    (printerr "ID error: Pattern IDs are not unique")
    ;; else
    (throw ex)))
