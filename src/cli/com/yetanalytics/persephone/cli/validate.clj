(ns com.yetanalytics.persephone.cli.validate
  (:require [clojure.tools.cli :as cli]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.utils.cli :as u]
            [com.yetanalytics.persephone.template.errors        :as errs]
            [com.yetanalytics.persephone.template.statement-ref :as sref])
  (:import [clojure.lang ExceptionInfo]))

(def validate-statement-options
  [["-p" "--profile URI"
    "Profile URI filepath/location; must specify one or more."
    :id        :profiles
    :missing   "No Profiles specified."
    :multi     true
    :parse-fn  u/read-profile
    :validate  [u/profile? u/profile-err-msg]
    :update-fn u/conj-argv]
   ["-i" "--template-id IRI"
    "IDs of Statement Templates to validate against; can specify zero or more. Filters out all Templates that are not included."
    :id        :template-ids
    :multi     true
    :validate  [u/iri? u/iri-err-msg]
    :update-fn u/conj-argv]
   ["-s" "--statement URI"
    "Statement filepath/location; must specify one."
    :id       :statement
    :missing  "No Statement specified."
    :parse-fn u/read-statement
    :validate [u/statement? u/statement-err-msg]]
   ["-e" "--extra-statements URI"
    (str "Extra Statement batch filepath/location; can specify zero or more "
         "and accepts arrays of Statements. "
         "If specified, activates Statement Ref property validation, "
         "where the referred object/context Statement exists in this batch "
         "and its Template exists in a provided Profile.")
    :id        :extra-statements
    :multi     true
    :parse-fn  u/read-statement
    :validate  [u/statements? u/statements-err-msg]
    :update-fn u/conj-argv-or-array]
   ["-a" "--all-valid"
    (str "If set, the Statement is not considered valid unless it is valid "
         "against ALL Templates. "
         "Otherwise, it only needs to be valid against at least one Template.")
    :id :all-valid]
   ["-c" "--short-circuit"
    (str "If set, then print on only the first Template the Statement fails "
         "validation against."
         "Otherwise, print for all Templates the Statement fails against.")
    :id :short-circuit]
   ["-h" "--help" "Display the 'validate' subcommand help menu."]])

(defn- validate*
  "Perform validation on `statement` based on the CLI options map; print any
   validation errors and return `false` if errors exist, `true` otherwise."
  [{:keys [profiles template-ids statement extra-statements
           all-valid short-circuit]}]
  (try
    (let [prof->map #(sref/profile->id-template-map % :validate-profile? false)
          ?temp-ids (not-empty template-ids)
          ?sref-fns (when (not-empty extra-statements)
                      {:get-statement-fn
                       (sref/statement-batch->id-statement-map extra-statements)
                       :get-template-fn
                       (->> profiles (map prof->map) (apply merge))})
          compiled  (per/compile-profiles->validators
                     profiles
                     :selected-templates ?temp-ids
                     :statement-ref-fns  ?sref-fns)
          ;; `:fn-type` is `:errors` instead of `:printer` since we want to
          ;; return whether the error results are empty or not.
          ;; TODO: Redo how validation printing works to be more like in pattern
          ;; matching and be a side effect that can happen for any `:fn-type`?
          error-res (and (some? compiled)
                         (per/validate-statement
                          compiled
                          statement
                          :fn-type :errors
                          :all-valid? all-valid
                          :short-circuit? short-circuit))]
      (when (some? error-res)
        (dorun (->> error-res vals (apply concat) errs/print-errors)))
      (empty? error-res))
    (catch ExceptionInfo e
      (u/print-assert-errors e)
      false)))

(defn validate
  "Perform Statement Template validation based on `arglist`; print any
   validation errors and return `false` if errors exist, `true` if validation
   passes or the `--help` argument was present."
  [arglist]
  (let [parsed  (cli/parse-opts arglist validate-statement-options)
        options (u/handle-parsed-args parsed)]
    (cond
      (= :help options)  true
      (= :error options) false
      :else (validate* options))))
