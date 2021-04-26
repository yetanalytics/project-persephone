(ns com.yetanalytics.persephone.template
  (:require [clojure.string :as string]
            [com.yetanalytics.persephone.utils.json :as json]
            [com.yetanalytics.persephone.template.predicates
             :refer [create-rule-pred]]))

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
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; A Statement Template MUST include all Determining Properties.
;; All Determining Properties are simply special instances of rules, and can
;; be rewritten as such.

(defn- add-determining-properties
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
;; Validator + template defs and utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare create-template-validator)
(declare create-template-predicate)

(defn- template-id->fn
  "Get the template validator or predicate using `get-template-fn`,
   and throw if `get-template-fn` returns nil."
  [create-from-temp-fn {:keys [get-template-fn] :as stmt-ref-fns} template-id]
  (if-some [template (get-template-fn template-id)]
    (create-from-temp-fn template stmt-ref-fns)
    (throw (ex-info (str "Template not found: " template-id)
                    {:kind        ::template-not-found
                     :template-id template-id
                     :template-fn get-template-fn}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validators
;; A validator returns a seq of errors upon failure, nil otherwise
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-statement-ref-validator
  "The arguments are as follows:
   `stmt-ref-templates`  The Statement Ref Template array
   `stmt-ref-path`     The location of the Statement Reference
   `get-template-fn`   A function to get a Template by ID
   `get-statement-fn`  A function that takes in a Statement ID and
                       returns the Statement
   
   The function returns a potentially-nested vector of errors if
   validation fails, nil otherwise."
  [stmt-ref-template-ids stmt-ref-path statement-ref-opts]
  (let [{:keys [get-statement-fn]} statement-ref-opts
        stmt-validators  (map (partial template-id->fn
                                       create-template-validator
                                       statement-ref-opts)
                              stmt-ref-template-ids)
        sref-path        (parse-locator stmt-ref-path)
        validate-stmt-fn (fn [sref-stmt]
                           ;; Return nil on first valid stmt ref template
                           (-> (reduce (fn [acc validator]
                                         (if-some [errs (validator sref-stmt)]
                                           (concat acc errs)
                                           (reduced nil)))
                                       '()
                                       stmt-validators)
                               vec
                               not-empty))]
    (fn [statement]
      ;; Pre-define let-bindings to avoid deep if-some nesting
      (let [sref       (first (find-values statement sref-path))
            sref-type? (= "StatementRef" (get sref "objectType"))
            sref-id    (get sref "id")
            sref-stmt  (when (and sref-type? sref-id)
                         (get-statement-fn sref-id))]
        (cond
          (not sref)
          [{:pred :statement-ref?
            :vals statement
            :rule {:location stmt-ref-path
                   :failure  :sref-not-found}}]
          (not sref-type?)
          [{:pred :statement-ref?
            :vals sref
            :rule {:location stmt-ref-path
                   :failure  :sref-object-type-invalid}}]
          (not sref-id)
          [{:pred :statement-ref?
            :vals sref
            :rule {:location stmt-ref-path
                   :failure  :sref-id-missing}}]
          (not sref-stmt)
          [{:pred :statement-ref?
            :vals sref-id
            :rule {:location stmt-ref-path
                   :failure  :sref-stmt-not-found}}]
          ;; TODO: Add additional errors for referencing future statements?
          :else
          (validate-stmt-fn sref-stmt))))))

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
          {:pred fail-pred
           :vals values
           :rule rule})))))

(defn- create-rule-validators
  [template rules ?stmt-ref-opts]
  (let [{?obj-srts :objectStatementRefTemplate
         ?ctx-srts :contextStatementRefTemplate}
        template]
    (cond-> (mapv create-rule-validator rules)
      (and ?stmt-ref-opts ?obj-srts)
      (conj (create-statement-ref-validator ?obj-srts
                                            "$.object"
                                            ?stmt-ref-opts))
      (and ?stmt-ref-opts ?ctx-srts)
      (conj (create-statement-ref-validator ?ctx-srts
                                            "$.context.statement"
                                            ?stmt-ref-opts)))))

(defn create-template-validator
  "Given `template`, return a validator function that takes a
   Statement as an argument and returns an nilable seq of error data."
  ([template]
   (create-template-validator template nil))
  ([template statement-ref-fns]
   (let [{temp-id :id} template
         rules         (add-determining-properties template)
         validators    (create-rule-validators template
                                               rules
                                               statement-ref-fns)]
     (fn [statement]
       (let [stmt-id (get statement "id")
             errors  (->> validators
                          (map (fn [validator] (validator statement)))
                          flatten ; concat error colls from different validators
                          (filter some?)
                          (map (fn [e] (update e :stmt #(if-not % stmt-id %))))
                          (map (fn [e] (update e :temp #(if-not % temp-id %)))))]
         (not-empty errors))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Predicates
;; A predicate returns true on success, false on failure
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-statement-ref-predicate
  "Same arguments as `create-statement-ref-validator`.
   
   The function returns false if validation fails, true otherwise."
  [stmt-ref-template-ids stmt-ref-path statement-ref-opts]
  (let [{:keys [get-statement-fn]} statement-ref-opts
        stmt-predicates  (map (partial template-id->fn
                                       create-template-predicate
                                       statement-ref-opts)
                              stmt-ref-template-ids)
        sref-path        (parse-locator stmt-ref-path)
        valid-sref-stmt? (fn [sref-stmt]
                           (some (fn [predicate] (predicate sref-stmt))
                                 stmt-predicates))]
    (fn [statement]
      (let [sref       (first (find-values statement sref-path))
            sref-type? (= "StatementRef" (get sref "objectType"))
            sref-id    (get sref "id")]
        (boolean ; coerce nils to false
         (when (and sref-type? sref-id)
           (when-some [sref-stmt (get-statement-fn sref-id)]
             (valid-sref-stmt? sref-stmt))))))))

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

(defn- create-rule-predicates
  [template rules ?stmt-ref-opts]
  (let [{?obj-srts :objectStatementRefTemplate
         ?ctx-srts :contextStatementRefTemplate}
        template]
    (cond-> (mapv create-rule-predicate rules)
      (and ?stmt-ref-opts ?obj-srts)
      (conj (create-statement-ref-predicate ?obj-srts
                                            "$.object"
                                            ?stmt-ref-opts))
      (and ?stmt-ref-opts ?ctx-srts)
      (conj (create-statement-ref-predicate ?ctx-srts
                                            "$.context.statement"
                                            ?stmt-ref-opts)))))

(defn create-template-predicate
  "Like `create-template-validator`, but returns a predicate that takes
   a Statement as an argument and returns a boolean."
  ([template]
   (create-template-predicate template nil))
  ([template statement-ref-fns]
   (let [rules (add-determining-properties template)
         preds (create-rule-predicates template rules statement-ref-fns)]
     (fn [statement]
       (every? (fn [pred] (pred statement)) preds)))))
