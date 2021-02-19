(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.template :as pan-template]
            [com.yetanalytics.persephone.template-validation :as t]
            [com.yetanalytics.persephone.pattern-validation :as p]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone.utils.json :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Assertions (Project Pan integration)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; FIXME: We need to set the :relation? key to true, but currently this will
;; cause errors because external IRIs are not supported yet in project-pan.

(defn- assert-profile
  [profile]
  (when-some [err (pan/validate-profile profile :ids? true :print-errs? false)]
    (throw (ex-info "Invalid Profile." err))))

(defn- assert-template
  [template]
  (when-some [err (s/explain-data ::pan-template/template template)]
    (throw (ex-info "Invalid Statement Template." err))))

(defn- assert-dfa
  [pattern-fsm]
  (when-not (= :dfa (:type pattern-fsm))
    (throw (ex-info "Compiled pattern is invalid!" {:pattern pattern-fsm}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Validation Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- unknown-option-throw [fn-type-kwd]
  (let [msg (str "Unknown option: " (name fn-type-kwd))]
    #?(:clj (throw (Exception. msg))
       :cljs (throw (js/Error. msg)))))

(defn validate-statement-vs-template
  "Takes a Statement Template and a Statement as arguments, with
   the following optional arguments:
   :fn-type - Sets the return value and side effects of the function:
     :predicate  Returns true for a valid Statement, false otherwise.
                 Default.
     :option     Returns the Statement if it's valid, nil otherwise
                 (c.f. Option/Maybe types).
     :result     Returns the validation error data as a seq if the
                 Statement is invalid, else nil (c.f. Result types).
     :assertion  Returns nil on a valid Statement, throws an
                 exception otherwise where the error data can be
                 extracted using `(-> e ex-data :errors)`.
     :printer    Prints an error message when the Statement is
                 invalid. Always returns nil.
   :validate-template? - If true, validate the Statement Template
   against the xAPI Profile spec. Default true; only set to false
   if you know what you're doing!"
  [temp stmt & {:keys [fn-type validate-template?]
                :or   {fn-type            :predicate
                       validate-template? true}}]
  (let [statement (if (string? stmt) (json/json->edn stmt) stmt)
        template  (if (string? temp) (json/json->edn temp :keywordize? true) temp)]
    (when validate-template? (assert-template template))
    (let [errs (t/validate-statement template statement)]
      (case fn-type
        :predicate (nil? errs)
        :option    (if (nil? errs) statement nil)
        :result    errs
        :printer   (do (when (some? errs) (t/print-error template
                                                         statement
                                                         errs))
                       nil)
        :assertion (if (nil? errs)
                     nil
                     (throw (ex-info "Invalid Statement." {:errors errs})))
        (unknown-option-throw fn-type)))))

(defn validate-statement-vs-profile
  "Takes a Profile and a Statement as arguments. The Statement is
   considered valid if the Statement is valid for at least one
   Statement Template in the Profile. Takes the following optional
   arguments:
   :fn-type - Sets the return value and side effects of the function:
     :predicate  Returns true for a valid Statement, false otherwise.
                 Default.
     :option     Returns the Statement if it's valid, nil otherwise
                 (c.f. Option/Maybe types).
     :result     Returns the validation error data if the Statement
                 is invalid, nil otherwise (c.f. Result types). The
                 data is a vector of error data for each Statement
                 Template.
     :templates  Returns the IDs of the Statement Templates the
                 Statement is valid for.
     :assertion  Returns nil on a valid Statement, throws an
                 exception otherwise where the error data can be
                 extracted using `(-> e ex-data :errors)`.
   :validate-template? - If true, validate the Profile against the
   xAPI Profile spec. Default true; only set to false if you know
   what you're doing!"
  [prof stmt & {:keys [fn-type validate-profile?]
                :or   {fn-type           :predicate
                       validate-profile? true}}]
  (let [statement (if (string? stmt) (json/json->edn stmt) stmt)
        profile   (if (string? prof) (json/json->edn prof :keywordize? true) prof)
        reduce-fn (fn [[template-ids results] template]
                    (if-let [errs (t/validate-statement template statement)]
                      [template-ids
                       (conj results errs)]
                      [(conj template-ids (:id template))
                       results]))]
    (when validate-profile? (assert-profile profile))
    (let [[tmpl-ids errors] (reduce reduce-fn [[] []] (:templates profile))
          is-passed         (or (empty? (:templates profile)) ; vacuously true
                                (not-empty tmpl-ids))]
      (case fn-type
        :predicate (boolean is-passed)
        :option    (if is-passed statement nil)
        :result    (if is-passed nil errors)
        :templates tmpl-ids
        :assertion (if is-passed
                     nil
                     (throw (ex-info "Invalid Statement." {:errors errors})))
        (unknown-option-throw fn-type)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern Matching Functions
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
                  (json/json->edn profile :keywordize? true)
                  profile)]
    ;; TODO: Make assert-profile toggleable?
    (assert-profile profile)
    (-> profile p/profile->fsms)))

(defn match-next-statement*
  "Uses a compiled Pattern and its current state info to validate the
   next Statement provided. Returns a new state info map if
   validation is successful or the current one if validation fails.
   
   The current state info map has the following fields:
     :next-state  The next state arrived at in the FSM after reading
                  the input. If the FSM cannot read the input, then
                  next-state is nil.
     :accepted?   True if the FSM as arrived at an accept state
                  after reading the input; false otherwise.
     :rejected?   True if the FSM will not be able to read any more
                  states, false otherwise. True iff :next-state
                  is nil.
   If state-info is nil, the function starts at the start state.
   If the state value is nil, or if input is nil, return state-info
   without calling the FSM."
  [pat-fsm curr-state-info stmt]
  (assert-dfa pat-fsm)
  (let [statement (if (string? stmt) (json/json->edn stmt) stmt)]
    (fsm/read-next pat-fsm curr-state-info statement)))

(defn match-next-statement
  "Takes a compiled Pattern, current states info, and a Statement and
   validates said Statement. The current state info is a mapping
   from registrations to the current state info for that
   registration. Assumes all Statements without a registration
   property have the same implicit registration. See
   match-next-statement* for details on the current state info map.
   Note: Subregistrations are not supported."
  [pat-fsm curr-states-info stmt]
  (assert-dfa pat-fsm)
  (let [statement    (if (string? stmt) (json/json->edn stmt) stmt)
        registration (get-in statement
                             ["context" "registration"]
                             :no-registration)]
    (update curr-states-info
            registration
            (fn [state-info] (match-next-statement* pat-fsm
                                                    state-info
                                                    statement)))))
