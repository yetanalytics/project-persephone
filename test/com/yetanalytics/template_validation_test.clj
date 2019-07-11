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

;; TODO Finish remaining tests
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
