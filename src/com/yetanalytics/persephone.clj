(ns com.yetanalytics.persephone
  (:require [com.yetanalytics.persephone.template-validation :as t]
            [com.yetanalytics.persephone.pattern-validation :as p]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone.utils.errors :as err]
            [com.yetanalytics.persephone.utils.json :as json]))

;; TODO: Work with XML and Turtle Profiles
;; TODO: Add Exception messages
;; TODO: Add project-pan integration
(defn compile-profile
  "Take a JSON-LD profile (or an equivalent EDN data structure) as an argument
  and returns a sequence of compiled primary Patterns, which can then be used
  in read-next-statement. Returns nil if there are no primary Patterns in the
  profile."
  [profile]
  (if (string? profile)
    ;; JSON-LD
    (-> profile json/json-to-edn p/profile->fsm)
    ;; EDN
    (-> profile p/profile->fsm)))

(defn profile-templates
  "Take a JSON-LD profile (or an equivalent EDN data structure) and return a
  vector of Statement Templates (in EDN format). Returns nil if there are
  there are no Templates in the Profile."
  [profile]
  (if (string? profile)
    ;; JSON-LD
    (->> profile json/json-to-edn :templates)
    ;; EDN
    (->> profile :templates)))

(defn read-next-statement
  "Uses a compiled Pattern and its current state info to validate the next
   Statement provided. Returns a new state info map if validation is successful
   or the current one if validation fails. A nil value for the current state
   info indicates that we have not yet started reading statements."
  [pat-fsm curr-state-info stmt]
  (let [statement       (if (string? stmt) (json/json-to-edn stmt) stmt)
        next-state-info (fsm/read-next pat-fsm curr-state-info statement)]
    (if (-> next-state-info :state nil?)
      (do (err/print-bad-statement statement) next-state-info)
      next-state-info)))

(defn validate-statement
  "Takes in a Statement Template and a Statement as arguments, respectively,
  and returns a boolean. If the function returns false, it prints an error
  message detailing all validation errors."
  [template stmt]
  (let [statement (if (string? stmt) (json/json-to-edn stmt) stmt)]
    (t/validate-statement template statement :err-msg true)))
