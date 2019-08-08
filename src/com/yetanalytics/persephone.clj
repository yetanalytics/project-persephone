(ns com.yetanalytics.persephone
  (:require [com.yetanalytics.persephone.template-validation :as t]
            [com.yetanalytics.persephone.pattern-validation :as p]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone.utils.errors :as err]
            [com.yetanalytics.persephone.utils.json :as json]))

;; TODO: Work with XML and Turtle Profiles
;; TODO: Add Exception messages 
(defn compile-profile
  "Take a JSON-LD profile (or an equivalent EDN data structure) as an argument
  and returns a sequence of compiled primary Patterns, which can then be used
  in read-next-statement. Returns nil if there are no primary Patterns in the
  profile."
  [profile]
  (if (string? profile)
    ;; JSON-LD
    (-> profile json/json-to-edn p/profile-to-fsm)
    ;; EDN
    (p/profile-to-fsm profile)))

(defn profile-templates
  "Take a JSON-LD profile (or an equivalent EDN data structure) and return a
  vector of Statement Templates (in EDN format). Returns nil if there are
  there are no Templates in the Profile."
  [profile]
  (if (string? profile)
    ;; JSON-LD
    (-> profile json/json-to-edn :templates)
    ;; EDN
    (-> profile :templates vec)))

(defn validate-statement
  "Takes in a Statement Template and a Statement as arguments, respectively,
  and returns a boolean. If the function returns false, it prints an error
  message detailing all validation errors."
  [template statement]
  (let [statement (if (string? statement) (json/json-to-edn statement) statement)]
    (t/validate-statement template statement :err-msg true)))

(defn read-next-statement
  "Uses a compiled Pattern and its current state to validate the next Statement
  in a sequence. Returns a new state if validation is successful or the current
  state if validation fails."
  [pattern statement & [curr-state]]
  (let [statement (if (string? statement) (json/json-to-edn statement) statement)
        next-state (fsm/read-next pattern statement curr-state)]
    (if-not (false? (:rejected-last next-state))
      (do (err/print-bad-statement statement) next-state)
      next-state)))
