(ns com.yetanalytics.persephone.template-validation
  (:require [clojure.set :as cset]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            #_[com.yetanalytics.pan.objects.template :as template]
            [com.yetanalytics.persephone.utils.json :as json]
            [com.yetanalytics.persephone.utils.errors :as emsg]))

;; TODO StatementRefTemplate predicates

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cond-on-val
  "Ensure that predicate is only considered when the value is not nil.
  Otherwise ignore the predicate by always returning true."
  [pred-fn v]
  (if (some? v)
    pred-fn
    (constantly true)))

(defn cond-partial
  "Compose Clojure's partial function with cond-on-val.
  The predicate is only considered when its first argument isn't nil."
  [pred-fn v]
  (cond-on-val (partial pred-fn v) v))

;; Currently unused; may be useful sometime in the future.
(defn value-map
  "Given an array of keys (each corresponding to a level of map nesting),
  return corresponding values from a vector of maps."
  [map-vec & ks]
  (mapv #(get-in % ks) map-vec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic predicates 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  ;; example usage of not-empty
  (nil? (not-empty []))
  (= ["foo"] (not-empty ["foo"])))

(defn all-matchable?
  "Returns true if every value is matchable, ie. not nil; false otherwise."
  [coll]
  (and (not-empty coll)
       (every? some? coll)))

(defn none-matchable?
  "Returns true if there are no matchable values, ie. every value is nil;
  false otherwise."
  [coll]
  (or (empty? coll)
      (every? nil? coll)))

(defn any-matchable?
  "Returns true if there exist some value that is matchable; false otherwise."
  [coll]
  (not (none-matchable? coll)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicates.
;; A Statement MUST follow all the rules in the Statement Template.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn presence-predicate-fn
  "pulls out the functionality common to the -values? functions

  `pred-fn` should be a fn of 2 arity
  - first arg = `presence` ie. any | all | none
  - second arg = `values`"
  [{:keys [presence values pred-fn]}]
  (pred-fn (set presence) (set values)))

;; If 'any' is provided, evaluated values MUST include at least one value
;; that is given by 'any'. In other words, the set of 'any' and the evaluated
;; values MUST interesect.
(defn any-values?
  "Return true if there's at least one value in 'any'; false otherwise."
  [any values]
  (presence-predicate-fn
   {:presence any
    :values values
    ;; must return bool
    :pred-fn (fn [p v] (-> v (cset/intersection p) not-empty boolean))}))

;; If 'all' is provided, evaluated values MUST only include values in 'all'
;; and MUST NOT include any unmatchable values. In other words, the set of
;; 'all' MUST be a superset of the evaluated values.
(defn all-values?
  "Return true is every value is in 'all'; false otherwise."
  [all values]
  (if-not (all-matchable? values)
    false
    (presence-predicate-fn
     {:presence all
      :values values
      :pred-fn (fn [p v] (cset/subset? v p))})))

;; If 'none' is provided, evaluated values MUST NOT include any values in
;; 'none'. In other words, the set of 'none' and the evaluated values MUST NOT
;; intersect.
(defn none-values?
  "Return true if there are no values in 'none'; false otherwise."
  [none values]
  (presence-predicate-fn
   {:presence none
    :values values
    :pred-fn (fn [p v] (empty? (cset/intersection v p)))}))

;; MUST apply the 'any', 'all' and 'none' requirements if presence is missing,
;; 'included' or 'recommended' (ie. not 'excluded') and any matchable values
;; are in the statement.

;; If presence is 'included', location and selector MUST return at least one
;; value (ie. MUST include at least one matchable value and MUST NOT include
;; any unmatchable values). 
(defn create-included-spec
  "Returns a clojure spec for when presence is 'included.'"
  [{:keys [any all none]}]
  (let [rule-any? (cond-partial any-values? any)
        rule-all? (cond-partial all-values? all)
        rule-none? (cond-partial none-values? none)]
    (s/and all-matchable?
           rule-any?
           rule-all?
           rule-none?)))

;; If presence is 'excluded', location and selector MUST NOT return any values
;; (ie. MUST NOT include any matchable values). 
(defn create-excluded-spec
  "Returns a clojure spec for when presence is 'excluded.'"
  [_]
  ;; the creation of a spec directly from a function is done using `s/spec`
  (s/spec none-matchable?))

;; If presence is 'recommended' or is missing, the evaluated values MUST 
;; conform to the 'any', 'all' and 'none' specs (but there are no additional
;; restrictions).
;; 'recommended' allows profile authors to not have any/all/none in a rule.
(defn create-default-spec
  "Returns a predicate for when presence is 'recommended' or is missing."
  [{:keys [any all none]}]
  (let [rule-any? (cond-partial any-values? any)
        rule-all? (cond-partial all-values? all)
        rule-none? (cond-partial none-values? none)]
    (s/or :missing none-matchable?
          :not-missing (s/and any-matchable?
                              rule-any?
                              rule-all?
                              rule-none?))))

;; Spec to check that the 'presence' keyword is correct.
;; A Statement Template MUST include one or more of presence, any, all or none.
(s/def ::rule
  (s/or :missing
        (fn [r] (and (not (contains? r :presence))
                     (or (contains? r :any)
                         (contains? r :all)
                         (contains? r :none))))
        :not-missing
        (fn [r] (#{"included" "excluded" "recommended"} (:presence r)))))

(defn create-rule-spec
  "Given a rule, create a spec that will validate a Statement against it."
  [{:keys [presence] :as rule}]
  (if (s/valid? ::rule rule)
    (case presence
      "included" (create-included-spec rule)
      "excluded" (create-excluded-spec rule)
      "recommended" (create-default-spec rule)
      nil (create-default-spec rule))
    (throw (ex-info "Invalid Rule" (s/explain-data ::rule rule)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn evaluate-paths
  "Given a vector of JSONPath strings and a statement, return a flattened
  vector of evaluated values."
  [statement json-paths]
  (letfn [(collify [maybe-coll]
            ;; ensure proper processing within `json/read-json`
            ;; - no other enformcent that `json-paths` is a coll
            (if (coll? maybe-coll) maybe-coll [maybe-coll]))
          (get-by-loc [loc]
            ;; query `statement` based on `loc`
            (json/read-json statement loc))
          (mapcatv [f & colls]
            ;; semi silly refactor
            ;; - more concise/effecient than previous version
            ;; - TODO: worth porting to json?
            (->> colls (apply mapcat f) vec))]
    (->> json-paths
         collify
         (mapcatv get-by-loc))))

;; in clojure, the special form `recur` handles resource management under the hood
;; - in the previous form of `find-values`, recursion without recur was used
;; -- wasn't actually dangerous but this pattern should be avoided when possible

(defn find-values
  "Using the 'location' and 'selector' JSONPath strings, return the evaluated 
  values from a Statement as a vector."
  [statement location & [selector]]
  (let [locations (json/split-json-path location)
        values (evaluate-paths statement locations)]
    (if-not (some? selector)
      values
      ;; there's a selector
      (letfn [(format-selector [sel]
                ;; format for use within `evaluate-paths`
                (-> sel (string/replace #"(\$)" "$1[*]") json/split-json-path))]
        (->> selector
             format-selector
             ;; dive one level deeper into original location query results
             ;; - navigate into the results given selector location string
             (evaluate-paths values))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Determining Properties
;; A Statement Template MUST include all Determining Properties.
;; All Determining Properties are simply special instances of rules, and can
;; be rewritten as such.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-det-properties
  "Convert Determining Properties into rules and add them to the Template's
  rules. Returns the modified rules vector."
  [{:keys [verb
           objectActivityType
           contextGroupingActivityType
           contextParentActivityType
           contextOtherActivityType
           contextCategoryActivityType
           attachmentUsageType
           rules]}]
  (letfn [(build-det-props [accum]
            (cond-> accum
              verb
              (conj {:location "$.verb.id"
                     :presence "included"
                     :all [verb]
                     :determiningProperty "Verb"})
              objectActivityType
              (conj {:location "$.object.definition.type"
                     :presence "included"
                     :all [objectActivityType]
                     :determiningProperty "objectActivityType"})
              contextParentActivityType
              (conj {:location
                     "$.context.contextActivities.parent[*].definition.type"
                     :presence "included"
                     :all contextParentActivityType
                     :determiningProperty "contextParentActivityType"})
              contextGroupingActivityType
              (conj {:location
                     "$.context.contextActivities.grouping[*].definition.type"
                     :presence "included"
                     :all contextGroupingActivityType
                     :determiningProperty "contextGroupingActivityType"})
              contextCategoryActivityType
              (conj {:location
                     "$.context.contextActivities.category[*].definition.type"
                     :presence "included"
                     :all contextCategoryActivityType
                     :determiningProperty "contextCategoryActivityType"})
              contextOtherActivityType
              (conj {:location
                     "$.context.contextActivities.other[*].definition.type"
                     :presence "included"
                     :all contextOtherActivityType
                     :determiningProperty "contextOtherActivityType"})
              attachmentUsageType
              (conj {:location
                     "$.attachments[*].usageType"
                     :presence "included"
                     :all attachmentUsageType
                     :determiningProperty "attachmentUsageType"})))]
    ;; refactor makes the process more clear, via seperation of concerns
    ;; - build rule config from template definition
    ;; - aggregate all rules together into a single coll
    (-> [] build-det-props (into rules))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bringing it all together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-rule-validator
  "Given a rule, create a function that will validate new Statements against
  the rule."
  [{:keys [location selector] :as rule}]
  (let [rule-spec (create-rule-spec rule)]
    (fn [statement]
      (let [values (find-values statement location selector)
            error-data (s/explain-data rule-spec values)]
        ;; nil indicates success
        ;; spec error data the opposite
        (if-not (nil? error-data)
          {:error (-> error-data ::s/problems first)
           :values values
           :rule rule}
          nil)))))

(defn create-rule-validators
  "Given a Statement Template, return a vector of validators representing its
  rules, including its Determining Properties."
  [template]
  (let [new-rules (add-det-properties template)]
    (mapv create-rule-validator new-rules)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validate statement 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO Currently unused; need to add this back in to validate Templates
;; (defn template-valid
;;   "Given a Statement Template, throw an exception if it is syntactically
;;  invalid. If it's valid, return it instead (similar to spec/conform)."
;;  [template]
;;  (let [errors (s/explain-data ::template/template template)]
;;    (if (some? errors)
;;      (throw (ex-info "Template Validation Exception" errors))
;;      template)))

(defn validate-statement*
  "Given a Statement and a Statement Template, validate the Statement.
 Returns nil if the Statement is valid, the spec error map otherwise"
  [template statement]
  (let [new-rules (add-det-properties template)
        validators (mapv create-rule-validator new-rules)
        error-vec (map #(% statement) validators)]
    (if-not (none-matchable? error-vec)
      error-vec
      nil)))

(defn validate-statement
  "Given a Statement and a Statement Template, validate the Statement.
  Returns true if Statement is valid, false otherwise.
  Set err-msg argument to true to print errors.
  Note: Assumes syntactically valid Template and Statement already."
  [template statement & {:keys [err-msg] :or {err-msg false}}]
  ; Statement Templates have keyword keys, but Statements have string keys
  (let [template-id (:id template)
        statement-id (get statement "id")
        error-vec (validate-statement* template statement)]
    (if-not (nil? error-vec)
      (if (true? err-msg)
        (do (emsg/print-error (filter some? error-vec) template-id statement-id)
            false)
        false)
      true)))
