(ns com.yetanalytics.persephone.server.validate
  (:require [clojure.tools.cli :as cli]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.utils.cli :as u])
  (:import [clojure.lang ExceptionInfo]))

;; TODO: Currently no way to provide Statement Ref property matching since
;; that requires a stream of statements to come from somewhere, which for the
;; webserver is during runtime, not compile-time.

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
    (str "IDs of Statement Templates to validate against; can specify zero or more. "
         "Filters out all Templates that are not included.")
    :id        :template-ids
    :multi     true
    :validate  [u/iri? u/iri-err-msg]
    :update-fn u/conj-argv]
   ["-a" "--all-valid"
    (str "If set, any Statement is not considered valid unless it is valid "
         "against ALL Templates. "
         "Otherwise, a Statement only needs to be valid against at least one Template.")
    :id :all-valid]
   ["-c" "--short-circuit"
    (str "If set, then print on only the first Template any Statement fails "
         "validation against. "
         "Otherwise, print for all Templates a Statement fails against.")
    :id :short-circuit]
   ["-h" "--help" "Display the 'validate' subcommand help menu."]])

(defonce validator-ref
  (atom {:validators     nil
         :all-valid?     false
         :short-circuit? false}))

(defn compile-templates!
  "Parse `arglist`, compile Profiles into Template validators, and store them
   in-memory. Returns either `:help`, `:error`, or `nil`."
  [arglist]
  (let [parsed  (cli/parse-opts arglist validate-statement-options)
        options (u/handle-parsed-args parsed)]
    (if (keyword? options)
      options
      (try (let [{:keys [profiles template-ids all-valid short-circuit]}
                 options
                 validators
                 (per/compile-profiles->validators
                  profiles
                  :selected-templates (not-empty template-ids))]
             (swap! validator-ref 
                    assoc
                    :validators validators
                    :all-valid? all-valid
                    :short-circuit? short-circuit)
             nil)
           (catch ExceptionInfo e
             (u/print-assert-errors e)
             :error)))))

(defn validate
  "Perform validation on `statement` against the Template validators that
   are stored in-memory in the webserver."
  [statement]
  (let [{:keys [validators all-valid? short-circuit?]} @validator-ref]
    (per/validate-statement validators
                            statement
                            :fn-type :errors
                            :all-valid? all-valid?
                            :short-circuit? short-circuit?)))
