(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.template :as pan-template]
            [com.yetanalytics.persephone.template-validation :as t]
            [com.yetanalytics.persephone.pattern-validation :as p]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone.utils.json :as json]
            [com.yetanalytics.persephone.utils.errors :as err-printer]))

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

(defn template->statement-validator
  "Takes a Statement Template, along with an optional :validate-template?
   arg. Default true; if so, validates that the Statement Template
   conforms to the xAPI Profile spec. Returns a tuple of the Template's
   id and its validation function."
  [template & {:keys [validate-template?] :or {validate-template? true}}]
  (let [template (if (string? template)
                   (json/json->edn template :keywordize? true)
                   template)]
    (when validate-template? (assert-template template))
    [(:id template) (t/create-template-validator template)]))

(defn profile->statement-validator
  "Takes a Profile, along with an optional :validate-profile? arg.
   Default true; if so, validates that the Profile conforms to the
   xAPI Profile spec. Returns a vector of tuples of each Template's ID
   and their validation function."
  [profile & {:keys [validate-profile?] :or {validate-profile? true}}]
  (when validate-profile? (assert-profile profile))
  (let [profile (if (string? profile)
                  (json/json->edn profile :keywordize? true)
                  profile)]
    (reduce (fn [acc template]
              (let [validator-fn (t/create-template-validator template)]
                (conj acc [(:id template) validator-fn])))
            []
            (:templates profile))))

(defn validate-statement-vs-template
  "Takes a compiled Statement Template and a Statement, with
   the following optional argument:
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
                 invalid. Always returns nil."
  [compiled-template statement & {:keys [fn-type] :or {fn-type :predicate}}]
  (let [[id validator-fn] compiled-template]
    (assert (fn? validator-fn))
    (let [stmt (if (string? statement) (json/json->edn statement) statement)
          errs (validator-fn stmt)]
      (case fn-type
        :result    errs
        :predicate (nil? errs)
        :option    (if (nil? errs) stmt nil)
        :printer   (when (some? errs)
                     (err-printer/print-error errs id (get stmt "id")))
        :assertion (when-not (nil? errs)
                     (throw (ex-info "Invalid Statement."
                                     {:type   :invalid-statement
                                      :errors errs})))
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
                 data is a map between each Statement Template and
                 the error data they produced.
     :templates  Returns the IDs of the Statement Templates the
                 Statement is valid for.
     :assertion  Returns nil on a valid Statement, throws an
                 exception otherwise where the error data can be
                 extracted using `(-> e ex-data :errors)`.
   :validate-template? - If true, validate the Profile against the
   xAPI Profile spec. Default true; only set to false if you know
   what you're doing!"
  [compiled-profile statement & {:keys [fn-type] :or {fn-type :predicate}}]
  (let [stmt
        (if (string? statement) (json/json->edn statement) statement)
        [valid-ids errors]
        (reduce (fn [[valid-ids errs-map] [id validator-fn]]
                  (if-let [errs (validator-fn stmt)]
                    [valid-ids (assoc errs-map id errs)]
                    [(conj valid-ids id) errs-map]))
                [[] {}]
                compiled-profile)
        passed?
        (or (empty? compiled-profile) ;; vacuously true
            (boolean (not-empty valid-ids)))]
    (case fn-type
      :predicate passed?
      :option    (if passed? statement nil)
      :result    (if passed? nil errors)
      :templates valid-ids
      :assertion (when-not passed?
                   (throw (ex-info "Invalid Statement."
                                   {:type   :invalid-statement
                                    :errors errors})))
      (unknown-option-throw fn-type))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern Matching Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Work with XML and Turtle Profiles
;; TODO: Add Exception messages

(defn compile-profile
  "Take a JSON-LD profile (or an equivalent EDN data structure) as an
   argument and returns a sequence of primary Pattern IDs to their
   corresponding Pattern FSMs, which can then be used in
   `read-next-statement`. Returns `{}` if there are no primary Patterns
   in the profile."
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
     :states     The next state arrived at in the FSM after reading
                 the input. If the FSM cannot read the input, then
                 next-state is the empty set.
     :accepted?  True if the FSM as arrived at an accept state
                 after reading the input; false otherwise.
   If state-info is nil, the function starts at the start state.
   If :states is empty, or if input is nil, return state-info
   without calling the FSM."
  [pat-fsm state-info stmt]
  (assert-dfa pat-fsm)
  (let [statement (if (string? stmt) (json/json->edn stmt) stmt)]
    (fsm/read-next pat-fsm state-info statement)))

(defn match-next-statement
  "Takes a map of Pattern IDs to Patterns FSMs (e.g. the result of
   `compile-profile`), a `all-state-info` map, and a Statement,
   validate that Statement. `curr-states-info` is a map of the
   following structure:

   { registration : { pattern-id : curr-state-info } }

   where `curr-state-info` is the return value of
   `match-next-statement*`

   `match-next-statement` will attempt to match the Statement against
   all compiled Patterns in the collection. It treats all
   Statements with the same registration value as part of the same
   sequence; Statements without registrations will be treated
   as having a default implicit registration. 
   
   Note: Subregistrations are not supported."
  [pat-fsm-map all-state-info stmt]
  #_(assert-dfa pat-fsm)
  (let [statement 
        (if (string? stmt) (json/json->edn stmt) stmt)
        registration
        (get-in statement ["context" "registration"] :no-registration)]
    (update
     all-state-info
     registration
     (fn [reg-state-info]
       (reduce-kv
        (fn [reg-state-info pat-id pat-fsm]
          (let [pat-state-info  (get reg-state-info pat-id)
                pat-state-info' (match-next-statement* pat-fsm
                                                       pat-state-info
                                                       statement)]
            (assoc reg-state-info pat-id pat-state-info')))
        reg-state-info
        pat-fsm-map)))))
