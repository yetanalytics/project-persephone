(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha   :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.template           :as pan-template]
            [com.yetanalytics.persephone.template-validation :as t]
            [com.yetanalytics.persephone.pattern-validation  :as p]
            [com.yetanalytics.persephone.utils.fsm    :as fsm]
            [com.yetanalytics.persephone.utils.json   :as json]
            [com.yetanalytics.persephone.utils.errors :as err-printer]
            [com.yetanalytics.persephone.utils.spec   :as stmt-spec]))

(def subreg-iri "https://w3id.org/xapi/profiles/extensions/subregistration")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Assertions (Project Pan integration)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- throw-unknown-opt [opt-keyword]
  (let [msg (str "Unknown option: :" (name opt-keyword))]
    #?(:clj (throw (IllegalArgumentException. msg))
       :cljs (throw (js/Error. msg)))))

;; FIXME: We need to set the :relation? key to true, but currently this will
;; cause errors because external IRIs are not supported yet in project-pan.

(defn- assert-profile
  [profile]
  (when-some [err (pan/validate-profile profile :ids? true :print-errs? false)]
    (throw (ex-info "Invalid Profile!"
                    {:kind   ::invalid-profile
                     :errors err}))))

(defn- assert-template
  [template]
  (when-some [err (s/explain-data ::pan-template/template template)]
    (throw (ex-info "Invalid Statement Template!"
                    {:kind   ::invalid-template
                     :errors err}))))

(defn- assert-dfa
  [pattern-fsm]
  (when-not (= :dfa (:type pattern-fsm))
    (throw (ex-info "Compiled pattern is invalid!"
                    {:kind    ::invalid-dfa
                     :pattern pattern-fsm}))))

(defn- assert-subregs
  [profile-id statement registration subreg-ext]
  (when (some? subreg-ext)
    (if-let [errs (s/explain-data stmt-spec/subreg-ext-spec subreg-ext)]
      (throw (ex-info "Subregistration extension fails spec!"
                      {:kind      ::invalid-subregistration
                       :profile   profile-id
                       :statement statement
                       :errors    errs}))
      (when (= :no-registration registration)
        (throw (ex-info "Subregistrations present without registration!"
                        {:kind      ::invalid-subregistration
                         :profile   profile-id
                         :statement statement}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Validation Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn template->validator
  "Takes `template`, along with an optional :validate-template?
   arg, and returns a tuple of the Statement Template ID and a
   Statement validation function.
  
   :validate-template? is default true. If true, `template->validator`
   checks that `template` conforms to the xAPI Profile spec."
  [template & {:keys [validate-template?] :or {validate-template? true}}]
  (let [template (if (string? template)
                   (json/json->edn template :keywordize? true)
                   template)]
    (when validate-template? (assert-template template))
    [(:id template) (t/create-template-validator template)]))

(defn profile->validator
  "Takes `profile`, along with an optional :validate-profile? arg,
   and returns a vector of tuples of the Statement Template ID and
   its Statement validation function.

   :validate-profile? is default true. If true, `profile->validator`
   checks that `profile` conforms to the xAPI Profile spec."
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
  "Takes `compiled-template` and `statement` where `compiled-template`
   is the result of `template->validator`, and validates `statement`
   against the Statement Template.

   Takes the :fn-type keyword argument, which sets the return value
   and side effects of `validate-statement-vs-template. Has the
   following options:
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
                                     {:type   ::invalid-statement
                                      :errors errs})))
        (throw-unknown-opt fn-type)))))

(defn validate-statement-vs-profile
  "Takes `compiled-profile` and `statement` where `compiled-profile`
   is the result of `profile->validator`, and validates `statement`
   against the Statement Templates in the Profile.

   Takes the :fn-type keyword argument, which sets the return value
   and side effects of `validate-statement-vs-profile.` Has the
   following options:
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
                 extracted using `(-> e ex-data :errors)`."
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
                                   {:type   ::invalid-statement
                                    :errors errors})))
      (throw-unknown-opt fn-type))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern Matching Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Work with XML and Turtle Profiles
;; TODO: Add Exception messages when Patterns are rejected

(defn profile->fsms
  "Take `profile`, a JSON-LD profile (or equivalent EDN data ) and
   returns a map between primary Pattern IDs and their corresponding
   Pattern FSMs. Returns `{}` if there are no primary Patterns in
   `profile`. The ID of `profile` is saved as metadata."
  [profile & {:keys [validate-profile?] :or {validate-profile? true}}]
  (let [profile (if (string? profile)
                  (json/json->edn profile :keywordize? true)
                  profile)
        prof-id (get profile :id)]
    (when validate-profile? (assert-profile profile))
    (-> profile p/profile->fsms (vary-meta assoc :profile-id prof-id))))

(defn match-statement-vs-pattern
  "Takes `pat-fsm`, `state-info`, and `statement`, where `pat-fsm` is
   one value in the map returned by `profile->fsms`. Uses `pat-fsm` to
   validate `statement` and returns a new state info map.
   
   `state-info` is a nilable map with the following fields:
     :states     The next state arrived at in the FSM after reading
                 the previous input. If the FSM could not read the
                 input, then :states is the empty set.
     :accepted?  True if the FSM as arrived at an accept state
                 after reading the previous input; false otherwise.
   If `state-info` is nil, the function starts at the start state.
   If :states is empty, return `state-info`."
  [pat-fsm state-info statement]
  (assert-dfa pat-fsm)
  (let [stmt (if (string? statement) (json/json->edn statement) statement)]
    (fsm/read-next pat-fsm state-info stmt)))

(defn match-statement-vs-profile
  "Takes `pat-fsm-map`, `state-info-map`, and `statement`, where
   `pat-fsm-map` is a return value of `profile->fsms`. Uses
   `pat-fsm-map` to validate `statement` and return an updated value
   for `state-info-map`.

   `state-info-map` is a map of the form:

   `{ registration { pattern-id state-info } }`

   where `state-info` is returned by `match-statement-vs-pattern`.

   `match-statement-vs-profile` will attempt to match `statement`
   against all Patterns in `pat-fsm-map`. If `statement` matches a
   Pattern, the updated state info will be associated with that
   Pattern's ID, which will then be associated with the registration
   value of `statement`. Statements without registrations will be
   assigned a default :no-registration key.
   
   If sub-registrations are present, then `state-info-map` keys will
   be a pair of registrations to sub-registrations. A sub-registration
   object will only be applied when its \"profile\" field corresponds
   to the profile ID of `pat-fsm-map` (given as metadata); if no
   such object has that ID, no sub-registration is applied."
  [pat-fsm-map state-info-map statement]
  (let [stmt
        (if (string? statement) (json/json->edn statement) statement)
        profile-id
        (-> pat-fsm-map meta :profile-id)
        registration
        (get-in stmt ["context" "registration"] :no-registration)
        ?sub-registration-ext
        (get-in stmt ["context" "extensions" subreg-iri])]
    (assert-subregs profile-id stmt registration ?sub-registration-ext)
    (letfn [(subreg-pred
              [subreg-obj]
              (when (= profile-id (get subreg-obj "profile"))
                (get subreg-obj "subregistration")))
            (get-reg-key
              []
              (let [?sub-reg (some subreg-pred ?sub-registration-ext)]
                (if ?sub-reg [registration ?sub-reg] registration)))
            (update-pat-si
              [reg-state-info pat-id pat-fsm]
              (let [pat-state-info  (get reg-state-info pat-id)
                    pat-state-info' (match-statement-vs-pattern
                                     pat-fsm
                                     pat-state-info
                                     stmt)]
                (assoc reg-state-info pat-id pat-state-info')))
            (update-reg-si
              [reg-state-info]
              (reduce-kv update-pat-si reg-state-info pat-fsm-map))]
      (update state-info-map (get-reg-key) update-reg-si))))
