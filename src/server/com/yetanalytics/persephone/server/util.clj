(ns com.yetanalytics.persephone.server.util
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli  :as cli]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.pan.axioms :as ax]
            [com.yetanalytics.pan.errors :as perr]
            [com.yetanalytics.persephone.utils.asserts :as assert]
            [com.yetanalytics.persephone.utils.json :as json]))

(defn read-profile [profile-filename]
  (json/coerce-profile (slurp profile-filename)))

(defn iri? [x] (s/valid? ::ax/iri x))

(def iri-err-msg "Must be a valid IRI.")

;; Basic validation; most validation will be done at the API level
(defn profile? [p] (map? p))

(defn profile-err-msg [_] "Must be a valid JSON object.")

(defn statement-err-data [s] (s/explain-data ::xs/statement s))

(defn statements-err-data [s] (s/explain-data ::xs/statements s))

(defn printerr
  "Print the `error-messages` vector line-by-line to stderr."
  [& error-messages]
  (binding [*out* *err*]
    (run! println error-messages))
  (flush))

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

(defn handle-args
  "Parse `args` based on `cli-options` (which should follow the tools.cli
   specification) and either return `:error`, print `--help` command and
   return `:help`, or return the parsed `options` map."
  [args cli-options]
  (let [{:keys [options summary errors]}
        (cli/parse-opts args cli-options)
        {:keys [help]}
        options]
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
