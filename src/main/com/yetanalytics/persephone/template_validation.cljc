(ns com.yetanalytics.persephone.template-validation
  (:require [clojure.set :as cset]
            [clojure.string :as string]
            [com.yetanalytics.pathetic :as json-path])
  #?(:cljs (:require-macros
            [com.yetanalytics.persephone.template-validation
             :refer [wrap-pred and-wrapped or-wrapped add-wrapped]])))

;; TODO StatementRefTemplate predicates

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Predicate macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; We use these macros in order to identify the name of the predicate that
;; returns false for a some series of nested predicates.

#?(:clj
   (defmacro wrap-pred
     "Wrap a predicate function f such that if f returns true,
      return nil, and if f returns false, return the keywordized
      name of f."
     [f]
     (assert (symbol? f))
     (let [pred-name# (keyword f)]
       `(fn [x#] (if (~f x#) nil ~pred-name#)))))

#?(:clj
   (defmacro and-wrapped
     "Given two functions wrapped using wrap-pred, return nil
      if both functions return true and the corresponding
      keywordized fn name for the function that returns false.
      Short circuiting."
     [f1 f2]
     `(fn [x#]
        (if-let [res1# (~f1 x#)]
          res1#
          (when-let [res2# (~f2 x#)]
            res2#)))))

#?(:clj
   (defmacro or-wrapped
     "Given two functions wrapped using wrap-pred, return nil
      if either function returns true and the keywordized fn
      name for the first function if both return false."
     [f1 f2]
     `(fn [x#]
        (when-let [res1# (~f1 x#)]
          (when-let [res2# (~f2 x#)]
            res1#)))))

#?(:clj
   (defmacro add-wrapped
     "Given a function wrapped using wrap-pred or add-wrapped,
      add a 2-ary predicate f only if the values are not nil."
     [wrapped-fns f values]
     `(if (some? ~values)
        ~(let [pred-name# (keyword f)]
           `(fn [x#]
              (if-let [res# (~wrapped-fns x#)]
                res#
                (if (~f ~values x#) nil ~pred-name#))))
        ~wrapped-fns)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic predicates 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; A value is considered matchable if it's not nil (we presume that the JSONPath
;; string will return matchable values as non-nil values). In other words, nil
;; means unmatchable value.

(defn all-matchable?
  "Returns true iff every value in `coll` is matchable."
  [coll]
  (or (empty? coll)
      (every? some? coll)))

(defn none-matchable?
  "Returns true iff no value in `coll` is matchable."
  [coll]
  (->> coll (filterv some?) empty?))

(defn any-matchable?
  "Returns true iff at least one matchable value in `coll` exists."
  [coll]
  (->> coll (filterv some?) not-empty boolean))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicates.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn presence-pred-fn
  "pulls out the functionality common to the -values? functions

  `pred-fn` should be a fn of 2 arity
  - first arg = `presence` ie. any | all | none
  - second arg = `values`"
  [presence-coll values-coll pred-fn]
  (pred-fn (set presence-coll) (set values-coll)))

;; If 'any' is provided, evaluated values MUST include at least one value
;; that is given by 'any'. In other words, the set of 'any' and the evaluated
;; values MUST interesect.
(defn some-any-values?
  "Return true if there's at least one value in 'any'; false otherwise."
  [any-coll values-coll]
  (presence-pred-fn any-coll
                    (filter some? values-coll)
                    (fn [p v] (-> (cset/intersection v p) not-empty boolean))))

;; If 'all' is provided, evaluated values MUST only include values in 'all'.
;; In other words, the set of 'all' MUST be a superset of the evaluated values.
(defn only-all-values?
  "Return true is every value is in 'all'; false otherwise."
  [all-coll values-coll]
  (presence-pred-fn all-coll
                    (filter some? values-coll)
                    (fn [p v] (cset/subset? v p))))

;; If 'none' is provided, evaluated values MUST NOT include any values in
;; 'none'. In other words, the set of 'none' and the evaluated values MUST NOT
;; intersect.
(defn no-none-values?
  "Return true if there are no values in 'none'; false otherwise."
  [none-coll values-coll]
  (presence-pred-fn none-coll
                    (filter some? values-coll)
                    (fn [p v] (-> (cset/intersection v p) empty?))))

;; If 'all' is provided, evaluated values MUST NOT include any unmatchable
;; values.
(defn no-unmatch-vals?
  "Return true if no unmatchable values exist; false otherwise."
  [presence-coll values-coll]
  (presence-pred-fn presence-coll
                    values-coll ;; don't ignore unmatchable values
                    (fn [_ v] (all-matchable? v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rule spec creation.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; MUST apply the 'any', 'all' and 'none' requirements if presence is missing,
;; 'included' or 'recommended' (ie. not 'excluded') and any matchable values
;; are in the statement.
;;
;; If presence is 'included', location and selector MUST include at least one
;; matchable value and MUST NOT include any unmatchable values. 
(defn create-included-pred
  "Returns a wrapped pred for when presence is 'included.'"
  [{:keys [any all none]}]
  (-> (and-wrapped (wrap-pred any-matchable?) (wrap-pred all-matchable?))
      (add-wrapped some-any-values? any)
      (add-wrapped only-all-values? all) ;; no-unmatch-vals? is redundant
      (add-wrapped no-none-values? none)))

;; If presence is 'excluded', location and selector MUST NOT return any values
;; (ie. MUST NOT include any matchable values). 
(defn create-excluded-pred
  "Returns a wrapped pred for when presence is 'excluded.'"
  [_]
  ;; the creation of a spec directly from a function is done using `s/spec`
  (wrap-pred none-matchable?))

;; If presence is 'recommended' or is missing, the evaluated values MUST 
;; conform to the 'any', 'all' and 'none' specs (but there are no additional
;; restrictions, i.e. it is possible not to have any matchable values).
;; 'recommended' allows profile authors to not have any/all/none in a rule.
(defn create-default-pred
  "Returns a wrapped pred for when presence is 'recommended' or is missing."
  [{:keys [any all none]}]
  (or-wrapped (-> (wrap-pred any-matchable?)
                  (add-wrapped some-any-values? any)
                  (add-wrapped only-all-values? all)
                  (add-wrapped no-unmatch-vals? all)
                  (add-wrapped no-none-values? none))
              (wrap-pred none-matchable?)))

;; Spec to check that the 'presence' keyword is correct.
;; A Statement Template MUST include one or more of presence, any, all or none.
;; NOTE: Should never happen with a validated Statement Template or Profile
(defn- assert-valid-rule
  [rule]
  (when-not (or (and (not (contains? rule :presence))
                     (or (contains? rule :any)
                         (contains? rule :all)
                         (contains? rule :none)))
                (#{"included" "excluded" "recommended"} (:presence rule)))
    (throw (ex-info "Invalid rule."
                    {:type ::invalid-rule-syntax
                     :rule rule}))))

(defn create-rule-pred
  "Given a rule, create a predicate that will validate a Statement
   against it. Returns the name of the atomic predicate that returns
   false, or nil if all return true."
  [{:keys [presence] :as rule}]
  (assert-valid-rule rule)
  (case presence
    "included"    (create-included-pred rule)
    "excluded"    (create-excluded-pred rule)
    "recommended" (create-default-pred rule)
    nil           (create-default-pred rule)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-values
  "Given a Statement, a location JSONPath string, and an optional selector
   JSONPath string, return a vector of the selected values. Unmatchable
   values are returned as nils."
  [stmt loc-path & [select-path]]
  (let [locations (json-path/get-values* stmt loc-path :return-missing? true)]
    (if-not select-path
      ;; No selector - return locations
      locations
      (json-path/get-values* locations select-path :return-missing? true))))

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
;; A Statement MUST follow all the rules in the Statement Template.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-rule-validator
  "Given a rule, create a function that will validate new Statements against
  the rule."
  [{:keys [location selector] :as rule}]
  (let [rule-spec     (create-rule-pred rule)
        location-path (json-path/parse-path location)
        ;; Locations values will be a JSON array, so we can query it using the
        ;; selector by adding a wildcard at the beginning.
        selector-path (when selector
                        (-> selector
                            (string/replace #"(\$)" "$1[*]")
                            json-path/parse-path))]
    (fn [statement]
      (let [values (find-values statement location-path selector-path)
            failed-pred (rule-spec values)]
        ;; nil indicates success
        ;; spec error data the opposite
        (if-not (nil? failed-pred)
          ;; :pred - the predicate that failed, causing this error
          ;; :values - the values that the predicate failed on
          ;; :rule - the Statement Template rule associated with the error
          {:pred   failed-pred
           :values values
           :rule   rule}
          nil)))))

(defn create-template-validator
  "Given a Statement Template, return a validator function that takes
   a Statement as an argument and returns an nilable seq of error data."
  [template]
  (let [rules' (add-det-properties template)
        preds  (mapv create-rule-validator rules')]
    (fn [statement]
      (let [errors (->> preds
                        (map (fn [f] (f statement)))
                        (filter some?))]
        (when (not-empty errors) errors)))))

(defn create-template-predicate
  "Like `create-template-validator`, but returns a predicate that takes
   a Statement as an argument and returns a boolean."
  [template]
  (let [rules' (add-det-properties template)
        preds  (mapv create-rule-validator rules')]
    (fn [statement]
      (->> preds
           (map (fn [f] (f statement)))
           (every? nil?)))))
