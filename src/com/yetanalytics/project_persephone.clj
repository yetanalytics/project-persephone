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
  in read-next-statement. Returns nil if there are no primary Patterns in the
  profile."
  [profile]
  (try
    (if (string? profile)
      ;; JSON-LD
      (-> profile u/json-to-edn p/profile-to-fsm)
      ;; EDN
      (p/profile-to-fsm profile))
    (catch Exception e (println err/profile-exception-msg) nil)))

(defn profile-templates
  "Take a JSON-LD profile (or an equivalent EDN data structure) and return a
  vector of Statement Templates (in EDN format). Returns nil if there are
  there are no Templates in the Profile."
  [profile]
  (try
    (if (string? profile)
      ;; JSON-LD
      (-> profile u/json-to-edn :templates)
      ;; EDN
      (-> profile :templates vec))
    (catch Exception e (println err/profile-exception-msg) nil)))

(defn validate-statement
  "Takes in a Statement Template and a Statement as arguments, respectively,
  and returns a boolean. If the function returns false, it prints an error
  message detailing all validation errors."
  [template statement]
  (try
    (let [statement (if (string? statement) (u/json-to-edn statement) statement)]
      (t/validate-statement template statement :err-msg true))
    (catch Exception e (println err/profile-exception-msg) nil)))

(defn read-next-statement
  "Uses a compiled Pattern and its current state to validate the next Statement
  in a sequence. Returns a new state if validation is successful or the current
  state if validation fails."
  [pattern statement & [curr-state]]
  (try
    (let [statement (if (string? statement) (u/json-to-edn statement) statement)
          next-state (fsm/read-next pattern statement curr-state)]
      (if-not (false? (:rejected-last next-state))
        (do (err/print-bad-statement statement) next-state)
        next-state))
    (catch Exception e (println err/statement-exception-msg) nil)))
