(ns com.yetanalytics.template-validation-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.util :as util]
            [com.yetanalytics.template-validation :as tv]))

(def ex-statement-1
  (util/json-to-edn (slurp "resources/sample_statements/adl_1.json")))
(def ex-statement-2
  (util/json-to-edn (slurp "resources/sample_statements/adl_2.json")))
(def ex-statement-3
  (util/json-to-edn (slurp "resources/sample_statements/adl_3.json")))
(def ex-statement-4
  (util/json-to-edn (slurp "resources/sample_statements/adl_4.json")))

;; Not a complete statement; only has context
(def ex-context-statement
  {:context
   {:contextActivities
    {:parent [{:id "http://foo.org/parent-activity"
               :definition {:type "http://foo.org/parent-activity-type"}}
              {:id "http://foo.org/parent-activity-2"
               :definition {:type "http://foo.org/parent-activity-type"}}]
     :grouping [{:id "http://foo.org/grouping-activity"
                 :definition {:type "http://foo.org/grouping-activity-type"}}
                {:id "http://foo.org/grouping-activity-2"
                 :definition {:type "http://foo.org/grouping-activity-type"}}]
     :category [{:id "http://foo.org/category-activity"
                 :definition {:type "http://foo.org/category-activity-type"}}
                {:id "http://foo.org/category-activity-2"
                 :definition {:type "http://foo.org/category-activity-type"}}]
     :other [{:id "http://foo.org/other-activity"
              :definition {:type "http://foo.org/other-activity-type"}}
             {:id "http://foo.org/other-activity-2"
              :definition {:type "http://foo.org/other-activity-type"}}]}}})

;; Example template
(def ex-template
  {:id "http://foo.org/example/template"
   :type "StatementTemplate"
   :inScheme "http://foo.org/profile/v1"
   :prefLabel {:en "Example Template"}
   :definition {:en "This template is an example template for test cases."}
   ;; Determining Properties
   :verb "http://foo.org/verb"
   :objectActivityType "http://foo.org/oat"
   :contextGroupingActivityType ["http://foo.org/cgat1" "http://foo.org/cgat2"]
   :contextParentActivityType ["http://foo.org/cpat1" "http://foo.org/cpat2"]
   :contextOtherActivityType ["http://foo.org/coat1" "http://foo.org/coat2"]
   :contextCategoryActivityType ["http://foo.org/ccat1" "http://foo.org/ccat2"]
   :attachmentUsageType ["http://foo.org/aut1" "http://foo.org/aut2"]
   ;; Statement Reference Templates
   ;; Because our activity must be of Activity type, we can't have a object-
   ;; StatementRefTemplate
   :contextStatementRefTemplate ["http://foo.org/templates/template3"
                                 "http://foo.org/templates/template4"]
   ;; Rules
   :rules [{:locator "$.actor.objectType" :presence "included"}
           {:locator "$.actor.name" :presence "included"
            ;; Developers (and friends) only
            :any ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder"
                  "Erika Lee" "Boris Boiko"]
            :none ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony"
                   "Jeremy Gardner"]}
           {:locator "$.actor"
            :selector "$.mbox"
            :presence "included"}
           {:locator "$.actor"
            :selector "$.mbox_sha1sum"
            :presence "excluded"}]})

;; Statement that conforms to ex-template
;; Not a complete Statement, but has the minimum for validation
(def ex-statement-0
  {:id "some-uuid"
   :actor {:objectType "Agent"
           :name "Yet Analytics Dev Team"
           :mbox "mailto:email@yetanalytics.io"
           :member [{:name "Will Hoyt"}
                    {:name "Milt Reder"}
                    {:name "John Newman"}
                    {:name "Henk Reder"}
                    {:name "Erika Lee"}
                    {:name "Boris Boiko"}]}
   :verb {:id "http://foo.org/verb"}
   :object {:id "http://www.example.com/object"
            :definition {:type "http://foo.org/oat"}}
   :context {:contextActivities
             {:parent [{:id "http://foo.org/ca1"
                        :definition {:type "http://foo.org/cpat1"}}
                       {:id "http://foo.org/ca2"
                        :definition {:type "http://foo.org/cpat2"}}]
              :grouping [{:id "http://foo.org/ca3"
                          :definition {:type "http://foo.org/cgat1"}}
                         {:id "http://foo.org/ca4"
                          :definition {:type "http://foo.org/cgat2"}}]
              :category [{:id "http://foo.org/ca5"
                          :definition {:type "http://foo.org/ccat1"}}
                         {:id "http://foo.org/ca6"
                          :definition {:type "http://foo.org/ccat2"}}]
              :other [{:id "http://foo.org/ca7"
                       :definition {:type "http://foo.org/coat1"}}
                      {:id "http://foo.org/ca8"
                       :definition {:type "http://foo.org/coat2"}}]}}
   :attachments [{:usageType "http://foo.org/aut1"}
                 {:usageType "http://foo.org/aut2"}]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Determining Properties predicate tests.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest verb?-test
  (testing "verb? predicate: 
           Statement MUST include Verb of Statement Template"
    (is (tv/verb? "http://example.com/xapi/verbs#sent-a-statement"
                  ex-statement-1))
    (is (tv/verb? "http://adlnet.gov/expapi/verbs/attempted"
                  ex-statement-2))
    (is (tv/verb? "http://adlnet.gov/expapi/verbs/attended"
                  ex-statement-3))
    (is (tv/verb? "http://adlnet.gov/expapi/verbs/experienced"
                  ex-statement-4))
    (is (not (tv/verb? "https://foo.org/bar" ex-statement-1)))
    (is (not (tv/verb? "https://foo.org/bar"
                       {:key "What the pineapple"})))
    ;; Don't cheat by passing in nil
    (is (not (tv/verb? nil ex-statement-1)))
    (is (not (tv/verb? nil {:key "Stan Lonna"})))))

(deftest object-activity-type?-test
  (testing "object-activity-type? predicate:
           Statement MUST include objectActivityType of Template."
    (is (tv/object-activity-type? "http://adlnet.gov/expapi/activities/meeting"
                                  ex-statement-3))
    (is (not (tv/object-activity-type? "http://foo.org/nonexistent/activity"
                                       ex-statement-1)))
    (is (not (tv/object-activity-type? "http://foo.org/nonexistent/activity"
                                       ex-statement-2)))
    (is (not (tv/object-activity-type? "http://foo.org/nonexistent/activity"
                                       ex-statement-4)))
    ;; Don't cheat by passing in nil
    (is (not (tv/object-activity-type? nil ex-statement-1)))
    ;; Check internal functioning of predicate
    (is (nil? (-> ex-statement-4 :object :definition :type)))))

(deftest context-parent-activity-types?-test
  (testing "context-parent-activity-types? predicate:
           Statement MUST include contextParentActivityType of Template."
    (is (tv/context-parent-activity-types?
         ["http://foo.org/parent-activity-type"] ex-context-statement))
    (is (not (tv/context-parent-activity-types?
              ["http://foo.org/parent/activity-type-2"] ex-context-statement)))
    ;; Superset is okay
    (is (tv/context-parent-activity-types?
         ["http://foo.org/parent-activity-type"
          "http://foo.org/parent/activity-type-2"] ex-context-statement))
    (is (not (tv/context-parent-activity-types?
              ["http://example.com/expapi/activities/meetingcategory"]
              ex-statement-3)))
    (is (not (tv/context-parent-activity-types? [] ex-statement-1)))
    (is (not (tv/context-parent-activity-types? nil ex-statement-1)))))

(deftest context-grouping-activity-types?-test
  (testing "context-grouping-activity-types? predicate:
           Statement MUST include contextGroupingActivityType of Template."
    (is (tv/context-grouping-activity-types?
         ["http://foo.org/grouping-activity-type"] ex-context-statement))
    (is (not (tv/context-grouping-activity-types?
              ["http://foo.org/grouping/activity-type-2"] ex-context-statement)))
    ;; Superset is okay
    (is (tv/context-grouping-activity-types?
         ["http://foo.org/grouping-activity-type"
          "http://foo.org/grouping/activity-type-2"] ex-context-statement))
    (is (not (tv/context-grouping-activity-types?
              ["http://example.com/expapi/activities/meetingcategory"]
              ex-statement-3)))
    (is (not (tv/context-grouping-activity-types? [] ex-statement-1)))
    (is (not (tv/context-grouping-activity-types? nil ex-statement-1)))))

(deftest context-category-activity-types?-test
  (testing "context-category-activity-types? predicate:
           Statement MUST include contextCategoryActivityType of Template."
    (is (tv/context-category-activity-types?
         ["http://foo.org/category-activity-type"] ex-context-statement))
    (is (not (tv/context-category-activity-types?
              ["http://foo.org/category/activity-type-2"] ex-context-statement)))
    ;; Superset is okay
    (is (tv/context-category-activity-types?
         ["http://foo.org/category-activity-type"
          "http://foo.org/category/activity-type-2"] ex-context-statement))
    ;; Only instance where a contextActivity actually has a type in ADL 
    ;; examples
    (is (tv/context-category-activity-types?
         ["http://example.com/expapi/activities/meetingcategory"]
         ex-statement-3))
    (is (not (tv/context-category-activity-types? [] ex-statement-1)))
    (is (not (tv/context-category-activity-types? nil ex-statement-1)))))

(deftest context-other-activity-types?-test
  (testing "context-other-activity-types? predicate:
           Statement MUST include contextOtherActivityType of Template."
    (is (tv/context-other-activity-types?
         ["http://foo.org/other-activity-type"] ex-context-statement))
    (is (not (tv/context-other-activity-types?
              ["http://foo.org/other/activity-type-2"] ex-context-statement)))
    ;; Superset is okay
    (is (tv/context-other-activity-types?
         ["http://foo.org/other-activity-type"
          "http://foo.org/other/activity-type-2"] ex-context-statement))
    (is (not (tv/context-other-activity-types?
              ["http://example.com/expapi/activities/meetingcategory"]
              ex-statement-3)))
    (is (not (tv/context-other-activity-types? [] ex-statement-1)))
    (is (not (tv/context-other-activity-types? nil ex-statement-1)))))

(deftest attachment-usage-types?-test
  (testing "attachment-usage-types? predicate:
           Statement MUST include attachmentUsageType of Template."
    (is (tv/attachment-usage-types?
         ["http://adlnet.gov/expapi/attachments/signature"] ex-statement-4))
    (is (not (tv/attachment-usage-types?
              ["http://adlnet.gov/expapi/attachments/signature"]
              ex-statement-1)))
    (is (not (tv/attachment-usage-types?
              ["http://adlnet.gov/expapi/attachments/signature"]
              ex-statement-2)))
    (is (not (tv/attachment-usage-types?
              ["http://adlnet.gov/expapi/attachments/signature"]
              ex-statement-3)))
    ;; Superset is okay
    (is (tv/attachment-usage-types?
         ["http://adlnet.gov/expapi/attachments/signature"
          "http://foo.org/some-other-usage-type"] ex-statement-4))
    (is (not (tv/attachment-usage-types? [] ex-statement-4)))
    (is (not (tv/attachment-usage-types? nil ex-statement-4)))
    (is (not (tv/attachment-usage-types? nil ex-statement-1)))))

(deftest create-det-properties-spec-test
  (testing "create-det-properties-spec function:
           Create a spec that validates Statement against Template"
    (is (s/valid? (tv/create-det-properties-spec ex-template)
                  ex-statement-0))
    (is (not (s/valid? (tv/create-det-properties-spec ex-template) {})))
    ;; Fail on the verb
    (is (not (s/valid? (tv/create-det-properties-spec ex-template)
                       (assoc ex-statement-0
                              :verb {:id "http://foo.org/verb2"}))))
    ;; Fail on contextParentActivityType
    (is (not (s/valid?
              (tv/create-det-properties-spec ex-template)
              (update-in ex-statement-0 [:context :contextActivities :parent]
                         #(conj % {:id "http://foo.org/ca0"
                                   :definition
                                   {:type "http://foo.org/cpat3"}})))))
    ;; Removing a value is okay
    (is (s/valid? (tv/create-det-properties-spec ex-template)
                  (update-in ex-statement-0
                             [:context :contextActivities :parent] pop)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicate tests.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; name-values = ["Andrew Downes" "Toby "Nichols" "Ena Hills"]


(def name-values (-> ex-statement-3 :actor :member (util/value-map :name)))

(deftest any-valid?-test
  (testing "any-valid? function: values MUST include at least one value that is
           given by 'any', ie. the collections need to intersect."
    (is (tv/any-valid? ["Andrew Downes" "Toby Nichols"] name-values))
    (is (tv/any-valid? ["Andrew Downes" "Will Hoyt"] name-values))
    (is (not (tv/any-valid? ["Will Hoyt" "Milt Reder"] name-values)))
    (is (not (tv/any-valid? [] name-values)))
    ;; any-valid? is undefined if there are no matchable values
    (is (not (tv/any-valid? [] [])))))

(deftest all-valid?-test
  (testing "all-valid? function: values MUST all be from the values given by
           'all'."
    (is (tv/all-valid? ["Andrew Downes" "Toby Nichols" "Ena Hills"]
                       name-values))
    ;; Superset is okay
    (is (tv/all-valid? ["Andrew Downes" "Toby Nichols" "Ena Hills" "Will Hoyt"]
                       name-values))
    (is (not (tv/all-valid? ["Andrew Downes" "Toby Nichols"] name-values)))
    (is (not (tv/all-valid? [] name-values)))
    ;; MUST NOT include any unmatchable values 
    (is (not (tv/all-valid? ["Andrew Downes" "Toby Nichols" "Ena Hills"] [])))))

(deftest none-valid?-test
  (testing "none-valid? function: values MUST NOT be included in the set given
           by 'none'."
    (is (tv/none-valid? ["Will Hoyt" "Milt Reder"] name-values))
    (is (not (tv/none-valid? ["Andrew Downes"] name-values)))
    (is (not (tv/none-valid? ["Will Hoyt" "Milt Reder" "Ena Hills"]
                             name-values)))
    ;; If there is nothing to exclude, we should be okay
    (is (tv/none-valid? [] name-values))
    (is (tv/none-valid? [] []))))

(deftest any-all-none?-test
  (testing "any-all-none?: Super-predicate over any-valid?, all-valid? and
           none-valid?."
    (is (tv/any-all-none? {:any ["Andrew Downes" "Will Hoyt"]
                           :all ["Andrew Downes" "Toby Nichols" "Ena Hills"]
                           :none ["Milt Reder"]}
                          name-values))
    (is (tv/any-all-none? {:any ["Andrew Downes"]} name-values))
    (is (not (tv/any-all-none? {:any ["Will Hoyt"]} name-values)))
    (is (not (tv/any-all-none? {:any ["Andrew Downes" "Will Hoyt"]
                                :none ["Ena Hills"]}
                               name-values)))
    (is (tv/any-all-none? {} name-values))))

(deftest included?-test
  (testing "included? function: values MUST have at least one matchable value
           (and no unmatcable values) and MUST follow any/all/none reqs."
    (is (tv/included? {:presence "included" :any ["Andrew Downes"]}
                      name-values))
    (is (not (tv/included? {:presence "included" :any ["Will Hoyt"]}
                           name-values)))
    (is (not (tv/included? {:presence "recommended" :any ["Andrew Downes"]}
                           name-values)))
    (is (not (tv/included? {:presence "included" :any ["Andrew Downes"]} [])))))

(deftest excluded?-test
  (testing "excluded? function: MUST NOT have any matchable values."
    (is (tv/excluded? {:presence "excluded"} []))
    (is (not (tv/excluded? {:presence "excluded"} name-values)))
    (is (not (tv/excluded? {:presence "included"} [])))))

(deftest recommended?-test
  (testing "recommended? function: MUST follow any/all/none reqs."
    (is (tv/recommended? {:presence "recommended" :any ["Andrew Downes"]}
                         name-values))
    (is (tv/recommended? {:presence "recommended"} name-values))
    (is (not (tv/recommended? {:presence "recommended" :any ["Will Hoyt"]}
                              name-values)))
    (is (not (tv/recommended? {:presence "included" :any ["Andrew Downes"]}
                              name-values)))))

(deftest missing?-test
  (testing "missing? function: MUST follow any/all/none reqs."
    (is (tv/missing? {:any ["Andrew Downes"]} name-values))
    (is (not (tv/missing? {:presence "included" :any ["Andrew Downes"]}
                          name-values)))
    ;; The key itself has to be missing 
    (is (not (tv/missing? {:presence nil :any ["Andrew Downes"]} name-values)))
    (is (not (tv/missing? {:any ["Will Hoyt"]} name-values)))))
