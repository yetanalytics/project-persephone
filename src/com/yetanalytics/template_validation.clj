(ns com.yetanalytics.template-validation
  (:require [clojure.set :as cset]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [com.yetanalytics.util :as util]))

;; TODO StatementRefTemplate predicates

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all-matchable?
  "Returns true if every value is matchable, ie. not nil; false otherwise."
  [coll]
  (and (not (empty? coll))
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

;; If 'any' is provided, evaluated values MUST include at least one value
;; that is given by 'any'. In other words, the set of 'any' and the evaluated
;; values MUST interesect.
(defn any-values
  "Return true if there's at least one value in 'any'; false otherwise."
  [any values]
  (let [any-set (set any)
        values-set (set values)]
    (not (empty? (cset/intersection values-set any-set)))))

;; If 'all' is provided, evaluated values MUST only include values in 'all'
;; and MUST NOT include any unmatchable values. In other words, the set of
;; 'all' MUST be a superset of the evaluated values.
(defn all-values
  "Return true is every value is in 'all'; false otherwise."
  [all values]
  (let [all-set (set all)
        values-set (set values)]
    (and (all-matchable? values)
         (cset/subset? values-set all-set))))

;; If 'none' is provided, evaluated values MUST NOT include any values in
;; 'none'. In other words, the set of 'none' and the evaluated values MUST NOT
;; intersect.
(defn none-values
  "Return true if there are no values in 'none'; false otherwise."
  [none values]
  (let [none-set (set none)
        values-set (set values)]
    (empty? (cset/intersection values-set none-set))))

;; MUST apply the 'any', 'all' and 'none' requirements if presence is missing,
;; 'included' or 'recommended' (ie. not 'excluded') and any matchable values
;; are in the statement.

;; If presence is 'included', location and selector MUST return at least one
;; value (ie. MUST include at least one matchable value and MUST NOT include
;; any unmatchable values). 
(defn create-included-pred
  "Returns a predicate for when presence is 'included.'"
  [rule]
  (let [rule-any? (util/cond-partial any-values rule)
        rule-all? (util/cond-partial all-values rule)
        rule-none? (util/cond-partial none-values rule)]
    (s/and all-matchable?
           rule-any?
           rule-all?
           rule-none?)))

;; If presence is 'excluded', location and selector MUST NOT return any values
;; (ie. MUST NOT include any matchable values). 
(defn create-excluded-pred
  "Returns a predicate for when presence is 'excluded.'"
  [_]
  (s/and none-matchable?))

;; If presence is 'recommended' or is missing, the evaluated values MUST 
;; conform to the 'any', 'all' and 'none' specs (but there are no additional
;; restrictions).
;; 'recommended' allows profile authors to not have any/all/none in a rule.
(defn create-default-pred
  "Returns a predicate for when presence is 'recommended' or is missing."
  [rule]
  (let [rule-any? (util/cond-partial any-values rule)
        rule-all? (util/cond-partial all-values rule)
        rule-none? (util/cond-partial none-values rule)]
    (s/or :empty none-matchable?
          :not-empty (s/and any-matchable?
                            rule-any?
                            rule-all?
                            rule-none?))))

;; Spec to check that the 'presence' keyword is correct.
;; A Statement Template MUST include one or more of presence, any, all or none.
(s/def ::presence
  (s/or :missing
        (fn [r] (and (not (contains? r :presence))
                     (or (contains? r :any)
                         (contains? r :all)
                         (contains? r :none))))
        :not-missing
        (fn [r] (#{"included" "excluded" "recommended"} (:presence r)))))

;; Helper function that throws an exception if rule is invalid.
(fn check-rule
  [rule]
  (s/check-asserts true)
  (s/assert ::rule rule)
  (s/check-asserts false))

(defn create-rule-spec
  "Given a rule, create a spec that will validate a Statement against it."
  [{:keys [presence] :as rule}]
  (check-rule rule)
  (case presence
    "included" (create-included-pred rule)
    "excluded" (create-excluded-pred rule)
    "recommended" (create-default-pred rule)
    nil (create-default-pred rule)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn evaluate-paths
  "Given a vector of JSONPath strings and a statement, return a flattened
  vector of evaluated values."
  [statement json-paths]
  (vec (mapcat identity
               (mapv (fn [loc] (util/read-json statement loc)) json-paths))))

(defn find-values
  "Using the 'location' and 'selector' JSONPath strings, return the evaluated 
  values from a Statement as a vector."
  [statement location & selector]
  (let [locations (util/split-json-path location)
        values (evaluate-paths statement locations)]
    ;; If there's a selector, re-evaulate the previously evaluated values
    (if (some? selector)
      (find-values values
                   (-> selector first (string/replace #"(\$)" "$1[*]")))
      values)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Determining Properties
;; A Statement Template MUST include all Determining Properties.
;; All Determining Properties are simply special instances of rules, and can
;; be rewritten as such.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-rules-spec
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
  (cond-> rules
    verb
    (conj {:location "$.id.verb"
           :presence "included"
           :all [verb]
           :determiningProperty "Verb"})
    objectActivityType
    (conj {:location "$.object.definition.type"
           :presence "included"
           :all "objectActivityType"
           :determiningProperty "objectActivityType"})
    contextParentActivityType
    (conj {:location "$.context.contextActivities.parent[*].definition.type"
           :presence "included"
           :all contextParentActivityType
           :determiningProperty "contextParentActivityType"})
    contextGroupingActivityType
    (conj {:location "$.context.contextActivities.grouping[*].definition.type"
           :presence "included"
           :all contextGroupingActivityType
           :determiningProperty "contextGroupingActivityType"})
    contextCategoryActivityType
    (conj {:location "$.context.contextActivities.categoryk[*].definition.type"
           :presence "included"
           :all contextCategoryActivityType
           :determiningProperty "contextCategoryActivityType"})
    contextOtherActivityType
    (conj {:location "$.context.contextActivities.other[*].definition.type"
           :presence "included"
           :all contextOtherActivityType
           :determiningProperty "contextOtherActivityType"})
    attachmentUsageType
    (conj {:location "$.attachments[*].usageType"
           :presence "included"
           :all attachmentUsageType
           :determiningProperty "attachmentUsageType"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bringing it all together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-rule-validator
  "Given a rule, create a function that will validate new Statements against
  the rule."
  [{:keys [location selector] :as rule}]
  (let [rule-spec (create-rule-spec rule)]
    (fn [statement]
      (let [values (find-values statement location selector)]
        (s/explain-data rule-spec values)))))

(defn create-rules-pred
  "Given a Statement Template, return a vector of predicates representing its
  rules, including its Determining Properties."
  [template]
  (let [new-rules (add-det-property-rules template)]
    (mapv create-rule-validator new-rules)))

(defn validate-statement
  "Given a Statement and a array of validation functions (created from a
  Statement Template), validate the Statement.
  Return an array of all the spec errors encountered."
  [validator-arr statement]
  (filterv some? (map #(% statement) validator-arr)))
