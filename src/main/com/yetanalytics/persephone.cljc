(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.utils.json :refer [convert-json]]
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
;; 
;; FIXME: Apply convert-profile here so that we don't have to convert profiles
;; twice.
(defn- conform-profile
  [profile]
  (if-some [err (pan/validate-profile profile :ids? true :print-errs? false)]
    (throw (ex-info "Invalid Profile." err))
    profile))

(defn- assert-profile
  [profile]
  (if-some [err (pan/validate-profile profile :ids? true :print-errs? false)]
    (throw (ex-info "Invalid Profile." err))
    nil))

(defn- conform-template
  [template]
  (if-some [err (s/explain-data ::pan-template/template template)]
    (throw (ex-info "Invalid Statement Template." err))
    template))

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
  (let [profile (if (string? profile)
                  ; JSON (need to remove @ char)
                  (convert-json profile "_")
                  ; EDN
                  profile)]
    (-> profile conform-profile p/profile->fsms)))

(defn profile-templates
  "Take a JSON-LD profile (or an equivalent EDN data structure) and return a
   vector of Statement Templates (in EDN format). Returns nil if there are
   there are no Templates in the Profile."
  [profile]
  (let [profile (if (string? profile)
                  ; JSON (need to remove @ char)
                  (convert-json profile "_")
                  ; EDN
                  profile)]
    (-> profile conform-profile :templates)))

(defn read-next-statement
  "Uses a compiled Pattern and its current state info to validate the next
   Statement provided. Returns a new state info map if validation is successful
   or the current one if validation fails. A nil value for the current state
   info indicates that we have not yet started reading statements."
  [pat-fsm curr-state-info stmt]
  (let [statement       (if (string? stmt) (json/json->edn stmt) stmt)
        next-state-info (fsm/read-next pat-fsm curr-state-info statement)]
    (if (-> next-state-info :state nil?)
      (do (err/print-bad-statement statement) next-state-info)
      next-state-info)))

(defn validate-statement
  "Takes in a Statement Template and a Statement as arguments, respectively,
   and returns a boolean. If the function returns false, it prints an error
   message detailing all validation errors."
  [template stmt]
  (let [statement (if (string? stmt) (json/json->edn stmt) stmt)
        template  (if (string? template) (json/json->edn template) template)]
    (-> template
        conform-template
        (t/validate-statement statement :err-msg true))))

(defn validate-statement-vs-template
  "Takes a Statement Template and a Statement as arguments, with the following
   optional arguments:
   :fn-type - Sets the return value and exception effects of the function:
     :predicate  Returns true for a valid Statement, false otherwise. Default.
     :option     Returns the Statement if it's valid, nil otherwise
                 (c.f. Option/Maybe types).
     :result     Returns the validation error data if the Statement is invalid,
                 nil otherwise (c.f. Result types).
     :assertion  Returns nil on a valid Statement, throws an exception otherwise
                 that carries the error map as extra data.
     :printer    Prints an error message when the Statement is invalid. Always
                 returns nil.
   :validate-template? - If true, validate the Profile against the xAPI Profile
                         spec. Default true; only set to false if you know what
                         you're doing!"
  [temp stmt {:keys [fn-type validate-template?]
              :or   {fn-type            :predicate
                     validate-template? true}}]
  (let [statement (if (string? stmt) (json/json->edn stmt) stmt)
        template  (if (string? temp) (json/json->edn temp) temp)]
    (when validate-template? (assert-template template))
    (let [err (t/validate-statement* template statement)]
      (case fn-type
        :predicate (nil? err)
        :option    (if (nil? err) statement nil)
        :result    err
        :printer   (do (when (nil? err) (t/print-error template statement err))
                       nil)
        :assertion (if (nil? err)
                     nil
                     (throw (ex-info "Invalid Statement." err)))))))

(defn validate-statement-vs-profile
  "Takes a Profile and a Statement as arguments. The Statement is considered
   valid if the Statement is valid for at least one Statement Template. Same
   options as validate-statement-vs-templates, except :printer is not an
   available option for fn-type."
  [prof stmt {:keys [fn-type validate-profile?]
              :or {fn-type           :predicate
                   validate-profile? true}}]
  (let [statement (if (string? stmt) (json/json->edn stmt) stmt)
        profile   (if (string? prof) (convert-json prof) prof)]
    (when validate-profile? (assert-profile profile))
    (let [results   (->> profile
                         :templates
                         (mapv (fn [t] t/validate-statement* t statement)))
          errors    (filterv some? results)
          is-passed (not-empty (filterv nil? results))]
      (case fn-type
        :predicate (boolean is-passed)
        :option    (if is-passed statement nil)
        :result    (if is-passed nil errors)
        :assertion (if is-passed
                     nil
                     (throw (ex-info "Invalid Statement." {:errors errors})))))))

;; TODO: Add tests
