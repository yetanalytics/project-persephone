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
   ["-i" "--template-id IRI" "IDs of Statement Templates to validate against; can specify one or more."
    :id        :template-ids
    :multi     true
    :validate  [s/iri? s/iri-err-msg]
    :update-fn (fnil conj [])]
   ["-s" "--statement URI" "Statement filepath/location; must specify one."
    :id       :statement
    :missing  "No Statement specified."
    :parse-fn f/read-statement
    :validate [s/statement? s/statement-err-msg]]
   ["-h" "--help" "Display the help menu."]])

(defn- validate
  [{:keys [profiles template-ids statement]}]
  (let [compiled (per/compile-profiles->validators
                  profiles
                  :validate-profiles? false ; already validated as CLI args
                  :selected-templates (not-empty template-ids))]
    (per/validate-statement compiled statement :fn-type :printer)))

(defn -main [& args]
  (validate (a/handle-args args validate-statement-options)))
