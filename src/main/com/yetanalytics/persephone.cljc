(ns com.yetanalytics.persephone
  (:require [clojure.spec.alpha   :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.template           :as pan-template]
            [com.yetanalytics.persephone.template-validation :as t]
            [com.yetanalytics.persephone.pattern-validation  :as p]
            [com.yetanalytics.persephone.utils.fsm    :as fsm]
            [com.yetanalytics.persephone.utils.json   :as json]
            [com.yetanalytics.persephone.utils.time   :as time]
            [com.yetanalytics.persephone.utils.errors :as err-printer]))

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

(defn- assert-dfas
  [profile-fsm-m]
  (when-not (every? #(= :dfa (:type %)) (vals profile-fsm-m))
    (throw (ex-info "Compiled ID-pattern map is invalid!"
                    {:kind    ::invalid-dfas
                     :pattern profile-fsm-m}))))

(defn- assert-prof-ref
  [profile-id statement]
  (let [cat-acts (get-in statement ["context" "contextActivities" "category"])
        cat-ids  (map #(get % "id") cat-acts)]
    (when-not (some #(= profile-id %) cat-ids)
      (throw (ex-info "Profile not referenced in category context activities!"
                      {:kind         ::missing-profile-reference
                       :profile      profile-id
                       :statement    statement})))))

(defn- assert-subregs
  [profile-id statement registration subreg-ext]
  (when (some? subreg-ext)
    (cond
      (= :no-registration registration)
      (throw (ex-info "Subregistrations present without registration!"
                      {:kind      ::invalid-subreg-no-registration
                       :profile   profile-id
                       :statement statement}))
      (empty? subreg-ext)
      (throw (ex-info "Subregistration extension is an empty array!"
                      {:kind      ::invalid-subreg-nonconformant
                       :profile   profile-id
                       :statement statement
                       :extension subreg-ext}))
      (not (every? #(and (contains? % "profile")
                         (contains? % "subregistration"))
                   subreg-ext))
      (throw (ex-info "Subregistration object is missing keys!"
                      {:kind      ::invalid-subreg-nonconformant
                       :profile   profile-id
                       :statement statement
                       :extension subreg-ext}))
      :else
      nil)))

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
  (let [profile (if (string? profile)
                  (json/json->edn profile :keywordize? true)
                  profile)]
    (reduce (fn [acc {:keys [id] :as template}]
              (assoc acc id template))
            {}
            (:templates profile))))

;; Doesn't exactly conform to stmt batch requirements since in theory,
;; a statement ref ID can refer to a FUTURE statement in the batch.
;; However, this shouldn't really matter in practice.
(defn statement-batch->id-statement-map
  "Takes the coll `statement-batch` and returns a map between
   Statement IDs and the Statement themselves. Used for Statement
   Ref resolution, particularly in statement batch matching."
  [statement-batch]
  (reduce (fn [acc statement]
            (assoc acc (get statement "id") statement))
          {}
          statement-batch))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Validation Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  (let [template (if (string? template)
                   (json/json->edn template :keywordize? true)
                   template)]
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
  (let [profile (if (string? profile)
                  (json/json->edn profile :keywordize? true)
                  profile)]
    (reduce
     (fn [acc template]
       (conj acc (template->validator template
                                      :statement-ref-fns statement-ref-fns
                                      :validate-template? false)))
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
  (let [stmt (if (string? statement) (json/json->edn statement) statement)
        {:keys [id validator-fn predicate-fn]} compiled-template]
    (case fn-type
      :predicate
      (predicate-fn stmt)
      :option
      (when (predicate-fn stmt) stmt)
      :result
      (validator-fn stmt)
      :printer
      (when-some [errs (validator-fn stmt)]
        (err-printer/print-error errs id (get stmt "id")))
      :assertion
      (when-some [errs (validator-fn stmt)]
        (throw (ex-info "Invalid Statement." {:type   ::invalid-statement
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
  (let [stmt (if (string? statement) (json/json->edn statement) statement)]
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
          (throw (ex-info "Invalid Statement." {:type   ::invalid-statement
                                                :errors errs})))
        ;; else
        (throw-unknown-opt fn-type)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern Matching Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Work with XML and Turtle Profiles
;; TODO: Add Exception messages when Patterns are rejected

;; O(n) search instead of O(n log n) sorting.
(defn- latest-version
  "Get the most recent version ID of `profile`."
  [{:keys [versions] :as _profile}]
  (reduce (fn [{latest-ts :generatedAtTime :as latest-ver}
               {newest-ts :generatedAtTime :as newest-ver}]
            (if (neg-int? (time/compare-timestamps latest-ts newest-ts))
              newest-ver   ; latest-ts occured before newest-ts
              latest-ver))
          (first versions) ; okay since empty arrays are banned by the spec
          versions))

(defn profile->fsms
  "Take `profile`, a JSON-LD profile (or equivalent EDN data ) and
   returns a map between primary Pattern IDs and their corresponding
   Pattern FSMs. Returns `{}` if there are no primary Patterns in
   `profile`. The ID of `profile` is saved as metadata for both the
   return value and each individual FSM.
   
   :statement-ref-fns takes the key-value pairs described in
   `template->validator`.

   :validate-profile? is default true. If true, `profile->validator`
   checks that `profile` conforms to the xAPI Profile spec."
  [profile & {:keys [statement-ref-fns validate-profile?]
              :or   {validate-profile? true}}]
  (let [profile (if (string? profile)
                  (json/json->edn profile :keywordize? true)
                  profile)]
    (when validate-profile? (assert-profile profile))
    (let [prof-id (-> profile latest-version :id)
          add-pid (fn [x] (vary-meta x assoc :profile-id prof-id))
          fsm-map (p/profile->fsms profile statement-ref-fns)]
      (reduce-kv (fn [m k v] (assoc m k (add-pid v)))
                 (add-pid {})
                 fsm-map))))

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
   If `state-info` is nil, the function starts at the start state. If
   :states is empty, return `state-info`.
   
   Throws an exception if the profile ID is not included in the
   category context activities of `statement`."
  [pat-fsm state-info statement]
  (assert-dfa pat-fsm)
  (let [stmt (if (string? statement) (json/json->edn statement) statement)]
    (assert-prof-ref (-> pat-fsm meta :profile-id) stmt)
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
   such object has that ID, no sub-registration is applied.
   
   Throws an exception if the Profile ID is not included in the
   `statement` category context activites or if the sub-registration
   extension is invalid."
  [pat-fsm-map state-info-map statement]
  (assert-dfas pat-fsm-map)
  (let [stmt
        (if (string? statement) (json/json->edn statement) statement)
        profile-id
        (-> pat-fsm-map meta :profile-id)
        registration
        (get-in stmt ["context" "registration"] :no-registration)
        ?subreg-ext
        (get-in stmt ["context" "extensions" subreg-iri])]
    (assert-prof-ref profile-id stmt)
    (assert-subregs profile-id stmt registration ?subreg-ext)
    (letfn [(subreg-pred
              [subreg-obj]
              (when (= profile-id (get subreg-obj "profile"))
                (get subreg-obj "subregistration")))
            (get-reg-key
              []
              (if-let [sub-reg (some subreg-pred ?subreg-ext)]
                [registration sub-reg]
                registration))
            (update-pat-si
              [reg-state-info pat-id pat-fsm]
              (let [pat-state-info  (get reg-state-info pat-id)
                    pat-state-info' (fsm/read-next pat-fsm
                                                   pat-state-info
                                                   stmt)]
                (assoc reg-state-info pat-id pat-state-info')))
            (update-reg-si
              [reg-state-info]
              (reduce-kv update-pat-si reg-state-info pat-fsm-map))]
      (update state-info-map (get-reg-key) update-reg-si))))

(defn- cmp-statements
  "Compare Statements `s1` and `s2` by their timestamp values."
  [s1 s2]
  (let [t1 (get s1 "timestamp")
        t2 (get s2 "timestamp")]
    (time/compare-timestamps t1 t2)))

(defn match-statement-batch-vs-pattern
  "Like `match-statement-vs-pattern`, but takes a collection of
   Statements instead of a singleton Statement. Automatically
   orders Statements by timestamp value, which should be present."
  [pat-fsm state-info statement-coll]
  (loop [stmt-coll (sort cmp-statements statement-coll)
         st-info   state-info]
    (if-let [stmt (first stmt-coll)]
      (recur (rest stmt-coll)
             (match-statement-vs-pattern pat-fsm st-info stmt))
      st-info)))

(defn match-statement-batch-vs-profile
  "Like `match-statement-vs-profile`, but takes a collection of
   Statements instead of a singleton Statement. Automatically
   orders Statements by timestamp value, which should be present."
  [pat-fsm-map state-info-map statement-coll]
  (loop [stmt-coll (sort cmp-statements statement-coll)
         si-map    state-info-map]
    (if-let [stmt (first stmt-coll)]
      (recur (rest stmt-coll)
             (match-statement-vs-profile pat-fsm-map si-map stmt))
      si-map)))
