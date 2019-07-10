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
           Statement MUST include contextParentActivityType of Template."))

(deftest context-grouping-activity-types?-test
  (testing "context-grouping-activity-types? predicate:
           Statement MUST include contextGroupingActivityType of Template."))

(deftest context-category-activity-types?-test
  (testing "context-category-activity-types? predicate:
           Statement MUST include contextCategoryActivityType of Template."))

(deftest context-other-activity-types?-test
  (testing "context-other-activity-types? predicate:
           Statement MUST include contextOtherActivityType of Template."))

(deftest attachment-usage-types?-test
  (testing "attachment-usage-types? predicate:
           Statement MUST include attachmentUsageType of Template."))
