(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha   :as s]
            [xapi-schema.spec     :as xs]
            [com.yetanalytics.pan.objects.profile  :as pan-profile]
            [com.yetanalytics.pan.objects.template :as pan-template]
            [com.yetanalytics.pan.objects.pattern  :as pan-pattern]
            [com.yetanalytics.persephone.template :as t]
            [com.yetanalytics.persephone.pattern  :as p]
            [com.yetanalytics.persephone.utils.asserts   :as assert]
            [com.yetanalytics.persephone.utils.profile   :as prof]
            [com.yetanalytics.persephone.utils.statement :as stmt]
            [com.yetanalytics.persephone.pattern.fsm     :as fsm]
            [com.yetanalytics.persephone.pattern.failure :as fmeta]
            [com.yetanalytics.persephone.template.errors :as terr-printer]
            [com.yetanalytics.persephone.pattern.errors  :as perr-printer]
            [com.yetanalytics.persephone.template.statement-ref :as sref]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::profiles (s/coll-of ::pan-profile/profile))

;; Function kwargs
(s/def ::validate-profiles? boolean?)
(s/def ::compile-nfa? boolean?)

(s/def ::selected-profiles (s/every ::pan-profile/id))
(s/def ::selected-templates (s/every ::pan-template/id))
(s/def ::selected-patterns (s/every ::pan-pattern/id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Validation Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Specs ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::validator-fn t/validator-spec)
(s/def ::predicate-fn t/predicate-spec)

(def compiled-template-spec
  "Spec for a compiled Template: a map of the Template `:id`, a `:validator-fn`,
   and a `:predicate-fn`."
  (s/keys :req-un [::pan-template/id
                   ::validator-fn
                   ::predicate-fn]))

(def compiled-templates-spec
  "Spec for a coll of compiled Template maps."
  (s/every compiled-template-spec))

;; Validation function kwargs
(s/def ::all-valid? boolean?)
(s/def ::short-circuit? boolean?)
(s/def ::fn-type #{:predicate
                   :filter
                   :errors
                   :templates
                   :printer
                   :assertion})

(def validation-error-map-spec
  "Spec for validation error: a map from the Template ID to a validation
   result map containing `:stmt`, `:temp`, `:pred`, `:vals`, and either
   `:prop` or `:sref`."
  (s/map-of ::pan-template/id
            (s/coll-of t/validation-result-spec :min-count 1)))

;; Template/Pattern -> Validator ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- template->validator
  "Takes `template` and nilable `statement-ref-fns` and returns a map
   containing the Template `:id`, a `:validator-fn`, and a `:predicate-fn`."
  [template ?statement-ref-fns]
  {:id           (:id template)
   :validator-fn (t/create-template-validator template ?statement-ref-fns)
   :predicate-fn (t/create-template-predicate template ?statement-ref-fns)})

(s/fdef compile-profiles->validators
  :args (s/cat :templates ::pan-template/templates
               :kw-args  (s/keys* :opt-un [::sref/statement-ref-fns
                                           ::validate-templates?
                                           ::selected-templates]))
  :ret compiled-templates-spec)

(defn compile-templates->validators
  "Takes a `templates` coll and returns a coll of maps:
   
   | Validator Map Key | Description
   | ---               | ---
   | `:id`             | The Statement Template ID
   | `:validator-fn`   | A function that returns error data if a Statement is invalid against the Template, else `nil`.
   | `:predicate-fn`   | A function that returns `true` if a Statement is valid against the Template, else `false`.
   
   `compile-templates->validators` takes the following kwargs:

   | Keyword Argument      | Description
   | ---                   | ---
   | `:statement-ref-fns`  | A map with two fields: `:get-template-fn` and `get-statement-fn`. If not present, then any Template's StatementRef properties are ignored.
   | `:validate-template?` | Whether to validate against the Template spec and check for ID clashes before compilation; default `true`.
   | `:selected-profiles`  | If present, filters out Profiles whose IDs are not in the coll. (Note that these should be profile IDs, not version IDs.)
   | `:selected-templates` | If present, filters out Templates whose IDs are not in the coll.
   
   The following are the fields of the `:statement-ref-fns` map:

   | Argument Map Key    | Description
   | ---                 | ---
   | `:get-template-fn`  | A function or map that takes a Template ID and returns the Template, or `nil` if not found. See also: `template.statement-ref/profile->id-template-map`.
   | `:get-statement-fn` | A function or map that takes a Statement ID and returns the Statement, or `nil` if not found."
  [templates & {:keys [statement-ref-fns
                       validate-templates?
                       selected-templates]
                :or   {validate-templates? true}}]
  (when validate-templates?
    (dorun (map assert/assert-template templates))
    (assert/assert-template-ids templates))
  (let [?temp-id-set    (when selected-templates (set selected-templates))
        temp->validator (fn [temp]
                          (template->validator temp statement-ref-fns))]
    (cond->> templates
      ?temp-id-set
      (filter (fn [{:keys [id]}] (?temp-id-set id)))
      true
      (map temp->validator))))

(s/fdef compile-profiles->validators
  :args (s/cat :profiles ::profiles
               :kw-args  (s/keys* :opt-un [::sref/statement-ref-fns
                                           ::validate-profiles?
                                           ::selected-profiles
                                           ::selected-templates]))
  :ret compiled-templates-spec)

(defn compile-profiles->validators
  "Takes a `profiles` coll and returns a coll of maps of `:id`,
   `:validator-fn`, and `:predicate-fn`, just like with
   `compile-templates->validators`. Takes the following kwargs:

   | Keyword Argument      | Description
   | ---                   | ---
   | `:statement-ref-fns`  | Same as in `compile-templates->validators`.
   | `:validate-profiles?` | Whether to validate against the Profile spec and check for ID clashes before compilation; default `true`.
   | `:selected-profiles`  | If present, filters out any Profiles whose IDs are not in the coll (Note that these should be profile IDs, not version IDs.)
   | `:selected-templates` | If present, filters out any Templates whose IDs are not present in the coll."
  [profiles & {:keys [statement-ref-fns
                      validate-profiles?
                      selected-profiles
                      selected-templates]
               :or   {validate-profiles? true}}]
  (when validate-profiles?
    (dorun (map assert/assert-profile profiles))
    (assert/assert-profile-ids profiles)
    (assert/assert-profile-template-ids profiles))
  (let [?prof-id-set  (when selected-profiles (set selected-profiles))
        template-coll (cond->> profiles
                        ?prof-id-set
                        (filter (fn [{:keys [id]}]
                                  (?prof-id-set id)))
                        true
                        (reduce (fn [acc {:keys [templates]}]
                                  (concat acc templates))
                                []))]
    (compile-templates->validators template-coll
                                   :statement-ref-fns statement-ref-fns
                                   :selected-templates selected-templates
                                   :validate-templates? false)))

;; Statement Validation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; `validated-statement?` based

(s/fdef validated-statement?
  :args (s/cat :compiled-templates compiled-templates-spec
               :statement ::xs/statement
               :kw-args (s/keys* :opt-un [::all-valid?]))
  :ret boolean?)

(defn validated-statement?
  "Returns `true` if `statement` is valid against any Template (if
   `:all-valid?` is `false`, default) or all Templates (if it
   is `true`) in `compiled-templates`, `false` otherwise."
  [compiled-templates statement & {:keys [all-valid?]
                                   :or   {all-valid? false}}]
  (let [pred-fn (fn [{:keys [predicate-fn]}] (predicate-fn statement))]
    (if all-valid?
      (->> compiled-templates
           (every? pred-fn))
      (->> compiled-templates
           (some pred-fn)
           boolean))))

;; c.f. Just/Option (Some/None) types
(s/fdef validate-statement-filter
  :args (s/cat :compiled-templates compiled-templates-spec
               :statement ::xs/statement
               :kw-args (s/keys* :opt-un [::all-valid?]))
  :ret (s/nilable ::xs/statement))

(defn validate-statement-filter
  "Returns `statement` if it is valid against any Template (if
   `:all-valid?` is `true`, default) or all Templates (if it
   is `false`) in `compiled-templates`, `nil` otherwise."
  [compiled-templates statement & {:keys [all-valid?]
                                   :or   {all-valid? false}}]
  (when (validated-statement? compiled-templates
                              statement
                              :all-valid? all-valid?)
    statement))

;; `validate-statement-errors` based

;; c.f. Result (Ok/Error) types
(s/fdef validate-statement-errors
  :args (s/cat :compiled-templates compiled-templates-spec
               :statement ::xs/statement
               :kw-args (s/keys* :opt-un [::all-valid?
                                          ::short-circuit?]))
  :ret (s/nilable validation-error-map-spec))

(defn validate-statement-errors
  "Returns map from Template IDs to error data maps for each Template
   that `statement` is invalid against. Takes the `:all-valid?` and
   `:short-circuit?` kwargs; the former denotes whether to return
   `nil` if any (default) or all Templates are valid against `statement`;
   the latter determines whether to return the first error (if `true`)
   or all errors (if `false`, default)."
  [compiled-templates statement & {:keys [all-valid? short-circuit?]
                                   :or   {all-valid?     false
                                          short-circuit? false}}]
  (let [err-acc    (if short-circuit?
                     (fn [acc id errs] (reduced (assoc acc id errs)))
                     (fn [acc id errs] (assoc acc id errs)))
        valid-acc  (if all-valid?
                     identity
                     (constantly (reduced nil)))
        conj-error (fn [acc {:keys [id validator-fn]}]
                     (if-some [errs (validator-fn statement)]
                       (err-acc acc id errs)
                       (valid-acc acc)))]
    (->> compiled-templates
         (reduce conj-error {})
         ;; no bad templates => vacuously true
         not-empty)))

(s/fdef validate-statement-assert
  :args (s/cat :compiled-templates compiled-templates-spec
               :statement ::xs/statement
               :kw-args (s/keys* :opt-un [::all-valid?
                                          ::short-circuit?]))
  ;; Spec doesn't test side effects - only catch nil case
  :ret nil?)

(defn validate-statement-assert
  "Throw an ExceptionInfo exception when `statement` is invalid
   against Templates in `compiled-templates`, returns `nil` otherwise.
   `:all-valid?` and `:short-circuit?` are the same as in the
   `validate-statement-errors` function."
  [compiled-templates statement & {:keys [all-valid? short-circuit?]
                                   :or   {all-valid?     false
                                          short-circuit? false}}]
  (when-some [errs (validate-statement-errors compiled-templates
                                              statement
                                              :all-valid? all-valid?
                                              :short-circuit? short-circuit?)]
    (throw (ex-info "Statement Validation Failure."
                    {:kind   ::invalid-statement
                     :errors errs}))))

(s/fdef validate-statement-print
  :args (s/cat :compiled-templates compiled-templates-spec
               :statement ::xs/statement
               :kw-args (s/keys* :opt-un [::all-valid?
                                          ::short-circuit?]))
  ;; Spec doesn't test side effects - only catch nil return
  :ret nil?)

(defn validate-statement-print
  "Prints errors for each Template that `statement` is invalid
   against. `:all-valid?` and `:short-circuit?` are the same as
   in the `validate-statement-errors` function."
  [compiled-templates statement & {:keys [all-valid? short-circuit?]
                                   :or   {all-valid?     false
                                          short-circuit? false}}]
  (when-some [errs (validate-statement-errors compiled-templates
                                              statement
                                              :all-valid? all-valid?
                                              :short-circuit? short-circuit?)]
    (dorun (->> errs
                vals
                (apply concat)
                terr-printer/print-errors))))

;; Other

(s/fdef validate-statement-template-ids
  :args (s/cat :compiled-templates compiled-templates-spec
               :statement ::xs/statement)
  :ret (s/every ::pan-template/id))

(defn validate-statement-template-ids
  "Returns a vector of all the Template IDs that `statement` is
   valid against."
  [compiled-templates statement]
  (let [conj-valid-id (fn [valid-ids {:keys [id predicate-fn]}]
                        (if (predicate-fn statement)
                          (conj valid-ids id)
                          valid-ids))]
    (reduce conj-valid-id [] compiled-templates)))

;; Generic validation

(s/fdef validate-statement
  :args (s/cat :compiled-templates compiled-templates-spec
               :statement ::xs/statement
               :kw-args (s/keys* :opt-un [::fn-type
                                          ::all-valid?
                                          ::short-circuit?]))
  :ret (s/or :predicate boolean?
             :filter    (s/nilable ::xs/statement)
             :errors    (s/nilable validation-error-map-spec)
             :templates (s/every ::pan-template/id)
             :printer   nil?
             :assertion nil?))

(defn validate-statement
  "Takes `compiled-templates` and `statement` where `compiled-templates`
   is the result of `compile-profiles->validators`, and validates
   `statement` against the Statement Templates in the Profile.

   Takes a `:fn-type` kwarg, which sets the return value and side effects
   of `validate-statement`. Has the following options:

   | Keyword Argument | Description
   | ---          | ---
   | `:predicate` | Returns `true` if `statement` is valid for any Statement Template, else `false`. Default.
   | `:filter`    | Returns `statement` if it is valid against any Template, else `nil`.
   | `:errors`    | Returns a map from Template IDs to error data for every Template the Statement is invalid against, `nil` if any Template is valid for `statement`.
   | `:templates` | Returns the IDs of the Templates that `statement` is valid against.
   | `:printer`   | Prints the error data for all Templates the Statement fails validation against, if every Template is invalid for `statement`
   | `:assertion` | Throws an exception upon validation failure (where `(-> e ex-data :errors)` returns all error data) if every Template is invalid for `statement`, else returns`nil`.
   
   Note that the above descriptions are only for when the kwargs
   `:all-valid?` and `:short-circuit?` are `false` (default).
   - If `:all-valid?` is `true`, then the validation is not considered
     `true` unless all Templates are valid against `statement`.
   - If `:short-circuit?` is `true`, then only the error data for the
     first invalid Template is returned."
  [compiled-templates statement & {:keys [fn-type all-valid? short-circuit?]
                                   :or   {fn-type        :predicate
                                          all-valid?     false
                                          short-circuit? false}}]
  (case fn-type
    :predicate
    (validated-statement? compiled-templates
                          statement
                          :all-valid? all-valid?)
    :filter
    (validate-statement-filter compiled-templates
                               statement
                               :all-valid? all-valid?)
    :errors
    (validate-statement-errors compiled-templates
                               statement
                               :all-valid? all-valid?
                               :short-circuit? short-circuit?)
    :templates
    (validate-statement-template-ids compiled-templates
                                     statement)
    :printer
    (validate-statement-print compiled-templates
                              statement
                              :all-valid? all-valid?
                              :short-circuit? short-circuit?)
    :assertion
    (validate-statement-assert compiled-templates
                               statement
                               :all-valid? all-valid?
                               :short-circuit? short-circuit?)
    ;; else
    (let [msg (str "Unknown option: :" (name fn-type))]
      #?(:clj (throw (IllegalArgumentException. msg))
         :cljs (throw (js/Error. msg))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern Matching Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Specs ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def compiled-profiles-spec
  "A compiled profiles spec: a map from Profile IDs to another map of Pattern
   IDs (from that Profile) to a map of compiled FSMs."
  (s/every-kv ::pan-profile/id
              (s/every-kv ::pan-pattern/id p/fsm-map-spec)))

(def registration-key-spec
  "Spec for a registration key - either a single registration UUID string
   or a vector pair of a registration and subregistration UUID."
  (s/or :no-subregistration ::stmt/registration
        :subregistration (s/tuple ::stmt/registration-id
                                  ::stmt/subregistration)))

;; Function kwargs
(s/def ::print? boolean?)

;; State info specs

(def state-info-meta-spec
  "Spec for failure metadata: a nilable map of `::pattern.failure/failure`."
  (s/nilable (s/keys :req-un [::fmeta/failure])))

(def state-info-spec ; state-info + meta
  "Spec for state info + failure metadata."
  (s/and p/state-info-spec
         (s/conformer meta)
         state-info-meta-spec))

(s/def ::accepts
  (s/every (s/tuple registration-key-spec ::pan-pattern/id)))

(s/def ::rejects
  (s/every (s/tuple registration-key-spec ::pan-pattern/id)))

(s/def ::states-map
  (s/every-kv registration-key-spec
              (s/every-kv ::pan-pattern/id state-info-spec)))

(def state-info-map-spec
  "Spec for an individual state info map."
  (s/or :start (s/nilable (s/and map? empty?))
        :continue (s/keys :req-un [::accepts
                                   ::rejects
                                   ::states-map])))

;; Match failure specs

;; TODO: Probably move to the `stmt` namespace
(s/def ::type #{::stmt/missing-profile-reference
                ::stmt/invalid-subreg-no-registration
                ::stmt/invalid-subreg-nonconformant})

(s/def ::error
  (s/keys :req-un [::type ::xs/statement]))

;; TODO: Delete in next break ver
(def ^:deprecated stmt-error-spec
  (s/keys :req-un [::error]))

(def statement-error-spec
  "Spec for a statement error (e.g. during match error)."
  (s/keys :req-un [::error]))

;; Profile to FSM Compilation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef compile-profiles->fsms
  :args (s/cat :profiles ::profiles
               :kw-args  (s/keys* :opt-un [::sref/statement-ref-fns
                                           ::validate-profiles?
                                           ::compile-nfa?
                                           ::selected-profiles
                                           ::selected-patterns]))
  :ret compiled-profiles-spec)

(defn compile-profiles->fsms
  "Take `profiles`, a collection of Profiles, and returns a map of the following
   form:
   ```clojure
   {
     profile-version {
       pattern-id {
         :id ...
         :dfa ...
         :nfa ...
       }
     }
   }
   ```
   where `profile-version` is the Profile version ID and `pattern-id` is the
   ID of a primary Pattern in that Profile. The FSM map keys are as follows:
   
   | FSM Map Key | Description
   | ---         | ---
   | `:id`       | The Pattern ID.
   | `:dfa`      | The DFA used for general Pattern matching.
   | `:nfa`      | The NFA with Pattern metadata used for reconstructing the Pattern path. This is an optional property that is only produced when `:compile-nfa?` is `true`.
   | `:nfa-meta` | The NFA metadata; assoc-ed here in case meta is lost from the `:nfa` value. Only present if `:nfa` is.

   The following are optional keyword arguments:
     
   | Keyword Argument      | Description
   | ---                   | ---
   | `:statement-ref-fns`  | Takes the key-value pairs described in `template->validator`.
   | `:validate-profiles?` | If `true` checks that all Profiles conform to the xAPI Profile spec and that all Profile, Template, and Pattern IDs do not clash. Throws exception if validation fails. Default `true`.
   | `:compile-nfa?`       | If `true` compiles an additional NFA that is used for composing detailed error traces. Default `false`.
   | `:selected-profiles`  | Coll that, if present, filters out any Profiles whose IDs are not in the coll. (Note that these should be profile IDs, not profile version IDs.)
   | `:selected-patterns`  | Coll that, if present, filters out any primary Patterns whose IDs are not in the coll."
  [profiles & {:keys [statement-ref-fns
                      validate-profiles?
                      compile-nfa?
                      selected-profiles
                      selected-patterns]
               :or   {validate-profiles? true
                      compile-nfa?       false}}]
  (when validate-profiles?
    (dorun (map assert/assert-profile profiles))
    (assert/assert-profile-ids profiles)
    (assert/assert-profile-template-ids profiles)
    (assert/assert-profile-pattern-ids profiles))
  (let [opt-map      {:statement-ref-fns statement-ref-fns
                      :compile-nfa?      compile-nfa?
                      ;; TODO: Rename back to :selected-patterns?
                      :select-patterns   selected-patterns}
        ?prof-id-set (when selected-profiles (set selected-profiles))
        profiles     (cond->> profiles
                       ?prof-id-set
                       (filter (fn [{:keys [id]}] (?prof-id-set id))))
        prof-id-seq  (map (fn [prof] (->> prof prof/latest-version :id))
                          profiles)
        pat-fsm-seq  (map (fn [prof] (p/profile->fsms prof opt-map))
                          profiles)]
    (into {} (map (fn [prof-id pf-map] [prof-id pf-map])
                  prof-id-seq
                  pat-fsm-seq))))

;; Registration Key Construction

(defn- construct-registration-key
  [profile-id registration ?subreg-ext]
  (if-some [subregistration
            (and ?subreg-ext
                 (stmt/get-subregistration-id profile-id ?subreg-ext))]
    [registration subregistration]
    registration))

;; Statement Pattern Matching ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- cond-println
  "Println only if `print?` is `true`."
  [print? expr]
  (when print? (println expr)))

(defn- match-statement-vs-pattern
  "Match `statement` against the Pattern DFA, and upon failure (i.e.
   `fsm/read-next` returns `#{}`), append printable failure metadata
   to the return value."
  [{pat-dfa  :dfa
    ?pat-nfa :nfa
    :as fsms}
   state-info
   statement
   print?]
  (let [start-opts  {:record-visits? (some? ?pat-nfa)}
        new-st-info (fsm/read-next pat-dfa start-opts state-info statement)]
    (if (fsm/rejected? new-st-info)
      (if-some [{old-fail-meta :failure :as old-meta} (meta state-info)]
        ;; The state info is stuck in the failure state from a prev iteration
        (do
          (cond-println print? (perr-printer/failure-message-str old-fail-meta))
          (with-meta new-st-info old-meta))
        ;; The state info encountered a failure state on this iteration
        (let [fail-meta (fmeta/construct-failure-info
                         fsms
                         state-info
                         statement)]
          (cond-println print? (perr-printer/failure-message-str fail-meta)) 
          (with-meta new-st-info {:failure fail-meta})))
      new-st-info)))

(s/fdef match-statement
  :args (s/cat :compiled-profiles compiled-profiles-spec
               :state-info-map    (s/or :error statement-error-spec
                                        :ok state-info-map-spec)
               :statement         ::xs/statement
               :kwargs            (s/keys* :opt-un [::print?]))
  :ret (s/or :error statement-error-spec
             :ok state-info-map-spec))

(defn match-statement
  "Takes `compiled-profiles`, `state-info-map`, and `statement`, where
   `compiled-profiles` is a return value of `compile-profiles->fsms`.
   Matches `statement` to against `compiled-profiles` and returns an
   updated value of `state-info-map`.

   `state-info-map` is a map with the following keys:

   | Map Key       | Description
   | ---           | ---
   | `:accepts`    | A vector of `[registration-key pattern-id]` key paths for accepted state infos (where `:accepted?` is `true`).
   | `:rejects`    | A vecotr of `[registration-key pattern-id]` for rejected state infos (where they are empty sets).
   | `:states-map` | A map of the form `{registration-key {pattern-id state-info}}}`
   
   `registration-key` can either be a registration UUID or a
   pair of registration and subregistration UUIDs. Statements without
   registrations will be assigned a default `:no-registration` key.
   
   `state-info` is a map of the following keys:

   | Map Key      | Description
   | ---          | ---
   | `:state`     | The current state in the FSM, i.e. where is the current location in the Pattern?
   | `:accepted?` | If that state is an accept state, i.e. did the inputs fully match the Pattern?
   | `:visited`   | The vec of visited Statement Templates; this is only present if `compiled-profiles` was compiled with `compile-nfa?` set to `true`.

   On error, this function returns the map
   ```clojure
   {
     :error {
       :type error-kw
       :statement {...}
     }
   }
   ```
   where `error-kw` is one of the following:

   | Pattern Match Error Keyword        | Description
   | ---                                | ---
   | `::missing-profile-reference`      | If `statement` does not have a Profile ID from `compiled-profiles` as a category context Activity.
   | `::invalid-subreg-no-registration` | If a sub-registration is present without a registration.
   | `::invalid-subreg-nonconformant`   | If the sub-registration extension value is invalid.
   
   `match-statement` takes in an optional `:print?` kwarg; if `true`,
   then prints any error or match failure."
  [compiled-profiles state-info-map statement & {:keys [print?]
                                                 :or   {print? false}}]
  (if-some [match-err (:error state-info-map)]
    (do
      (cond-println print? (perr-printer/error-message-str match-err))
      state-info-map)
    (let [reg-pat-st-m  (:states-map state-info-map)
          prof-id-set   (set (keys compiled-profiles))
          stmt-prof-ids (stmt/get-statement-profile-ids statement prof-id-set)
          stmt-reg      (stmt/get-statement-registration statement)
          ?stmt-subreg  (stmt/get-statement-subregistration statement stmt-reg)]
      (if-let [err-kw (or (when (keyword? stmt-prof-ids) stmt-prof-ids)
                          (when (keyword? ?stmt-subreg) ?stmt-subreg))]
        (let [match-err {:type      err-kw
                         :statement statement}]
          (cond-println print? (perr-printer/error-message-str match-err))
          {:error match-err})
        (let [match-res
              (for [;; Map over profile IDs
                    [prof-id pat-fsm-m] compiled-profiles
                    :when (contains? stmt-prof-ids prof-id)
                    :let [reg-key (construct-registration-key
                                   prof-id
                                   stmt-reg
                                   ?stmt-subreg)]
                    ;; Map over pattern IDs
                    [pat-id fsm-m] pat-fsm-m
                    :let [old-st-info (get-in reg-pat-st-m [reg-key pat-id])
                          new-st-info (match-statement-vs-pattern
                                       fsm-m
                                       old-st-info
                                       statement
                                       print?)]]
                [[reg-key pat-id] new-st-info])
              new-states-map
              (reduce (fn [m [assoc-k new-st-info]]
                        (assoc-in m assoc-k new-st-info))
                      reg-pat-st-m
                      match-res)
              new-accepts
              (reduce (fn [acc [assoc-k new-st-info]]
                        (cond-> acc
                          (fsm/accepted? new-st-info)
                          (conj assoc-k)))
                      []
                      match-res)
              new-rejects
              (reduce (fn [acc [assoc-k new-st-info]]
                        (cond-> acc
                          (fsm/rejected? new-st-info)
                          (conj assoc-k)))
                      []
                      match-res)]
          {:accepts    new-accepts
           :rejects    new-rejects
           :states-map new-states-map})))))

(s/fdef match-statement-batch
  :args (s/cat :compiled-profiles compiled-profiles-spec
               :state-info-map    (s/or :error statement-error-spec
                                        :ok state-info-map-spec)
               :statement-batch   (s/coll-of ::xs/statement))
  :ret (s/or :error statement-error-spec
             :ok state-info-map-spec))

(defn match-statement-batch
  "Similar to `match-statement`, except takes a batch of Statements (instead
   of a single Statement) and sorts them by timestamp before matching.
   Short-circuits if an error (e.g. missing Profile ID reference) is detected."
  [compiled-profiles state-info-map statement-batch & {:keys [print?]
                                                       :or   {print? false}}]
  (let [match-statement (fn [st-info-map stmt]
                          (match-statement compiled-profiles
                                           st-info-map
                                           stmt
                                           :print? print?))]
    (loop [stmt-coll   (sort stmt/compare-statements-by-timestamp
                             statement-batch)
           st-info-map state-info-map]
      (if-let [stmt (first stmt-coll)]
        (let [match-res (match-statement st-info-map stmt)]
          (if (:error match-res)
            ;; Error keyword - early termination
            match-res
            ;; Valid state info map - continue
            (recur (rest stmt-coll) match-res)))
        ;; Finish matching
        st-info-map))))
