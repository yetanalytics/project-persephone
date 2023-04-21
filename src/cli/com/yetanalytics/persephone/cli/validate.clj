(ns com.yetanalytics.persephone.cli.validate
  (:require [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.cli.util.args :as a]
            [com.yetanalytics.persephone.cli.util.file :as f]
            [com.yetanalytics.persephone.cli.util.spec :as s])
  (:gen-class))

(def validate-statement-options
  [["-p" "--profile URI" "Profile URI filepath/location; must specify one or more."
    :id        :profiles
    :missing   "No Profiles specified."
    :multi     true
    :parse-fn  f/read-profile
    :validate  [s/profile? s/profile-err-msg]
    :update-fn (fnil conj [])]
   ["-i" "--template-id IRI" "IDs of Statement Templates to validate against; can specify zero or more."
    :id        :template-ids
    :multi     true
    :validate  [s/iri? s/iri-err-msg]
    :update-fn (fnil conj [])]
   ["-s" "--statement URI" "Statement filepath/location; must specify one."
    :id       :statement
    :missing  "No Statement specified."
    :parse-fn f/read-statement
    :validate [s/statement? s/statement-err-msg]]
   ["-a" "--all-valid"
    (str "If set, the Statement is not considered valid unless it is valid against ALL Templates. "
         "Otherwise, the Statement only needs to be valid against at least one Template.")
    :id :all-valid]
   ["-c" "--short-circuit"
    (str "If set, then print on only the first Template the Statement fails validation against."
         "Otherwise, print for all Templates the Statement fails against.")
    :id :short-circuit]
   ["-h" "--help" "Display the help menu."]])

(defn- validate
  [{:keys [profiles template-ids statement all-valid short-circuit]}]
  (let [compiled (per/compile-profiles->validators
                  profiles
                  :validate-profiles? false ; already validated as CLI args
                  :selected-templates (not-empty template-ids))
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
