(ns com.yetanalytics.project-persephone
  (:require [com.yetanalytics.template-validation :as t]
            [com.yetanalytics.pattern-validation :as p]
            [com.yetanalytics.utils.fsm :as fsm]
            [com.yetanalytics.utils.errors :as err]
            [com.yetanalytics.util :as u]))

;; TODO Work with XML and Turtle Profiles
;; Also make Exception messages more specific
(defn compile-profile
  "Take a JSON-LD profile (or an equivalent EDN data structure) as an argument
  and returns a sequence of compiled primary Patterns, which can then be used
  in read-next-statement."
  [profile]
  (try
    (if (string? profile)
      ;; JSON-LD
      (-> profile u/json-to-edn p/profile-to-fsm)
      ;; EDN
      (p/profile-to-fsm profile))
    (catch Exception e err/profile-exception-msg)))

(defn validate-statement
  "Takes in a Statement Template and a Statement as arguments, respectively,
  and reutrns a boolean. If the function returns false, it prints an error
  message detailing all validation errors."
  [template statement]
  (try
    (let [statement (if (string? statement) (u/json-to-edn statement) statement)]
      (t/validate-statement template statement :err-msg true))
    (catch Exception e err/profile-exception-msg)))

(defn read-next-statement
  "Uses a compiled Pattern and its current state to validate the next Statement
  in a sequence. Returns a new state if validation is successful or the current
  state if validation fails."
  [pattern statement & [curr-state]]
  (try
    (let [statement (if (string? statement) (u/json-to-edn statement) statement)
          next-state (fsm/read-next pattern statement curr-state)]
      (if-not (false? (:rejected-last next-state))
        (do (e/print-bad-statement statement) next-state)
        next-state))
    (catch Exception e err/statement-exception-msg)))
