(ns com.yetanalytics.template-validation
  (:require [clojure.set :as cset]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn string-same?
  "Return true if string-1 exists and is equal to string-2, false otherwise."
  [string-1 string-2]
  (and (some? string-1)
       (= string-1 string-2)))

(defn set-subset?
  "Return true if set-1 exists and is a subset of set-2, false otherwise."
  [set-1 set-2]
  (and (not (empty? set-1))
       (cset/subset? set-1 set-2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Determining Properties predicates.
;; A Statment MUST include all the Determing Properties in the Statement 
;; Template.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Statement MUST include the Verb of the Statement Template.
(defn verb? [t-verb statement]
  "Return true if Statement's Verb exists and whose id matches the Statement 
  Template's; false otherwise."
  (let [s-verb (-> statement :verb :id)]
    (string-same? s-verb t-verb)))

;; Statement MUST include the objectActivityType of the Statement Template.
(defn object-activity-type? [t-oat statement]
  "Return true if Statement's objectActivityType exists and whose id matches 
  the Statement Template's; false otherwise."
  (let [s-oat (-> statement :object :definition :type)]
    (string-same? s-oat t-oat)))

;; Statement MUST include contextParentActivityTypes from the Template.
(defn context-parent-activity-types? [t-cpats statement]
  "Return true if Statement's contextParentActivityTypes (the type of each
  parent contextActivity) exist and are included in the Statement Template;
  false otherwise."
  (let [s-cpats (-> statement :context :contextActivities :parent
                    (util/value-map :definition :type))]
    (and (not (empty? s-cpats))
         (cset/subset? (set s-cpats) (set t-cpats)))))

;; Statement MUST include contextGroupingActivityTypes from the Template.
(defn context-grouping-activity-types? [t-cgats statement]
  "Return true if Statement's contextGroupingActivityTypes (the type of each
  grouping contextActivity) exist and are included in the Statement Template;
  false otherwise."
  (let [s-cgats (-> statement :context :contextActivities :grouping
                    (util/value-map :definition :type))]
    (and (not (empty? s-cgats))
         (cset/subset? (set s-cgats) (set t-cgats)))))

;; Statement MUST include contextCategoryActivityTypes from the Template.
(defn context-category-activity-types? [t-ccats statement]
  "Return true if Statement's contextCategoryActivityTypes (the type of each
  category contextActivity) exist and are included in the Statement Template;
  false otherwise."
  (let [s-ccats (-> statement :context :contextActivities :category
                    (util/value-map :definition :type))]
    (and (not (empty? s-ccats))
         (cset/subset? (set s-ccats) (set t-ccats)))))

;; Statement MUST include contextOtherActivityTypes from the Template.
(defn context-other-activity-types? [t-coats statement]
  "Return true if Statement's contextOtherActivityTypes (the type of each
  other contextActivity) exist and are included in the Statement Template;
  false otherwise."
  (let [s-coats (-> statement :context :contextActivities :other
                    (util/value-map :definition :type))]
    (and (not (empty? s-coats))
         (cset/subset? (set s-coats) (set t-coats)))))

;; Statement MUST include attachmentUsageTypes from the Statement Template.
(defn attachment-usage-types? [t-auts statement]
  "Return true if the Statement's attachmentUsageTypes (the usageType of each
  attachment) exist and are in the Statement Template; false otherwise."
  (let [s-auts (-> statement :attachments (util/value-map :usageType))]
    (and (not (empty? s-auts))
         (cset/subset? (set s-auts) (set t-auts)))))

(defn create-det-properties-spec
  "Given a Statement Template as an argument, return a spec for the Template's
  Determing Properties."
  [{:keys [verb objectActivityType contextParentActivityType
           contextGroupingActivityType contextCategoryActivityType
           contextOtherActivityType attachmentUsageType]}]
  (s/and (util/predicate verb? verb)
         (util/predicate object-activity-type? objectActivityType)
         (util/predicate context-parent-activity-types?
                         contextParentActivityType)
         (util/predicate context-grouping-activity-types?
                         contextGroupingActivityType)
         (util/predicate context-category-activity-types?
                         contextCategoryActivityType)
         (util/predicate context-other-activity-types?
                         contextOtherActivityType)
         (util/predicate attachment-usage-types? attachmentUsageType)))

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
(defn any-valid?
  "Return true if there's at least one value in 'any'; false otherwise."
  [any values]
  (let [any-set (set any)]
    (some (partial contains? any-set) values)))

;; If 'all' is provided, evaluated values MUST only include values in 'all'
;; and MUST NOT include any unmatchable values.
(defn all-valid?
  "Return true is every value is in 'all'; false otherwise."
  [all values]
  (let [all-set (set all)]
    (and (not (empty? values))
         (every? (partial contains? all-set) values))))

;; If 'none' is provided, evaluated values MUST NOT include any values in
;; 'none'
(defn none-valid?
  "Return true if there are no values in 'none'; false otherwise."
  [none values]
  (let [none-set (set none)]
    (not (some (partial contains? none-set) values))))

(defn any-all-none?
  "Super-predicate over the any-valid?, all-valid?, and none-valid? 
  predicates. Ignore predicates where the corresponding value is nil."
  [{:keys [any all none] :as rule} values]
  (every? true? (cond-> []
                  any (conj (any-valid? any values))
                  all (conj (all-valid? all values))
                  none (conj (none-valid? none values)))))

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
;; 'recommended' is only a thing to allow a rule to not have any/all/none.
(defn recommended?
  "Returns true if presence is 'recommended' and the values follow the specs."
  [{:keys [presence] :as rule} values]
  (and (#{"recommended"} presence)
       (if (not (empty? values))
         (any-all-none? rule values)
         true)))

;; If presence is missing, the evaluated values MUST conform to the 'any',
;; 'all' and 'none' specs.
(defn missing?
  "Returns true if presence is missing and the values follow the specs.
  Acts as a 'default' if presence is not included, exlucded nor recommended."
  [{:keys [presence] :as rule} values]
  (and (not (contains? rule :presence))
       (if (not (empty? values))
         (any-all-none? rule values)
         true)))

(defn create-rule-spec
  "Given a rule as an argument, return a spec for that rule that will validate
  a Statement."
  [{:keys [presence any all none] :as rule}]
  (let [is-included? (partial included? rule)
        is-excluded? (partial excluded? rule)
        is-recommended? (partial recommended? rule)
        is-missing? (partial missing? rule)]
    (s/or :included is-included?
          :excluded is-excluded?
          :recommended is-recommended?
          :no-presence is-missing?)))

(s/explain (create-rule-spec {:presence "excluded"}) ["foo"])

; (defn get-values
;   "Using the 'locator' and 'selector properties, return the evaluated values
;   from a Statement."
;   [statement location & {:selector selector}]
;   (let [location-vals (util/read-json statement location)]
;     (if (some? selector)
;       (util/read-json location-vals selector)
;       location-vals)))

;; TODO Write functions that actually validate Statements
