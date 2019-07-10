(ns com.yetanalytics.template-validation
  (:require [clojure.set :as cset]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Determining Properties predicates.
;; A Statment MUST include all the Determing Properties in the Statement 
;; Template.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statement MUST include the Verb of the Statement Template.
(defn verb? [t-verb statement]
  (let [s-verb (-> statement :verb :id)]
    (and (some? s-verb)
         (= s-verb t-verb))))

;; Statement MUST include the objectActivityType (the activityType of the
;; object Activity) of the Statement Template.
(defn object-activity-type? [t-oat statement]
  (let [s-oat (-> statement :object :definition :type)]
    (and (some? s-oat)
         (= s-oat t-oat))))

;; Statement MUST include contextParentActivityTypes (the activityTypes of the
;; context's parent Activities) from the Statement Template.
(defn context-parent-activity-types? [t-cpats statement]
  (let [s-cpats (-> statement :context :contextActivities :parent
                    (util/get-value-map :type))]
    (and (not (empty? s-cpats))
         (cset/subset? (set s-cpats) (set t-cpats)))))

;; Statement MUST include contextGroupingActivityTypes (the activityTypes of 
;; the context's grouping Activities) from the Statement Template.
(defn context-grouping-activity-types? [t-cgats statement]
  (let [s-cgats (-> statement :context :contextActivities :grouping
                    (util/get-value-map :type))]
    (and (not (empty? s-cgats))
         (cset/subset? (set s-cgats) (set t-cgats)))))

;; Statement MUST include contextCategoryActivityTypes (the activityTypes of 
;; the context's category Activities) from the Statement Template.
(defn context-category-activity-types? [t-ccats statement]
  (let [s-ccats (-> statement :context :contextActivities :category
                    (util/get-value-map :type))]
    (and (not (empty? s-ccats))
         (cset/subset? (set s-ccats) (set t-ccats)))))

;; Statement MUST include contextOtherActivityTypes (the activityTypes of the
;; context's other Activities) from the Statement Template.
(defn context-other-activity-types? [t-coats statement]
  (let [s-coats (-> statement :context :contextActivities :other
                    (util/get-value-map :type))]
    (and (not (empty? s-coats))
         (cset/subset? (set s-coats) (set t-coats)))))

;; Statement MUST include attachmentUsageTypes (the usageTypes of a statement's
;; attachments) from the StatementTemplate.
(defn attachment-usage-types? [t-auts statement]
  (let [s-auts (-> statement :attachments (util/get-value-map :usageType))]
    (and (not (empty? s-auts))
         (cset/subset? (set s-auts) (set t-auts)))))

(defn create-det-properties-spec
  "Given a Statement Template as an argument, return a spec for the Template's
  Determing Properties."
  [{:keys [verb objectActivityType contextParentActivityType
           contextGroupingActivityType contextCategoryActivityType
           contextOtherActivityType attachmentUsageType]}]
  (s/and (util/cond-on-val verb
                           (partial verb? verb))
         (util/cond-on-val objectActivityType
                           (partial object-activity-type?
                                    objectActivityType))
         (util/cond-on-val contextParentActivityType
                           (partial context-parent-activity-type?
                                    contextParentActivityType))
         (util/cond-on-val contextGroupingActivityType
                           (partial context-grouping-activity-types?
                                    contextGroupingActivityType))
         (util/cond-on-val contextCategoryActivityType
                           (partial context-category-activity-types?
                                    contextCategoryActivityType))
         (util/cond-on-val contextOtherActivityType
                           (partial context-other-activity-types?
                                    contextOtherActivityType))
         (util/cond-on-val attachmentUsageType
                           (partial attachment-usage-types?
                                    attachmentUsageType))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement References predicates.
;; A Statement's StatementRefs MUST reference Statements that match one of
;; the Statement Templates referenced by the main Template.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO Fill in

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicates.
;; A Statement MUST follow all the rules in the Statement Template.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; If 'any' is provided, evaluated values MUST include at least one value
;; that is given by 'any'.
(defn any-valid? [presence any values]
  (fn [any values]
    (let [any-set (set any)] (some (partial contains? any-set) values))))

;; If 'all' is provided, evaluated values MUST only include values in 'all'
;; and MUST NOT include any unmatchable values.
(defn all-valid? [presence all values]
  (fn [all values]
    (let [all-set (set all)] (every? (partial contains? all-set) values))))

;; If 'none' is provided, evaluated values MUST NOT include any values in
;; 'none'
(defn none-valid? [presence none values]
  (fn [none values]
    (let [none-set (set none)]
      (not (some (partial contains? none-set) values)))))

(defn any-all-none?
  "Super-predicate over the any-valid?, all-valid?, and none-valid? 
  predicates."
  [{:keys [any all none] :as rule} values]
  (every? true? (cond-> []
                  any (any-valid? any values)
                  all (all-valid? all values)
                  none (none-valid? none values))))

;; MUST apply the 'any', 'all' and 'none' requirements if presence is missing,
;; 'included' or 'recommended' (ie. not 'excluded') and any matchable values
;; are in the statement.

;; If presence is 'included', location and selector MUST return at least one
;; value (ie. MUST include at least one matchable value and MUST NOT include
;; any unmatchable values). 
(defn included?
  "Returns true if presence is 'included' and the values follow the specs."
  [{:keys [presence] :as rule} values]
  (and (#{"included"} presence)
       (not (empty? values))
       (any-all-none? rule values)))

;; If presence is 'excluded', location and selector MUST NOT return any values
;; (ie. MUST NOT include any matchable values). 
(defn excluded?
  "Returns true if presence is 'excluded' and the values follow the specs."
  [{:keys [presence] :as rule} values]
  (and (#{"excluded"} presence)
       (empty? values)))

;; If presence is 'recommended', the evaluated values MUST conform to the 'any',
;; 'all' and 'none' specs (but recommended doesn't have restrictions of its
;; own).
(defn recommended?
  "Returns true if presence is 'recommended' and the values follow the specs."
  [{:keys [presence] :as rule} values]
  (and (#{"recommended" presence})
       (if (not (empty? values))
         (any-all-none? rule values)
         true)))

;; If presence is missing, the evaluated values MUST conform to the 'any',
;; 'all' and 'none specs.
(defn valid-values?
  "Returns true if presence is missing and the values follow the specs."
  [{:keys [presence] :as rule} values]
  (and (nil? presence)
       (if (not (empty? values))
         (any-all-none? rule values)
         true)))

(defn create-rule-spec
  "Given a rule as an argument, return a spec for that rule that will validate
  a Statement."
  [{:keys [presence any all none] :as rule}]
  (s/or :included (partial included? rule)
        :exluded (partial excluded? rule)
        :recommended (partial recommended? rule)
        :no-presence (partial valid-values? rule)))

; (defn get-values
;   "Using the 'locator' and 'selector properties, return the evaluated values
;   from a Statement."
;   [statement location & {:selector selector}]
;   (let [location-vals (util/read-json statement location)]
;     (if (some? selector)
;       (util/read-json location-vals selector)
;       location-vals)))

;; TODO Write functions that actually validate Statements
