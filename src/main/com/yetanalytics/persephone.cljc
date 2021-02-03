(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.template :as pan-template]
            [com.yetanalytics.persephone.template-validation :as t]
            [com.yetanalytics.persephone.pattern-validation :as p]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone.utils.errors :as err]
            [com.yetanalytics.persephone.utils.json :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Assertions (Project Pan integration)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; FIXME: We need to set the :relation? key to true, but currently this will
;; cause errors because external IRIs are not supported yet in project-pan.

(defn- assert-profile
  [profile]
  (if-some [err (pan/validate-profile profile :ids? true :print-errs? false)]
    (throw (ex-info "Invalid Profile." err))
    nil))

(defn- assert-template
  [template]
  (if-some [err (s/explain-data ::pan-template/template template)]
    (throw (ex-info "Invalid Statement Template." err))
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Core functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Work with XML and Turtle Profiles
;; TODO: Add Exception messages

(defn compile-profile
  "Take a JSON-LD profile (or an equivalent EDN data structure) as an argument
   and returns a sequence of compiled primary Patterns, which can then be used
   in read-next-statement. Returns nil if there are no primary Patterns in the
   profile."
  [profile]
  (assert-profile profile)
  (if (string? profile)
    ;; JSON-LD
    (-> profile (json/json-to-edn :kwd true) p/profile->fsms)
    ;; EDN
    (-> profile p/profile->fsms)))

(defn profile-templates
  "Take a JSON-LD profile (or an equivalent EDN data structure) and return a
   vector of Statement Templates (in EDN format). Returns nil if there are
   there are no Templates in the Profile."
  [profile]
  (assert-profile profile)
  (if (string? profile)
    ;; JSON-LD
    (-> profile (json/json-to-edn :kwd true) :templates)
    ;; EDN
    (-> profile :templates)))

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
  (assert-template template)
  (let [statement (if (string? stmt) (json/json-to-edn stmt) stmt)]
    (t/validate-statement template statement :err-msg true)))
