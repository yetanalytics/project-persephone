(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha   :as s]
            [xapi-schema.spec     :as xs]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.profile  :as pan-profile]
            [com.yetanalytics.pan.objects.template :as pan-template]
            [com.yetanalytics.pan.objects.pattern  :as pan-pattern]
            [com.yetanalytics.persephone.template :as t]
            [com.yetanalytics.persephone.pattern  :as p]
            [com.yetanalytics.persephone.utils.json      :as json]
            [com.yetanalytics.persephone.utils.maps      :as maps]
            [com.yetanalytics.persephone.utils.profile   :as prof]
            [com.yetanalytics.persephone.utils.statement :as stmt]
            [com.yetanalytics.persephone.pattern.fsm     :as fsm]
            [com.yetanalytics.persephone.template.errors :as err-printer]
            [com.yetanalytics.persephone.pattern.errors  :as perr-printer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Coercions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- coerce-statement
  [statement]
  (if (string? statement)
    (json/json->edn statement)
    statement))

(defn- coerce-template
  [template]
  (if (string? template)
    (json/json->edn template :keywordize? true)
    template))

(defn- coerce-profile
  [profile]
  (if (string? profile)
    (json/json->edn profile :keywordize? true)
    profile))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Assertions (Project Pan integration)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- throw-unknown-opt [opt-keyword]
  (let [msg (str "Unknown option: :" (name opt-keyword))]
    #?(:clj (throw (IllegalArgumentException. msg))
       :cljs (throw (js/Error. msg)))))

;; FIXME: We need to set the :relation? key to true, but currently this will
;; cause errors because external IRIs are not supported yet in project-pan.

(s/def ::profiles
  (s/coll-of (s/or :json (s/and (s/conformer coerce-profile)
                                ::pan-profile/profile)
                   :edn ::pan-profile/profile)))

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

;; TODO: Make these asserts Project Pan's responsibility

(defn- assert-profile-ids
  [profiles]
  (let [prof-ids (map :id profiles)]
    (when (not= prof-ids (distinct prof-ids))
      (throw (ex-info "Profile IDs are not unique!"
                      {:type ::non-unique-profile-ids
                       :ids  prof-ids})))))

(defn- assert-template-ids
  [profiles]
  (let [temp-ids (mapcat (fn [{:keys [templates]}] (map :id templates))
                         profiles)]
    (when (not= temp-ids (distinct temp-ids))
      (throw (ex-info "Template IDs are not unique!"
                      {:type ::non-unique-template-ids
                       :ids  temp-ids})))))

(defn- assert-pattern-ids
  [profiles]
  (let [pat-ids (mapcat (fn [{:keys [patterns]}] (map :id patterns))
                        profiles)]
    (when (not= pat-ids (distinct pat-ids))
      (throw (ex-info "Pattern IDs are not unique!"
                      {:type ::non-unique-pattern-ids
                       :ids  pat-ids})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Validation Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn profile->id-template-map
  "Takes `profile` and returns a map between Statement Template IDs
   and the Templates themselves. Used for Statement Ref Template
   resolution.
   
   :validate-profile? is default true. If true, `profile->validator`
   checks that `profile` conforms to the xAPI Profile spec."
  [profile & {:keys [validate-profile?] :or {validate-profile? true}}]
  (when validate-profile? (assert-profile profile))
  (let [profile (coerce-profile profile)]
    (maps/mapify-coll (:templates profile))))

;; Doesn't exactly conform to stmt batch requirements since in theory,
;; a statement ref ID can refer to a FUTURE statement in the batch.
;; However, this shouldn't really matter in practice.
(defn statement-batch->id-statement-map
  "Takes the coll `statement-batch` and returns a map between
   Statement IDs and the Statement themselves. Used for Statement
   Ref resolution, particularly in statement batch matching."
  [statement-batch]
  (maps/mapify-coll statement-batch :string? true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Validation Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: More sophisticated function specs
(s/def ::get-template-fn fn?)
(s/def ::get-statement-fn fn?)

(s/def ::statement-ref-fns
  (s/keys :req-un [::get-template-fn
                   ::get-statement-fn]))

(defn template->validator
  "Takes `template`, along with an optional :validate-template?
   arg, and returns a map contaiing the Statement Template ID,
   a validation function, and a predicate function.

   :statement-ref-fns is a map with the following key-val pairs:
     :get-template-fn   Function that takes a Statement Template ID
                        and returns the corresponding Template. Can be
                        created using `profile->id-template-map`.
                        Must return `nil` if the Template is not found.
     :get-statement-fn  Function that takes a Statement ID and
                        returns the corresponding Statement. Must
                        return `nil` if the Statement is not found.
   If :statement-ref-fns is not provided, Statement Ref Template
   properties are ignored.

   :validate-template? is default true. If true, `template->validator`
   checks that `template` conforms to the xAPI Profile spec."
  [template & {:keys [statement-ref-fns validate-template?]
               :or   {validate-template? true}}]
  (let [template (coerce-template template)]
    (when validate-template? (assert-template template))
    {:id           (:id template)
     :validator-fn (t/create-template-validator template statement-ref-fns)
     :predicate-fn (t/create-template-predicate template statement-ref-fns)}))

(defn profile->validator
  "Takes `profile`, along with an optional :validate-profile? arg,
   and returns a vector of tuples of the Statement Template ID and
   its Statement validation function.

   :statement-ref-fns takes the key-value pairs described in
   `template->validator`.

   :validate-profile? is default true. If true, `profile->validator`
   checks that `profile` conforms to the xAPI Profile spec."
  [profile & {:keys [statement-ref-fns validate-profile?]
              :or   {validate-profile? true}}]
  (when validate-profile? (assert-profile profile))
  (let [profile (coerce-profile profile)]
    (reduce
     (fn [acc template]
       (conj acc (template->validator template
                                      :statement-ref-fns statement-ref-fns
                                      :validate-template? false)))
     []
     (:templates profile))))

(defn compile-profiles->validators
  [profiles & {:keys [statement-ref-fns
                      validate-profiles?
                      selected-profiles
                      selected-templates]
               :or   {validate-profiles? true}}]
  (when validate-profiles?
    (dorun (map assert-profile profiles))
    (assert-profile-ids profiles)
    (assert-template-ids profiles))
  (let [?prof-id-set    (when selected-profiles (set selected-profiles))
        ?temp-id-set    (when selected-templates (set selected-templates))
        temp->validator (fn [temp]
                          (template->validator
                           temp
                           :statement-ref-fns statement-ref-fns
                           :validate-template? false))]
    (cond->> profiles
      ?prof-id-set
      (filter (fn [{:keys [id]}] (?prof-id-set id)))
      true
      (reduce (fn [acc {:keys [templates]}] (concat acc templates)) [])
      ?temp-id-set
      (filter (fn [{:keys [id]}] (?temp-id-set id)))
      true
      (map temp->validator))))

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
  (let [stmt (coerce-statement statement)
        {:keys [validator-fn predicate-fn]} compiled-template]
    (case fn-type
      :predicate
      (predicate-fn stmt)
      :option
      (when (predicate-fn stmt) stmt)
      :result
      (validator-fn stmt)
      :printer
      (when-some [errs (validator-fn stmt)]
        (err-printer/print-errors errs))
      :assertion
      (when-some [errs (validator-fn stmt)]
        (throw (ex-info "Invalid Statement." {:kind   ::invalid-statement
                                              :errors errs})))
      ;; else
      (throw-unknown-opt fn-type))))

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
  (let [stmt (coerce-statement statement)]
    (letfn [(valid-stmt?
              [stmt]
              (boolean (some (fn [{:keys [predicate-fn]}] (predicate-fn stmt))
                             compiled-profile)))
            (get-valid-ids
              [stmt]
              (reduce (fn [valid-ids {:keys [id predicate-fn]}]
                        (if (predicate-fn stmt)
                          (conj valid-ids id)
                          valid-ids))
                      []
                      compiled-profile))
            (get-errors ; Returns nil if stmt is valid, else the id-error map
              [stmt]
              (not-empty ; no templates => vacuously true
               (reduce (fn [acc {:keys [id validator-fn]}]
                         (if-some [errs (validator-fn stmt)]
                           (assoc acc id errs)
                           (reduced nil)))
                       {}
                       compiled-profile)))]
      (case fn-type
        :predicate
        (valid-stmt? stmt)
        :option
        (when (valid-stmt? stmt) stmt)
        :result
        (get-errors stmt)
        :templates
        (get-valid-ids stmt)
        :assertion
        (when-some [errs (get-errors stmt)]
          (throw (ex-info "Invalid Statement." {:kind   ::invalid-statement
                                                :errors errs})))
        ;; else
        (throw-unknown-opt fn-type)))))

(defn validated-statement?
  "Returns `true` if `statement` is valid against all Templates in
   `compiled-profiles`, `false` otherwise."
  [compiled-profiles statement]
  (let [stmt (coerce-statement statement)]
    (boolean (some (fn [{:keys [predicate-fn]}] (predicate-fn stmt))
                   compiled-profiles))))

;; c.f. Just/Option (Some/None) types
(defn validate-statement-filter
  "Returns `statement` if it is valid against all Templates in
   `compiled-profiles`, `nil` otherwise."
  [compiled-profiles statement]
  (when (validated-statement? compiled-profiles statement)
    statement))

(defn validate-statement-template-ids
  "Returns a vector of the Template IDs that `statement` is valid
   against."
  [compiled-profiles statement]
  (let [stmt          (coerce-statement statement)
        conj-valid-id (fn [valid-ids {:keys [id predicate-fn]}]
                        (if (predicate-fn stmt)
                          (conj valid-ids id)
                          valid-ids))]
    (reduce conj-valid-id [] compiled-profiles)))

;; c.f. Result (Ok/Error) types
(defn validate-statement-errors
  "Returns a coll of error info maps when validating `statement` against
   the Templates in `compiled-profiles` if all Templates are invalid,
   `nil` otherwise."
  [compiled-profiles statement]
  (let [stmt       (coerce-statement statement)
        conj-error (fn [acc {:keys [id validator-fn]}]
                     (if-some [errs (validator-fn stmt)]
                       (assoc acc id errs)
                       (reduced nil)))]
    (->> compiled-profiles
         (reduce conj-error {})
         ;; no templates => vacuously true
         not-empty)))

(defn validate-statement-assert
  "Throw an ExceptionInfo exception if `statement` is invalid against
   all Templates in `compiled-profiles`, returns `nil` otherwise."
  [compiled-profiles statement]
  (when-some [errs (validate-statement-errors compiled-profiles
                                              statement)]
    (throw (ex-info "Invalid Statement." {:kind   ::invalid-statement
                                          :errors errs}))))

(defn validate-statement-print
  "Print all errors for each Template that `statement` is invalid against."
  [compiled-profiles statement]
  (let [stmt (coerce-statement statement)]
    (dorun (map (fn [{:keys [validation-fn]}]
                  (when-some [errs (validation-fn stmt)]
                    (err-printer/print-errors errs)))
                compiled-profiles))))

(defn validate-statement
  "Takes `compiled-profiles` and `statement` where `compiled-profile`
   is the result of `compile-profiles->validators`, and validates
   `statement` against the Statement Templates in the Profile.

   Takes a `:fn-type` kwarg, which sets the return value and side effects
   of `validate-statement`. Has the following options:

     :predicate  Returns `true` for a valid Statement, else `false`.
                 Default.
     :option     Returns the Statement if it's valid, else `nil`.
     :result     Returns validation error data on the first Template
                 the Statement is invalid against,, else `nil`. The
                 data is a map from Template ID to error data.
     :templates  Returns the IDs of the Statement Templates the
                 Statement is valid for.
     :printer    Prints the error data for all Templates the Statement
                 fails validation against.
     :assertion  Throws an exception upon validation failure (where
                 `(-> e ex-data :errors)` returns the error data),
                 else returns `nil`."
  [compiled-profiles statement & {:keys [fn-type] :or {fn-type :predicate}}]
  (case fn-type
    :predicate
    (validated-statement? compiled-profiles statement)
    :option
    (validate-statement-filter compiled-profiles statement)
    :result
    (validate-statement-errors compiled-profiles statement)
    :templates
    (validate-statement-template-ids compiled-profiles statement)
    :printer
    (validate-statement-print compiled-profiles statement)
    :assertion
    (validate-statement-assert compiled-profiles statement)
    ;; else
    (throw-unknown-opt fn-type)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern Matching Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Work with XML and Turtle Profiles
;; TODO: Add Exception messages when Patterns are rejected

;; TODO: Move specs to top of namespace
(s/def ::validate-profiles? boolean?)
(s/def ::compile-nfa? boolean?)
(s/def ::selected-profiles (s/every ::pan-profile/id))
(s/def ::selected-patterns (s/every ::pan-pattern/id))

;; Profile -> FSM Compilation

(def compiled-profiles-spec
  (s/every-kv ::pan-profile/id
              (s/every-kv ::pan-pattern/id p/fsm-map-spec)))

(s/fdef compile-profiles->fsms
  :args (s/cat :profiles ::profiles
               :kw-args  (s/keys* :opt-un [::statement-ref-fns
                                           ::validate-profiles?
                                           ::compile-nfa?
                                           ::selected-profiles
                                           ::selected-patterns]))
  :ret compiled-profiles-spec)

(defn compile-profiles->fsms
  "Take `profiles`, a collection of JSON-LD profiles (or equivalent EDN
   data) and returns a map of the following form:
     
     { profile-version { pattern-id {:id ... :dfa ... :nfa ...} } }
  Where `profile-version` is the latest profile version ID in the Profile
  and `pattern-id` is the ID of a primary Pattern in that Profile. The
  leaf values include the following: 
     
     `:id`       The pattern ID.
     `:dfa`      The DFA used for general pattern matching.
     `:nfa`      The NFA with pattern metadata used for reconstructing the
                 pattern path. This is an optional property that is only
                 produced when `:compile-nfa?` is `true`.
     `:nfa-meta` The NFA metadata; assoc-ed here in case meta is lost
                 from the `:nfa` value. Only present if `:nfa` is.
   The following are optional arguments:

     :statement-ref-fns  takes the key-value pairs described in
     `template->validator`.
     :validate-profiles? if true checks that all profiles conform to the
     xAPI Profile spec and throws an exception if not. Default false.
     :compile-nfa?       if true compiles an additional NFA that is used
     for composing detailed error traces. Default false.
     :selected-profiles  if present filters out any profiles whose IDs
     are not in the coll. (Note that these should be profile IDs, not
     profile version IDs.)
     :selected-patterns  if present filters out any primary patterns
     whose IDs are not in the coll."
  [profiles & {:keys [statement-ref-fns
                      validate-profiles?
                      compile-nfa?
                      selected-profiles
                      selected-patterns]
               :or   {validate-profiles?     true
                      compile-nfa?           false}}]
  (let [profiles (map coerce-profile profiles)
        opt-map  {:statement-ref-fns statement-ref-fns
                  :compile-nfa?      compile-nfa?
                  :selected-patterns selected-patterns}]
    (when validate-profiles?
      (dorun (map assert-profile profiles))
      (assert-profile-ids profiles)
      (assert-pattern-ids profiles))
    (let [?prof-id-set (when selected-profiles (set selected-profiles))
          profiles     (cond->> profiles
                         ?prof-id-set
                         (filter (fn [{:keys [id]}] (?prof-id-set id))))
          prof-id-seq  (map (fn [prof] (->> prof prof/latest-version :id))
                            profiles)
          pat-fsm-seq  (map (fn [prof] (p/profile->fsms prof opt-map))
                            profiles)]
      (into {} (map (fn [prof-id pf-map] [prof-id pf-map])
                    prof-id-seq
                    pat-fsm-seq)))))

;; Registration Key Construction

(def registration-key-spec
  (s/or :no-subregistration ::stmt/registration
        :subregistration (s/tuple ::stmt/registration-id
                                  ::stmt/subregistration)))

(defn- construct-registration-key
  [profile-id registration ?subreg-ext]
  (if-some [subregistration (and ?subreg-ext
                                 (stmt/get-subregistration-id profile-id
                                                              ?subreg-ext))]
    [registration subregistration]
    registration))

;; Pattern Matching

(s/def ::states-map
  (s/every-kv registration-key-spec
              (s/every-kv ::pan-pattern/id
                          p/state-info-spec)))

(s/def ::accepts
  (s/every (s/tuple registration-key-spec ::pan-pattern/id)))

(s/def ::rejects
  (s/every (s/tuple registration-key-spec ::pan-pattern/id)))

(def state-info-map-spec
  (s/or :start (s/nilable #{{}})
        :continue (s/keys :req-un [::accepts
                                   ::rejects
                                   ::states-map])))

;; TODO: Move to top of namespace
(s/def ::error #{::stmt/missing-profile-reference
                 ::stmt/invalid-subreg-no-registration
                 ::stmt/invalid-subreg-nonconformant})

(def match-stmt-error-spec
  (s/keys :req-un [::error ::xs/statement]))

;; TODO: Move to top of ns
(def statement-spec
  (s/or :json (s/and (s/conformer coerce-statement)
                     ::xs/statement)
        :edn ::xs/statement))

(defn- match-statement-vs-pattern
  "Match `statement` against the pattern DFA, and upon failure (i.e.
   `fsm/read-next` returns `#{}`), append printable failure metadata
   to the return value."
  [{pat-id    :id
    pat-dfa   :dfa
    ?pat-nfa  :nfa
    ?nfa-meta :nfa-meta}
   state-info
   statement
   print?]
  (let [start-opts  {:record-visits? (some? ?pat-nfa)}
        new-st-info (fsm/read-next pat-dfa start-opts state-info statement)]
    (if (empty? new-st-info)
      (if-some [old-meta (meta state-info)]
        (do
          (when print?
            (println (perr-printer/error-msg-str (:failure old-meta))))
          (with-meta new-st-info old-meta))
        (let [?fail-trc (when ?pat-nfa
                          (let [read-temp-ids
                                (if ?nfa-meta
                                  (partial p/read-visited-templates
                                           ?pat-nfa
                                           ?nfa-meta)
                                  (partial p/read-visited-templates
                                           ?pat-nfa))
                                build-trace
                                (fn [temp-ids]
                                  {:templates temp-ids
                                   :patterns  (read-temp-ids temp-ids)})]
                            (->> state-info
                                 (map :visited)
                                 (map build-trace))))
              fail-meta (cond-> {:statement (get statement "id")
                                 :pattern   pat-id}
                          ?fail-trc
                          (assoc :traces ?fail-trc))]
          (when print?
            (println (perr-printer/error-msg-str fail-meta)))
          (with-meta new-st-info {:failure fail-meta})))
      new-st-info)))

(s/fdef match-statement
  :args (s/cat :compiled-profiles compiled-profiles-spec
               :state-info-map    state-info-map-spec
               :statement         (s/or :error match-stmt-error-spec
                                        :ok statement-spec))
  :ret (s/or :error match-stmt-error-spec
             :ok state-info-map-spec))

(defn match-statement
  "Takes `compiled-profiles`, `state-info-map`, and `statement`, where
   `compiled-profiles` is a return value of `compile-profiles->fsms`.
   Matches `statement` to against `compiled-profiles` and returns an
   updated value of `state-info-map`.

   `state-info-map` is a map of the form:
   
     {:accepts    [[registration-key pattern-id] ...]
      :rejects    [[registration-key pattern-id] ...]
      :states-map {registration-key {pattern-id state-info}}}
   where

     :accepts    is a coll of identifiers for accepted state infos
                 (where `:accepted?` is `true`).
     :rejects    is a coll of identifiers for rejected state infos
                 (where they are empty sets).
     :states-map is a doubly-nested map that associates registration
                 keys and pattern IDs to state info maps.
   
   `registration-key` can either be a registration UUID or a
   pair of registration and subregistration UUIDs. Statements without
   registrations will be assigned a default `:no-registration` key.
   
   `state-info` is a map of the following:

     :state     The current state in the FSM, i.e. where is the
                current location in the Pattern?
     :accepted? If that state is an accept state, i.e. did the inputs
                fully match the Pattern?
     :visited   The vec of visited Statement Templates; this is only
                present if `compiled-profiles` was compiled with
                `compile-nfa?` set to `true`.

   On error, returns the map
     
     {:error {:type error-kw :statement {...}}}
   where `error-kw` is one of the following:
     
     ::missing-profile-reference      if `statement` does not have a
                                      Profile ID from `compiled-profiles`
                                      as a category context activity.
     ::invalid-subreg-no-registration if a sub-registration is present
                                      without a registration.
     ::invalid-subreg-nonconformant   if the sub-registration extension
                                      value is invalid.
     
     `match-statement` takes in an optional `:print?` kwarg; if true,
     then prints any error or match failure."
  [compiled-profiles state-info-map statement & {:keys [print?]
                                                 :or   {print? false}}]
  (if (:error state-info-map) ; TODO: Should errors also be printed?
    state-info-map
    (let [statement      (coerce-statement statement)
          reg-pat-st-m   (:states-map state-info-map)
          prof-id-set    (set (keys compiled-profiles))
          stmt-prof-ids  (stmt/get-statement-profile-ids statement prof-id-set)
          stmt-reg       (stmt/get-statement-registration statement)
          ?stmt-subreg   (stmt/get-statement-subregistration statement stmt-reg)]
      (if-let [err-kw (or (when (keyword? stmt-prof-ids) stmt-prof-ids)
                          (when (keyword? ?stmt-subreg) ?stmt-subreg))]
        {:error {:type      err-kw
                 :statement statement}}
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
               :state-info-map    state-info-map-spec
               :statement-batch   (s/coll-of (s/or :error match-stmt-error-spec
                                                   :ok statement-spec)))
  :ret (s/or :error match-stmt-error-spec
             :ok state-info-map-spec))

(defn match-statement-batch
  "Similar to `match-statement`, except takes a batch of statements and
   sorts them by timestamp before matching. Short-circuits if an error
   (e.g. missing Profile ID reference) is detected."
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
        st-info-map))))
