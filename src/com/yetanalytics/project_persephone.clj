(ns com.yetanalytics.project-persephone
  (:require [com.yetanalytics.template-validation :as t]
            [com.yetanalytics.pattern-validation :as p]
            [com.yetanalytics.utils.fsm :as fsm]
            [com.yetanalytics.utils.errors :as err]
            [com.yetanalytics.util :as u]))

;; TODO Work with XML and Turtle Profiles
;; Also make Exception messages more specific
(defn compile-profile
  "Turn a JSON-LD profile into a format that can be used by project-persephone."
  [profile]
  (try
    (if (string? profile)
      ;; JSON-LD
      (-> profile u/json-to-edn p/profile-to-fsm)
      ;; EDN
      (p/profile-to-fsm profile))
    (catch Exception e err/profile-exception-msg)))

(defn check-individual-statement
  "Check an individual Statement against an individual Statement Template.
  Prints an error if the Statement is invalid."
  [template statement]
  (try
    (let [statement (if (string? statement) (u/json-to-edn statement) statement)]
      (t/validate-statement template statement :err-msg true))
    (catch Exception e err/profile-exception-msg)))

(defn read-next-statement
  "Use a compiled profile to validate the next Statement in a sequence.
  Takes in an FSM state as an argument."
  [pattern statement & [&  curr-state]]
  (try
    (let [statement (if (string? statement) (u/json-to-edn statement) statement)
          next-state (fsm/read-next pattern statement curr-state)]
      (if-not (false? (:rejected-last next-state))
        (do (e/print-bad-statement statement) next-state)
        next-state))
    (catch Exception e err/statement-exception-msg)))
