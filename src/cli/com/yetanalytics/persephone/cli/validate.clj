(ns com.yetanalytics.persephone.cli.validate
  (:require [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.cli.util.args :as a]
            [com.yetanalytics.persephone.cli.util.file :as f]
            [com.yetanalytics.persephone.cli.util.spec :as s]
            [com.yetanalytics.persephone.template.statement-ref :as sref])
  (:gen-class))

(def validate-statement-options
  [["-p" "--profile URI"
    "Profile URI filepath/location; must specify one or more."
    :id        :profiles
    :missing   "No Profiles specified."
    :multi     true
    :parse-fn  f/read-profile
    :validate  [s/profile? s/profile-err-msg]
    :update-fn (fnil conj [])]
   ["-i" "--template-id IRI"
    "IDs of Statement Templates to validate against; can specify zero or more."
    :id        :template-ids
    :multi     true
    :validate  [s/iri? s/iri-err-msg]
    :update-fn (fnil conj [])]
   ["-s" "--statement URI"
    "Statement filepath/location; must specify one."
    :id       :statement
    :missing  "No Statement specified."
    :parse-fn f/read-statement
    :validate [s/statement? s/statement-err-msg]]
   ["-e" "--extra-statements URI"
    (str "Extra Statement batch filepath/location; can specify zero or more. "
         "If specified, activates Statement Ref property validation, "
         "where the referred object/context Statement exists in this batch "
         "and its Template exists in a provided Profile.")
    :id        :extra-statements
    :multi     true
    :parse-fn  f/read-statement
    :validate  [s/statement? s/statement-err-msg]
    :update-fn (fnil conj [])]
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
   ["-h" "--help" "Display the help menu."]])

(defn- validate
  [{:keys [profiles template-ids statement extra-statements
           all-valid short-circuit]}]
  (let [prof->map #(sref/profile->id-template-map % :validate-profile? false)
        ?temp-ids (not-empty template-ids)
        ?sref-fns (when (not-empty extra-statements)
                    {:get-statement-fn
                     (sref/statement-batch->id-statement-map extra-statements)
                     :get-template-fn
                     (->> profiles (map prof->map) (apply merge))})
        compiled  (per/compile-profiles->validators
                   profiles
                   :validate-profiles? false ; already validated as CLI args
                   :selected-templates ?temp-ids
                   :statement-ref-fns  ?sref-fns)
        ;; TODO: This is an obvious hack, please fix
        res-str  (with-out-str (per/validate-statement
                                compiled
                                statement
                                :fn-type :printer
                                :all-valid? all-valid
                                :short-circuit? short-circuit))]
    (when (some? res-str) (print res-str))
    (empty? res-str)))

(defn -main [& args]
  (if (validate (a/handle-args args validate-statement-options))
    (System/exit 0)
    (System/exit 1)))

(comment
  (a/handle-args '("--help") validate-statement-options)

  (validate
   (a/handle-args
    '("-p" "../poseidon/dev-resources/profile/calibration.jsonld" 
      "-s" "sample-statement.json"
      "-a" "-c")
    validate-statement-options)))
