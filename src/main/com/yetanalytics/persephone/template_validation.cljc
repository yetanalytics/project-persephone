(ns com.yetanalytics.persephone.template-validation
  (:require [clojure.set :as cset]
            [clojure.string :as string]
            [com.yetanalytics.persephone.utils.json :as json])
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
  (->> coll (filter some?) empty?))

(defn any-matchable?
  "Returns true iff at least one matchable value in `coll` exists."
  [coll]
  (->> coll (filter some?) not-empty boolean))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicates.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- presence-pred-fn
  "pulls out the functionality common to the -values? functions

  `pred-fn` should be a fn of 2 arity
  - first arg = `presence` set i.e. any | all | none
  - second arg = `values` collection"
  [presence-set values-coll pred-fn]
  (pred-fn presence-set (-> values-coll set (disj nil))))

;; If 'any' is provided, evaluated values MUST include at least one value
;; that is given by 'any'. In other words, the set of 'any' and the evaluated
;; values MUST interesect.
(defn some-any-values?
  "Return true if there's at least one value from `vals-coll` in
   `any-set`; false otherwise."
  [any-set vals-coll]
  (presence-pred-fn
   any-set
   vals-coll
   (fn [p v] (-> (cset/intersection v p) not-empty boolean))))

;; If 'all' is provided, evaluated values MUST only include values in 'all'.
;; In other words, the set of 'all' MUST be a superset of the evaluated values.
(defn only-all-values?
  "Return true is every value from `vals-coll` is in `all-set`;
   false otherwise."
  [all-set vals-coll]
  (presence-pred-fn
   all-set
   vals-coll
   (fn [p v] (cset/subset? v p))))

;; If 'none' is provided, evaluated values MUST NOT include any values in
;; 'none'. In other words, the set of 'none' and the evaluated values MUST NOT
;; intersect.
(defn no-none-values?
  "Return true if there are no values from `vals-coll` in `none-set`;
   false otherwise."
  [none-set vals-coll]
  (presence-pred-fn
   none-set
   vals-coll
   (fn [p v] (empty? (cset/intersection v p)))))

(defn every-val-present?
  "Return true if every value in `presence-set` can be found in
   `vals-coll`; false otherwise."
  [presence-set vals-coll]
  (presence-pred-fn
   presence-set
   vals-coll
   (fn [p v] (cset/superset? v p))))

;; If 'all' is provided, evaluated values MUST NOT include any unmatchable
;; values.
(defn no-unmatch-vals?
  "Return true if no unmatchable values from `vals-coll` exist;
   false otherwise."
  [_all-set vals-coll]
  (all-matchable? vals-coll))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rule spec creation.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- nset
  "'nilable set'; like `clojure.core/set` but preserves nil args."
  [coll]
  (when (some? coll) (set coll)))

;; MUST apply the 'any', 'all' and 'none' requirements if presence is missing,
;; 'included' or 'recommended' (ie. not 'excluded') and any matchable values
;; are in the statement.
;;
;; If presence is 'included', location and selector MUST include at least one
;; matchable value and MUST NOT include any unmatchable values. 
(defn create-included-pred
  "Returns a wrapped pred for when presence is 'included.'"
  [{:keys [any all none]}]
  (let [any-set  (nset any)
        all-set  (nset all)
        none-set (nset none)]
    (-> (and-wrapped (wrap-pred any-matchable?) (wrap-pred all-matchable?))
        (add-wrapped some-any-values? any-set)
        (add-wrapped only-all-values? all-set) ;; no-unmatch-vals? is redundant
        (add-wrapped no-none-values? none-set))))

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
  "Returns a wrapped pred for when presence is 'recommended' or is
   missing."
  [{:keys [any all none]}]
  (let [any-set  (nset any)
        all-set  (nset all)
        none-set (nset none)]
    (or-wrapped (-> (wrap-pred any-matchable?)
                    (add-wrapped some-any-values? any-set)
                    (add-wrapped only-all-values? all-set)
                    (add-wrapped no-unmatch-vals? all-set)
                    (add-wrapped no-none-values? none-set))
                (wrap-pred none-matchable?))))

(defn create-det-prop-pred
  "Returns a wrapped pred for use with Determining Properties.
   `prop-vals` contains the values of the given Property."
  [{:keys [prop-vals]}]
  (let [pres-set (nset prop-vals)]
    (-> (wrap-pred any-matchable?)
        (add-wrapped every-val-present? pres-set))))

;; Spec to check that the 'presence' keyword is correct.
;; A Statement Template MUST include one or more of presence, any, all or none.
;; NOTE: Should never happen with a validated Statement Template or Profile
(defn- assert-valid-rule
  [rule]
  (when-not (or (contains? rule :determining-property)
                (and (not (contains? rule :presence))
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
  (if (contains? rule :determining-property)
    (create-det-prop-pred rule)
    (case presence
      "included"    (create-included-pred rule)
      "excluded"    (create-excluded-pred rule)
      "recommended" (create-default-pred rule)
      nil           (create-default-pred rule))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-values
  "Given a Statement `stmt`, a parsed location JSONPath `loc-path`,
   and an optional selector JSONPath `select-path`, return a vector
   of the selected values. Unmatchable values are returned as nils."
  ([stmt loc-path]
   (find-values stmt loc-path nil))
  ([stmt loc-path select-path]
   (let [locations (json/get-jsonpath-values stmt loc-path)]
     (if-not select-path
       locations ;; No selector - return locations
       (json/get-jsonpath-values locations select-path)))))

(defn parse-locator
  "Parse the `locator` path."
  [locator]
  (json/parse-jsonpath locator))

(defn parse-selector
  "Conform and parse the `selector` path to be used on values returned
   by a locator."
  [selector]
  ;; Locations values will be a JSON array, so we can query it using the
  ;; selector by adding a wildcard at the beginning.
  (-> selector (string/replace #"(\$)" "$1[*]") json/parse-jsonpath))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Determining Properties
;; A Statement Template MUST include all Determining Properties.
;; All Determining Properties are simply special instances of rules, and can
;; be rewritten as such.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-determining-properties
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
              (conj {:location  "$.verb.id"
                     :prop-vals [verb]
                     :determining-property "Verb"})
              objectActivityType
              (conj {:location  "$.object.definition.type"
                     :prop-vals [objectActivityType]
                     :determining-property "objectActivityType"})
              contextParentActivityType
              (conj {:location
                     "$.context.contextActivities.parent[*].definition.type"
                     :prop-vals contextParentActivityType
                     :determining-property "contextParentActivityType"})
              contextGroupingActivityType
              (conj {:location
                     "$.context.contextActivities.grouping[*].definition.type"
                     :prop-vals contextGroupingActivityType
                     :determining-property "contextGroupingActivityType"})
              contextCategoryActivityType
              (conj {:location
                     "$.context.contextActivities.category[*].definition.type"
                     :prop-vals contextCategoryActivityType
                     :determining-property "contextCategoryActivityType"})
              contextOtherActivityType
              (conj {:location
                     "$.context.contextActivities.other[*].definition.type"
                     :prop-vals contextOtherActivityType
                     :determining-property "contextOtherActivityType"})
              attachmentUsageType
              (conj {:location  "$.attachments[*].usageType"
                     :prop-vals attachmentUsageType
                     :determining-property "attachmentUsageType"})))]
    ;; refactor makes the process more clear, via seperation of concerns
    ;; - build rule config from template definition
    ;; - aggregate all rules together into a single coll
    (-> [] build-det-props (into rules))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Ref Template validators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare create-template-validator)
(declare create-template-predicate)

(defn- template-id->fn
  [create-from-temp-fn get-template-fn template-id]
  (if-let [template (get-template-fn template-id)]
    (create-from-temp-fn template)
    (throw (ex-info "Template not found!"
                    {:kind        ::template-not-found
                     :template-id template-id
                     :template-fn get-template-fn}))))

(defn- create-statement-ref-validator
  "The arguments are as follows:
   `stmt-ref-templates`  The Statement Ref Template array
   `stmt-ref-path`     The location of the Statement Reference
   `get-template-fn`   A function to get a Template by ID
   `get-statement-fn`  A function that takes in a Statement ID and
                       returns the Statement
   
   The function returns a potentially-nested vector of errors if
   validation fails, nil otherwise."
  [stmt-ref-template-ids stmt-ref-path get-template-fn get-statement-fn]
  (let [stmt-validators (map (partial template-id->fn
                                      create-template-validator
                                      get-template-fn)
                             stmt-ref-template-ids)
        stmt-ref-path'  (parse-locator stmt-ref-path)]
    (fn [statement]
      ;; TODO: Throw/return errors
      (let [stmt-ref (first (find-values statement stmt-ref-path'))
            stmt-id  (get stmt-ref "id")
            stmt     (get-statement-fn stmt-id)]
        (-> (reduce (fn [acc validator]
                      (if-some [errs (validator stmt)]
                        (concat acc errs)
                        (reduced nil)))
                    '()
                    stmt-validators)
            vec
            not-empty)))))

(defn- create-statement-ref-predicate
  "Same arguments as `create-statement-ref-validator`.
   
   The function returns false if validation fails, true otherwise."
  [stmt-ref-template-ids stmt-ref-path get-template-fn get-statement-fn]
  (let [stmt-predicates (map (partial template-id->fn
                                      create-template-predicate
                                      get-template-fn)
                             stmt-ref-template-ids)
        stmt-ref-path'  (parse-locator stmt-ref-path)]
    (fn [statement]
      ;; TODO: Throw/return errors
      (let [stmt-ref (first (find-values statement stmt-ref-path'))
            stmt-id  (get stmt-ref "id")
            stmt     (get-statement-fn stmt-id)]
        (boolean (some (fn [predicate] (predicate stmt)) stmt-predicates))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validators and predicates
;; Validators return an error map on failure and nil otherwise.
;; Predicates return a boolean value.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-rule-validator
  "Given `rule`, create a function that will validate new Statements
   against the rule."
  [{:keys [location selector] :as rule}]
  (let [rule-validator (create-rule-pred rule)
        location-path  (parse-locator location)
        selector-path  (when selector (parse-selector selector))]
    (fn [statement]
      (let [values    (find-values statement location-path selector-path)
            fail-pred (rule-validator values)]
        ;; nil indicates success
        (when-not (nil? fail-pred)
          ;; :pred - the predicate that failed, causing this error
          ;; :values - the values that the predicate failed on
          ;; :rule - the Statement Template rule associated with the error
          {:pred   fail-pred
           :values values
           :rule   rule})))))

(defn create-rule-predicate
  "Given `rule`, create a function that returns true or false when
   validating Statements against it."
  [{:keys [location selector] :as rule}]
  (let [rule-validator (create-rule-pred rule)
        location-path  (parse-locator location)
        selector-path  (when selector (parse-selector selector))]
    (fn [statement]
      (let [values    (find-values statement location-path selector-path)
            fail-pred (rule-validator values)]
        ;; nil indicates success
        (nil? fail-pred)))))

(defn- create-rule-validators
  [template rules ?stmt-ref-opts]
  (let [{?obj-srts :objectStatementRefTemplate
         ?ctx-srts :contextStatementRefTemplate}
        template
        {get-temp-fn :get-template-fn
         get-stmt-fn :get-statement-fn}
        ?stmt-ref-opts]
    (cond-> (mapv create-rule-validator rules)
      (and ?stmt-ref-opts ?obj-srts)
      (conj (create-statement-ref-validator ?obj-srts
                                            "$.object"
                                            get-temp-fn
                                            get-stmt-fn))
      (and ?stmt-ref-opts ?ctx-srts)
      (conj (create-statement-ref-validator ?ctx-srts
                                            "$.context.statement"
                                            get-temp-fn
                                            get-stmt-fn)))))

(defn- create-rule-predicates
  [template rules ?stmt-ref-opts]
  (let [{?obj-srts :objectStatementRefTemplate
         ?ctx-srts :contextStatementRefTemplate}
        template
        {get-temp-fn :get-template-fn
         get-stmt-fn :get-statement-fn}
        ?stmt-ref-opts]
    (cond-> (mapv create-rule-predicate rules)
      (and ?stmt-ref-opts ?obj-srts)
      (conj (create-statement-ref-predicate ?obj-srts
                                            "$.object"
                                            get-temp-fn
                                            get-stmt-fn))
      (and ?stmt-ref-opts ?ctx-srts)
      (conj (create-statement-ref-predicate ?ctx-srts
                                            "$.context.statement"
                                            get-temp-fn
                                            get-stmt-fn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bringing it all together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-template-validator
  "Given `template`, return a validator function that takes a
   Statement as an argument and returns an nilable seq of error data."
  [template & {:keys [statement-ref-opts]}]
  (let [rules      (add-determining-properties template)
        validators (create-rule-validators template rules statement-ref-opts)]
    (fn [statement]
      (let [errors (->> validators (map #(% statement)) flatten (filter some?))]
        (not-empty errors)))))

(defn create-template-predicate
  "Like `create-template-validator`, but returns a predicate that takes
   a Statement as an argument and returns a boolean."
  [template & {:keys [statement-ref-opts]}]
  (let [rules (add-determining-properties template)
        preds (create-rule-predicates template rules statement-ref-opts)]
    (fn [statement]
      (every? (fn [pred] (pred statement)) preds))))
