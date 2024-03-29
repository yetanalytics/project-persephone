(ns com.yetanalytics.persephone-test.template-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone.template.errors :as print-errs]
            [com.yetanalytics.persephone.template :as tv]
            #?(:clj
               [com.yetanalytics.persephone-test.test-utils :as test-u]
               :cljs
               [com.yetanalytics.persephone-test.test-utils :as test-u :refer [slurp]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Template Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ex-statement-1
  (test-u/json->edn (slurp "test-resources/sample_statements/adl_1.json")))
(def ex-statement-2
  (test-u/json->edn (slurp "test-resources/sample_statements/adl_2.json")))
(def ex-statement-3
  (test-u/json->edn (slurp "test-resources/sample_statements/adl_3.json")))
(def ex-statement-4
  (test-u/json->edn (slurp "test-resources/sample_statements/adl_4.json")))

;; Statement that conforms to ex-template
(def ex-statement-0
  {"id"          "281398bd-152d-4cf5-a73c-5b7afeb8c584"
   "actor"       {"objectType" "Group"
                  "name"       "Yet Analytics Dev Team"
                  "mbox"       "mailto:email@yetanalytics.io"
                  "member"     [{"name" "Will Hoyt"
                                 "mbox" "mailto:whoyt@yetanalytics.io"}
                                {"name" "Milt Reder"
                                 "mbox" "mailto:mreder@yetanalytics.io"}
                                {"name" "John Newman"
                                 "mbox" "mailto:jnewman@yetanalytics.io"}
                                {"name" "Henk Reder"
                                 "mbox" "mailto:hreder@yetanalytics.io"}
                                {"name" "Erika Lee"
                                 "mbox" "mailto:elee@yetanalytics.io"}
                                {"name" "Boris Boiko"
                                 "mbox" "mailto:bboiko@yetanalytics.io"}]}
   "verb"        {"id" "http://foo.org/verb"}
   "object"      {"id"          "http://www.example.com/object"
                  "objectType" "Activity"
                  "definition" {"type" "http://foo.org/oat"}}
   "result"     {"score" {"raw" 9001}} ; It's over 9000!
   "context"    {"contextActivities"
                 {"parent"   [{"id"         "http://foo.org/ca1"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/cpat1"}}
                              {"id"         "http://foo.org/ca2"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/cpat2"}}]
                  "grouping" [{"id"         "http://foo.org/ca3"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/cgat1"}}
                              {"id"         "http://foo.org/ca4"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/cgat2"}}]
                  "category" [{"id"         "http://foo.org/ca5"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/ccat1"}}
                              {"id"         "http://foo.org/ca6"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/ccat2"}}]
                  "other"    [{"id"         "http://foo.org/ca7"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/coat1"}}
                              {"id"         "http://foo.org/ca8"
                               "objectType" "Activity"
                               "definition" {"type" "http://foo.org/coat2"}}]}}
   "attachments" [{"usageType"   "http://foo.org/aut1"
                   "display"     {"en" "Attachment 1"}
                   "length"      100
                   "contentType" "application/octet-stream"
                   "sha2"        "SomeSHA2"}
                  {"usageType" "http://foo.org/aut2"
                   "display"     {"en" "Attachment 2"}
                   "length"      200
                   "contentType" "application/octet-stream"
                   "sha2"        "SomeSHA3"}]})

;; Example template
;; Note how while Statement keys are strings, Statement Template keys are
;; keywords.
(def ex-template
  {:id                          "http://foo.org/example/template"
   :type                        "StatementTemplate"
   :inScheme                    "http://foo.org/profile/v1"
   :prefLabel                   {:en "Example Template"}
   :definition                  {:en "This template is an example template for test cases."}
   ;; Determining Properties
   :verb                        "http://foo.org/verb"
   :objectActivityType          "http://foo.org/oat"
   :contextGroupingActivityType ["http://foo.org/cgat1" "http://foo.org/cgat2"]
   :contextParentActivityType   ["http://foo.org/cpat1" "http://foo.org/cpat2"]
   :contextOtherActivityType    ["http://foo.org/coat1" "http://foo.org/coat2"]
   :contextCategoryActivityType ["http://foo.org/ccat1" "http://foo.org/ccat2"]
   :attachmentUsageType         ["http://foo.org/aut1" "http://foo.org/aut2"]
   ;; Statement Reference Templates
   ;; Because our activity must be of Activity type, we can't have a object-
   ;; StatementRefTemplate
   :contextStatementRefTemplate ["http://foo.org/templates/template3"
                                 "http://foo.org/templates/template4"]
   ;; Rules
   :rules [{:location "$.actor.objectType"
            :presence "included"}
           {:location "$.actor.member[*].name"
            :presence "included"
            ;; Developers (and friends) only
            :any      ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder"
                       "Erika Lee" "Boris Boiko"]
            :none     ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony"
                       "Jeremy Gardner"]}
           {:location "$.actor"
            :selector "$.mbox"
            :presence "included"}
           {:location "$.actor"
            :selector "$.mbox_sha1sum"
            :presence "excluded"}
           {:location "$.object.objectType | $.context.contextActivities.parent[*].objectType | $.context.contextActivities.grouping[*].objectType | $.context.contextActivities.category[*].objectType | $.context.contextActivities.other[*].objectType"
            :presence "included"
            :all      ["Activity"]}]})

(def base-template
  {:id         "http://foo.org/example/template"
   :type       "StatementTemplate"
   :inScheme   "http://foo.org/profile/v1"
   :prefLabel  {:en "Example Template"}
   :definition {:en "This template is an example template for test cases."}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest find-values-test
  (testing "find-values: given a statement, location and selector, find the
          values evaluated by the JSONPath strings."
    (is (= ["http://foo.org/verb"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.verb.id"))))
    (is (= ["Group"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor.objectType"))))
    (is (= ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor.member[*].name"))))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor.mbox"))))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor.mbox")
                           nil)))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor")
                           (tv/parse-selector "$.mbox"))))
    (is (= [nil]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor")
                           (tv/parse-selector "$.mbox_sha1sum"))))
    (is (= ["mailto:email@yetanalytics.io" nil]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor.mbox | $.actor.mbox_sha1sum"))))
    (is (= ["mailto:email@yetanalytics.io" nil]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.actor")
                           (tv/parse-selector "$.mbox | $.mbox_sha1sum"))))
    (is (= ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator
                            "$.object.objectType 
                            | $.context.contextActivities.parent[*].objectType 
                            | $.context.contextActivities.grouping[*].objectType
                            | $.context.contextActivities.category[*].objectType
                            | $.context.contextActivities.other[*].objectType"))))
    (is (= ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity"]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.context.contextActivities")
                           (tv/parse-selector
                            "$.parent[*].objectType 
                           | $.grouping[*].objectType
                           | $.category[*].objectType
                           | $.other[*].objectType"))))
    (is (= 4 (count (tv/find-values
                     ex-statement-0
                     (tv/parse-locator "$.context.contextActivities.parent 
                                | $.context.contextActivities.grouping
                                | $.context.contextActivities.category
                                | $.context.contextActivities.other")))))
    (is (vector? (first (tv/find-values
                         ex-statement-0
                         (tv/parse-locator "$.context.contextActivities.parent 
                                 | $.context.contextActivities.grouping
                                 | $.context.contextActivities.category
                                 | $.context.contextActivities.other")))))
    (is (= [nil nil nil nil]
           (tv/find-values
            ex-statement-0
            (tv/parse-locator "$.context.contextActivities.parent.fi
                          | $.context.contextActivities.grouping.fy
                          | $.context.contextActivities.category.fo
                          | $.context.contextActivities.other.fum"))))
    (is (= #{"http://foo.org/oat"
             "http://foo.org/cpat1" "http://foo.org/cpat2"
             "http://foo.org/cgat1" "http://foo.org/cgat2"
             "http://foo.org/ccat1" "http://foo.org/ccat2"
             "http://foo.org/coat1" "http://foo.org/coat2"}
           (set (tv/find-values ex-statement-0
                                (tv/parse-locator "$..type")))))
    (is (= [9001]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$.result.score.raw"))))
    (is (= [9001]
           (tv/find-values ex-statement-0
                           (tv/parse-locator "$..raw"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validator/Predicate creation tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This set of tests is a pred on the Statement Template rule logic.
;; NOTE: validator-fn returns nil on success, error data on failure.
;;       The :pred field is the name of the predicate that failed.
(deftest create-rule-validator-test
  (testing "create-rule-validator function: Given a rule, create a validation
            function that accepts Statements."
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "included"
                                                  :any ["foo" "baz"]})]
      ;; MUST include at least one matchable value if presence is included
      (is (= :any-matchable? (:pred (validator-fn []))))
      (is (= :any-matchable? (:pred (validator-fn [nil nil]))))
      ;; MUST NOT include any unmatchable values if presence is included
      (is (= :all-matchable? (:pred (validator-fn [nil "foo" nil]))))
      ;; MUST, if any is provided, include at least one value in any as one of the matchable values
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (nil? (validator-fn ["foo" "bar"])))
      (is (= :some-any-values? (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "included"
                                                  :all ["foo" "baz"]})]
      ;; MUST include at least one matchable value if presence is included
      (is (= :any-matchable? (:pred (validator-fn []))))
      (is (= :any-matchable? (:pred (validator-fn [nil nil]))))
      ;; MUST NOT include any unmatchable values if presence is included
      (is (= :all-matchable? (:pred (validator-fn [nil "foo" nil]))))
      ;; MUST, if all is provided, only include values in all as matchable values
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (= :only-all-values? (:pred (validator-fn ["foo" "bar"]))))
      (is (= :only-all-values? (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "included"
                                                  :none ["foo"]})]
      ;; MUST include at least one matchable value if presence is included
      (is (= :any-matchable? (:pred (validator-fn []))))
      (is (= :any-matchable? (:pred (validator-fn [nil nil]))))
      ;; MUST NOT include any unmatchable values if presence is included
      (is (= :all-matchable? (:pred (validator-fn [nil "foo" nil]))))
      (is (= :all-matchable? (:pred (validator-fn [nil "bar" nil]))))
      ;; MUST NOT, if none is provided, include any values in none as matchable values
      (is (nil? (validator-fn ["bar"])))
      (is (= :no-none-values? (:pred (validator-fn ["foo" "bar"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "excluded"})]
      ;; MUST NOT include any matchable values if presence is excluded
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      (is (= :none-matchable? (:pred (validator-fn [nil "foo" nil])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "recommended"
                                                  :any ["foo" "baz"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      ;; MUST, if any is provided, include at least one value in any as one of the matchable values
      (is (nil? (validator-fn [nil "foo" nil])))
      (is (nil? (validator-fn ["foo" "bar" "baz"])))
      (is (= :some-any-values? (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "recommended"
                                                  :all ["foo" "baz"]})]
      (is (nil? (:pred (validator-fn []))))
      (is (nil? (:pred (validator-fn [nil nil]))))
      ;; MUST NOT, if all is provided, include any unmatchable values
      (is (= :no-unmatch-vals? (:pred (validator-fn [nil "foo" nil]))))
      ;; MUST, if all is provided, only include values in all as matchable values
      (is (nil? (validator-fn ["foo"])))
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (= :only-all-values? (:pred (validator-fn ["foo" "bar"]))))
      (is (= :only-all-values? (:pred (validator-fn ["foo" "bar" "baz"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "recommended"
                                                  :none ["foo"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      (is (nil? (validator-fn [nil "bar" nil])))
      ;; MUST NOT, if none is provided, include any values in none as matchable values
      (is (nil? (validator-fn ["bar" "baz" "qux"])))
      (is (= :no-none-values? (:pred (validator-fn ["foo"]))))
      (is (= :no-none-values? (:pred (validator-fn ["foo" "bar"]))))
      (is (= :no-none-values? (:pred (validator-fn [nil "foo" nil])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :any ["foo" "baz"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      ;; MUST, if any is provided, include at least one value in any as one of the matchable values
      (is (nil? (validator-fn [nil "foo" nil])))
      (is (nil? (validator-fn ["foo" "bar" "baz"])))
      (is (= :some-any-values? (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :all ["foo" "baz"]})]
      (is (nil? (:pred (validator-fn []))))
      (is (nil? (:pred (validator-fn [nil nil]))))
      ;; MUST NOT, if all is provided, include any unmatchable values
      (is (= :no-unmatch-vals? (:pred (validator-fn [nil "foo" nil]))))
      ;; MUST, if all is provided, only include values in all as matchable values
      (is (nil? (validator-fn ["foo"])))
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (= :only-all-values? (:pred (validator-fn ["foo" "bar"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :none ["foo"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      (is (nil? (validator-fn [nil "bar" nil])))
      ;; MUST NOT, if none is provided, include any values in none as matchable values
      (is (nil? (validator-fn ["bar" "baz" "qux"])))
      (is (= :no-none-values? (:pred (validator-fn ["foo"]))))
      (is (= :no-none-values? (:pred (validator-fn ["foo" "bar"]))))
      (is (= :no-none-values? (:pred (validator-fn [nil "foo" nil])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :match-vals ["foo" "bar"]
                                                  :det-prop "X"})]
      ;; MUST include all the Determining Properties [values] in the Statement Template
      (is (nil? (validator-fn ["foo" "bar"])))
      (is (nil? (validator-fn ["foo" "bar" "baz"])))
      (is (nil? (validator-fn ["baz" "bar" "qux ""foo"])))
      (is (nil? (validator-fn [nil "foo" nil "bar" nil])))
      (is (= :every-val-present? (:pred (validator-fn ["foo"]))))
      (is (= :every-val-present? (:pred (validator-fn ["baz" "foo"]))))
      (is (= :every-val-present? (:pred (validator-fn [nil "foo" nil]))))
      (is (= :any-matchable? (:pred (validator-fn [nil])))))))

(def create-template-validator
  (fn [template]
    (tv/create-template-validator template nil)))

(def create-template-predicate
  (fn [template]
    (tv/create-template-predicate template nil)))

(deftest create-template-validator-test
  (testing "create-template-validator function: Given a template, create a
            validator function that accepts Statements."
    (is (nil? ((create-template-validator ex-template) ex-statement-0)))
    (is (nil? ((create-template-validator
                (merge
                 base-template
                 {:verb "http://example.com/xapi/verbs#sent-a-statement"}))
               ex-statement-1)))
    (is (nil? ((create-template-validator
                (merge
                 base-template
                 {:verb "http://adlnet.gov/expapi/verbs/attempted"}))
               ex-statement-2)))
    (is (nil? ((create-template-validator
                (merge
                 base-template
                 {:verb "http://adlnet.gov/expapi/verbs/attended"
                  :objectActivityType "http://adlnet.gov/expapi/activities/meeting"
                  :contextCategoryActivityType
                  ["http://example.com/expapi/activities/meetingcategory"]}))
               ex-statement-3)))
    (is (nil? ((create-template-validator
                (merge
                 base-template
                 {:verb "http://adlnet.gov/expapi/verbs/experienced"}))
               ex-statement-4)))
    (is (nil? ((create-template-validator base-template)
               ex-statement-4)))
    (is (some? ((create-template-validator
                 (merge
                  base-template
                  {:verb "http://adlnet.gov/expapi/verbs/experienced-not"}))
               ex-statement-4)))))

(deftest create-template-predicate-test
  (testing "create-template-predicate function: Given a template, create a
            predicate that accepts Statements."
    (is ((create-template-predicate ex-template) ex-statement-0))
    (is ((create-template-predicate
          (merge base-template
                 {:verb "http://example.com/xapi/verbs#sent-a-statement"}))
         ex-statement-1))
    (is ((create-template-predicate
          (merge base-template
                 {:verb "http://adlnet.gov/expapi/verbs/attempted"}))
         ex-statement-2))
    (is ((create-template-predicate
          (merge base-template
                 {:verb "http://adlnet.gov/expapi/verbs/attended"
                  :objectActivityType "http://adlnet.gov/expapi/activities/meeting"
                  :contextCategoryActivityType
                  ["http://example.com/expapi/activities/meetingcategory"]}))
         ex-statement-3))
    (is ((create-template-predicate
          (merge base-template
                 {:verb "http://adlnet.gov/expapi/verbs/experienced"}))
         ex-statement-4))
    (is ((create-template-predicate base-template)
         ex-statement-4))
    (is (not ((create-template-predicate
               (merge base-template
                      {:verb "http://adlnet.gov/expapi/verbs/experienced-not"}))
              ex-statement-4)))))

(deftest create-template-validator-test-2
  (testing "validate-statement function"
    (is (= [{:pred :every-val-present?
             :vals ["http://example.com/xapi/verbs#sent-a-statement"]
             :prop {:location "$.verb.id"
                    :match-vals ["http://foo.org/verb"]
                    :det-prop "Verb"}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil]
             :prop {:location "$.object.definition.type"
                    :match-vals ["http://foo.org/oat"]
                    :det-prop "objectActivityType"}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil]
             :prop {:location "$.context.contextActivities.parent[*].definition.type"
                    :match-vals ["http://foo.org/cpat1" "http://foo.org/cpat2"]
                    :det-prop "contextParentActivityType"}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil]
             :prop {:location "$.context.contextActivities.grouping[*].definition.type"
                    :match-vals ["http://foo.org/cgat1" "http://foo.org/cgat2"]
                    :det-prop "contextGroupingActivityType"}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil]
             :prop {:location "$.context.contextActivities.category[*].definition.type"
                    :match-vals ["http://foo.org/ccat1" "http://foo.org/ccat2"]
                    :det-prop "contextCategoryActivityType"}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil]
             :prop {:location "$.context.contextActivities.other[*].definition.type"
                    :match-vals ["http://foo.org/coat1" "http://foo.org/coat2"]
                    :det-prop "contextOtherActivityType"}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil]
             :prop {:location "$.attachments[*].usageType"
                    :match-vals ["http://foo.org/aut1" "http://foo.org/aut2"]
                    :det-prop "attachmentUsageType"}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil]
             :rule {:location "$.actor.member[*].name"
                    :presence "included"
                    :any ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]
                    :none ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony" "Jeremy Gardner"]}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}
            {:pred :any-matchable?
             :vals [nil nil nil nil nil]
             :rule {:location
                    "$.object.objectType | $.context.contextActivities.parent[*].objectType | $.context.contextActivities.grouping[*].objectType | $.context.contextActivities.category[*].objectType | $.context.contextActivities.other[*].objectType"
                    :presence "included"
                    :all ["Activity"]}
             :temp (:id ex-template)
             :stmt (get ex-statement-1 "id")}]
           ((create-template-validator ex-template) ex-statement-1)))))

(deftest create-template-predicate-test-2
  (testing "valid-statement? function"
    (is (not ((create-template-predicate ex-template) ex-statement-1)))))

(deftest print-error-test
  (testing "printing an error message using the print-error fn"
    (is (= (str "----- Statement Validation Failure -----\n"
                "Template ID:  http://foo.org/example/template\n"
                "Statement ID: fd41c918-b88b-4b20-a0a5-a4c32391aaa0\n"
                "\n"
                "Template Verb property was not matched.\n"
                " template Verb:\n"
                "   http://foo.org/verb\n"
                " statement Verb:\n"
                "   http://example.com/xapi/verbs#sent-a-statement\n"
                "\n"
                "Template objectActivityType property was not matched.\n"
                " template objectActivityType:\n"
                "   http://foo.org/oat\n"
                " statement objectActivityType:\n"
                "   no values found at location\n"
                "\n"
                "Template contextParentActivityType property was not matched.\n"
                " template contextParentActivityType:\n"
                "   http://foo.org/cpat1\n"
                "   http://foo.org/cpat2\n"
                " statement contextParentActivityType:\n"
                "   no values found at location\n"
                "\n"
                "Template contextGroupingActivityType property was not matched.\n"
                " template contextGroupingActivityType:\n"
                "   http://foo.org/cgat1\n"
                "   http://foo.org/cgat2\n"
                " statement contextGroupingActivityType:\n"
                "   no values found at location\n"
                "\n"
                "Template contextCategoryActivityType property was not matched.\n"
                " template contextCategoryActivityType:\n"
                "   http://foo.org/ccat1\n"
                "   http://foo.org/ccat2\n"
                " statement contextCategoryActivityType:\n"
                "   no values found at location\n"
                "\n"
                "Template contextOtherActivityType property was not matched.\n"
                " template contextOtherActivityType:\n"
                "   http://foo.org/coat1\n"
                "   http://foo.org/coat2\n"
                " statement contextOtherActivityType:\n" "   no values found at location\n"
                "\n"
                "Template attachmentUsageType property was not matched.\n"
                " template attachmentUsageType:\n"
                "   http://foo.org/aut1\n"
                "   http://foo.org/aut2\n"
                " statement attachmentUsageType:\n"
                "   no values found at location\n"
                "\n"
                "Template rule was not followed:\n"
                "  {:location \"$.actor.member[*].name\",\n"
                "   :presence \"included\",\n"
                "   :any [\"Will Hoyt\" \"Milt Reder\" \"John Newman\" \"Henk Reder\" \"Erika Lee\" \"Boris Boiko\"],\n"
                "   :none [\"Shelly Blake-Plock\" \"Brit Keller\" \"Mike Anthony\" \"Jeremy Gardner\"]}\n"
                " failed: at least one matchable value must exist\n"
                " statement values:\n"
                "   no values found at location\n"
                "\n"
                "Template rule was not followed:\n"
                "  {:location \"$.object.objectType | $.context.contextActivities.parent[*].objectType | $.context.contextActivities.grouping[*].objectType | $.context.contextActivities.category[*].objectType | $.context.contextActivities.other[*].objectType\",\n"
                "   :presence \"included\",\n"
                "   :all [\"Activity\"]}\n"
                " failed: at least one matchable value must exist\n"
                " statement values:\n"
                "   no values found at location\n"
                "\n"
                "-----------------------------\n"
                "Total errors found: 9\n\n")
           (with-out-str
             (print-errs/print-errors
              ((create-template-validator ex-template) ex-statement-1)))))))

(deftest statement-ref-templates-test
  (let [;; Templates
        sref-template-id-0 "http://example.org/stmt-ref-template-0"
        sref-template-id-1 "http://example.org/stmt-ref-template-1"
        sref-template-id-2 "http://example.org/stmt-ref-template-2"
        sref-template-id-3 "http://example.org/stmt-ref-template-3"
        sref-template-id-4 "http://example.org/stmt-ref-template-4"
        sref-template-id-5 "http://example.org/stmt-ref-template-5"
        sref-template-id-a "http://example.org/stmt-template-a"
        sref-template-id-b "http://example.org/stmt-template-b"
        sref-template-id-c "http://example.org/stmt-template-c"
        id-template-map
        {sref-template-id-0 (merge
                             base-template
                             {:id sref-template-id-0
                              :objectStatementRefTemplate
                              [sref-template-id-1
                               sref-template-id-a]
                              :contextStatementRefTemplate
                              [sref-template-id-a
                               sref-template-id-1]})
         sref-template-id-1 (merge
                             base-template
                             {:id sref-template-id-1
                              :objectStatementRefTemplate
                              [sref-template-id-2]
                              :contextStatementRefTemplate
                              [sref-template-id-2]})
         sref-template-id-2 (merge
                             base-template
                             {:id sref-template-id-2
                              :objectStatementRefTemplate
                              [sref-template-id-3]
                              :contextStatementRefTemplate
                              [sref-template-id-3]})
         sref-template-id-3 (merge
                             base-template
                             {:id sref-template-id-3
                              :objectStatementRefTemplate
                              [sref-template-id-a]
                              :contextStatementRefTemplate
                              [sref-template-id-a]})
         sref-template-id-4 (merge
                             base-template
                             {:id sref-template-id-4
                              :objectStatementRefTemplate
                              [sref-template-id-b
                               sref-template-id-5]
                              :contextStatementRefTemplate
                              [sref-template-id-5]})
         sref-template-id-5 (merge
                             base-template
                             {:id sref-template-id-5
                              :verb "http://foo.org/verb-bar"
                              :contextStatementRefTemplate
                              [sref-template-id-c]})
         sref-template-id-a (merge base-template
                                   {:id   sref-template-id-a
                                    :verb "http://foo.org/verb"})
         sref-template-id-b (merge base-template
                                   {:id   sref-template-id-b
                                    :verb "http://foo.org/verb-2"})
         sref-template-id-c (merge base-template
                                   {:id   sref-template-id-c
                                    :verb "http://foo.org/verb-3"})}
        ;; Statements
        stmt-id-0 "00000000-0000-4000-a000-000000000000"
        stmt-id-1 "00000000-0000-4000-a000-000000000001"
        stmt-id-2 "00000000-0000-4000-a000-000000000002"
        stmt-id-3 "00000000-0000-4000-a000-000000000003"
        stmt-id-4 "00000000-0000-4000-a000-000000000004"
        stmt-map
        {stmt-id-0 (-> ex-statement-0
                       (assoc "id" stmt-id-0)
                       (assoc "object" {"id" stmt-id-1
                                        "objectType" "StatementRef"})
                       (assoc-in ["context" "statement"]
                                 {"id" stmt-id-1
                                  "objectType" "StatementRef"}))
         stmt-id-1 (-> ex-statement-0
                       (assoc "id" stmt-id-1)
                       (assoc "object" {"id" stmt-id-2
                                        "objectType" "StatementRef"})
                       (assoc-in ["context" "statement"]
                                 {"id" stmt-id-2
                                  "objectType" "StatementRef"}))
         stmt-id-2 (-> ex-statement-0
                       (assoc "id" stmt-id-2)
                       (assoc "object" {"id" stmt-id-3
                                        "objectType" "StatementRef"})
                       (assoc-in ["context" "statement"]
                                 {"id" stmt-id-3
                                  "objectType" "StatementRef"}))
         stmt-id-3 (-> ex-statement-0
                       (assoc "id" stmt-id-3)
                       (assoc "object" {"id" stmt-id-4
                                        "objectType" "StatementRef"})
                       (assoc-in ["context" "statement"]
                                 {"id" stmt-id-4
                                  "objectType" "StatementRef"}))
         stmt-id-4 (-> ex-statement-0
                       (assoc "id" stmt-id-4))}
        make-validator
        (fn [id]
          (tv/create-template-validator
           (get id-template-map id)
           {:get-template-fn  id-template-map
            :get-statement-fn stmt-map}))
        make-predicate
        (fn [id]
          (tv/create-template-predicate
           (get id-template-map id)
           {:get-template-fn  id-template-map
            :get-statement-fn stmt-map}))]
    (testing "statement ref template validators - valid"
      (is (nil? ((make-validator sref-template-id-0)
                 (get stmt-map stmt-id-0))))
      (is (nil? ((make-validator sref-template-id-1)
                 (get stmt-map stmt-id-1))))
      (is (nil? ((make-validator sref-template-id-2)
                 (get stmt-map stmt-id-2))))
      (is (nil? ((make-validator sref-template-id-3)
                 (get stmt-map stmt-id-3)))))
    (testing "statement ref template predicates - valid"
      (is ((make-predicate sref-template-id-0)
           (get stmt-map stmt-id-0)))
      (is ((make-predicate sref-template-id-1)
           (get stmt-map stmt-id-1)))
      (is ((make-predicate sref-template-id-2)
           (get stmt-map stmt-id-2)))
      (is ((make-predicate sref-template-id-3)
           (get stmt-map stmt-id-3))))
    (testing "statement ref template validator - invalid"
      (let [stmt (-> ex-statement-0
                      (dissoc "object")
                      (dissoc "context"))]
        (is (= [{:pred :statement-ref?
                 :vals [stmt]
                 :sref {:location     "$.object"
                        :sref-failure :sref-not-found}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}
                {:pred :statement-ref?
                 :vals [stmt]
                 :sref {:location     "$.context.statement"
                        :sref-failure :sref-not-found}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}]
               ((make-validator sref-template-id-3) stmt))))
      (let [stmt (-> ex-statement-0
                     (assoc-in ["object"]
                               {"objectType" "Foo" "id" "bar"})
                     (assoc-in ["context" "statement"]
                               {"objectType" "Foo" "id" "bar"}))]
        (is (= [{:pred :statement-ref?
                 :vals [(get-in stmt ["object"])]
                 :sref {:location     "$.object"
                        :sref-failure :sref-object-type-invalid}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}
                {:pred :statement-ref?
                 :vals [(get-in stmt ["context" "statement"])]
                 :sref {:location     "$.context.statement"
                        :sref-failure :sref-object-type-invalid}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}]
               ((make-validator sref-template-id-3) stmt))))
      (let [stmt (-> ex-statement-0
                     (assoc-in ["object"]
                               {"objectType" "StatementRef"})
                     (assoc-in ["context" "statement"]
                               {"objectType" "StatementRef"}))]
        (is (= [{:pred :statement-ref?
                 :vals [(get-in stmt ["object"])]
                 :sref {:location     "$.object"
                        :sref-failure :sref-id-missing}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}
                {:pred :statement-ref?
                 :vals [(get-in stmt ["context" "statement"])]
                 :sref {:location     "$.context.statement"
                        :sref-failure :sref-id-missing}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}]
               ((make-validator sref-template-id-3) stmt))))
      (let [stmt (-> ex-statement-0
                     (assoc-in ["object"]
                               {"objectType" "StatementRef" "id" "baz"})
                     (assoc-in ["context" "statement"]
                               {"objectType" "StatementRef" "id" "qux"}))]
        (is (= [{:pred :statement-ref?
                 :vals [(get-in stmt ["object" "id"])]
                 :sref {:location     "$.object"
                        :sref-failure :sref-stmt-not-found}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}
                {:pred :statement-ref?
                 :vals [(get-in stmt ["context" "statement" "id"])]
                 :sref {:location     "$.context.statement"
                        :sref-failure :sref-stmt-not-found}
                 :temp sref-template-id-3
                 :stmt (get stmt "id")}]
               ((make-validator sref-template-id-3) stmt))))
      (is (= [{:pred :every-val-present?
               :vals ["http://foo.org/verb"]
               :prop {:location   "$.verb.id"
                      :match-vals ["http://foo.org/verb-bar"]
                      :det-prop   "Verb"}
               :temp sref-template-id-5
               :stmt stmt-id-4}
              {:pred :statement-ref?
               :vals [(get stmt-map stmt-id-4)]
               :sref {:location     "$.context.statement"
                      :sref-failure :sref-not-found}
               :temp sref-template-id-5
               :stmt stmt-id-4}]
             ((make-validator sref-template-id-5)
              (get stmt-map stmt-id-4))))
      (is (= [;; stmt-template-b
              {:pred :every-val-present?
               :vals ["http://foo.org/verb"]
               :prop {:location   "$.verb.id"
                      :match-vals ["http://foo.org/verb-2"]
                      :det-prop   "Verb"}
               :temp sref-template-id-b
               :stmt stmt-id-4}
              ;; stmt-ref-template-5 - object
              {:pred :every-val-present?
               :vals ["http://foo.org/verb"]
               :prop {:location   "$.verb.id"
                      :match-vals ["http://foo.org/verb-bar"]
                      :det-prop   "Verb"}
               :temp sref-template-id-5
               :stmt stmt-id-4}
              {:pred :statement-ref?
               :vals [(get stmt-map stmt-id-4)]
               :sref {:location     "$.context.statement"
                      :sref-failure :sref-not-found}
               :temp sref-template-id-5
               :stmt stmt-id-4}
              ;; stmt-ref-template-5 - context
              {:pred :every-val-present?
               :vals ["http://foo.org/verb"]
               :prop {:location   "$.verb.id"
                      :match-vals ["http://foo.org/verb-bar"]
                      :det-prop   "Verb"}
               :temp sref-template-id-5
               :stmt stmt-id-3}
              {:pred :every-val-present?
               :vals ["http://foo.org/verb"]
               :prop {:location   "$.verb.id"
                      :match-vals ["http://foo.org/verb-3"]
                      :det-prop   "Verb"}
               :temp sref-template-id-c
               :stmt stmt-id-4}]
             ((make-validator sref-template-id-4)
              (assoc-in (get stmt-map stmt-id-3)
                        ["context" "statement"]
                        {"objectType" "StatementRef"
                         "id"         stmt-id-3})))))
    (testing "statement ref template predicate - invalid"
        (is (not ((make-predicate sref-template-id-4)
                  (get stmt-map stmt-id-3)))))))
