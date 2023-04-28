(ns com.yetanalytics.persephone.utils.cli
  "Clojure-only namespace for CLI-specific utilities, used by the
   `:cli` and `:server` aliases instead of the general API."
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.pan.axioms :as ax]
            [com.yetanalytics.pan.errors :as perr]
            [com.yetanalytics.persephone.utils.asserts :as assert]
            [com.yetanalytics.persephone.utils.json    :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; File reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-profile
  [profile-filename]
  (json/coerce-profile (slurp profile-filename)))

(defn read-statement
  [statement-filename]
  (json/coerce-statement (slurp statement-filename)))

(defn conj-argv
  "Function to conj a non-array-valued arg `v` to pre-existing `values`."
  [values v]
  (let [values (or values [])]
    (conj values v)))

(defn conj-argv-or-array
  "Function to conj an array-valued or non-array-valued arg `v` to
   pre-existing `values`. An array-valued `v` is presumed to be a vector
   (as opposed to a list or lazy seq)."
  [values v]
  (let [values (or values [])]
    (if (vector? v) (into values v) (conj values v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn iri? [x] (s/valid? ::ax/iri x))

(defn iri-err-msg [_] "Must be a valid IRI.")

;; Super-basic profile check; most work is done during compilation
(defn profile? [p] (map? p))

(defn profile-err-msg [_] "Must be a valid JSON object.")

(defn statement? [s] (s/valid? ::xs/statement s))

(defn statement-err-data [s] (s/explain-data ::xs/statement s))

(defn statement-err-msg [s] (s/explain-str ::xs/statement s))

(defn statements? [s]
  (s/or :single   (s/valid? ::xs/statement s)
        :multiple (s/valid? ::xs/statements s)))

(defn statements-err-data [s] (s/explain-data ::xs/statements s))

(defn statements-err-msg [s]
  (if (vector? s)
    (s/explain-str ::xs/statements s)
    (s/explain-str ::xs/statement s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Error printing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn printerr
  "Print the `error-messages` vector line-by-line to stderr."
  [& error-messages]
  (binding [*out* *err*]
    (run! println error-messages))
  (flush))

(defn print-assert-errors
  "Handle all possible assertions given an ExceptionInfo `ex` thrown from
   the `persephone.utils.asserts` namespace. Print assertion exceptions to
   stderr using `printerr`, or re-throw if not recognized."
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

(defn handle-parsed-args
  "Given the return value of `cli/parse-opts`, return either `:error`,
   `:help` or the parsed `options` map. In the `:error` case, print the
   CLI errors to stderr, and in the `:help` case, print the `--help`
   command result to stdout."
  [{:keys [options summary errors]}]
  (let [{:keys [help]} options]
    (cond
      ;; Display help menu and exit
      help
      (do (println summary)
          :help)
      ;; Display error message and exit
      (not-empty errors)
      (do (apply printerr errors)
          :error)
      ;; Do the things
      :else
      options)))
