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
   :rules [{:location "$.actor.objectType" :presence "included"}
           {:location "$.actor.member[*].name" :presence "included"
            ;; Developers (and friends) only
            :any ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder"
                  "Erika Lee" "Boris Boiko"]
            :none ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony"
                   "Jeremy Gardner"]}
           {:location "$.actor"
            :selector "$.mbox"
            :presence "included"}
           {:location "$.actor"
            :selector "$.mbox_sha1sum"
            :presence "excluded"}
           {:location
            "$.object.objectType | $.context.contextActivities.parent[*].objectType | $.context.contextActivities.grouping[*].objectType | $.context.contextActivities.category[*].objectType | $.context.contextActivities.other[*].objectType"
            :presence "included"
            :all ["Activity"]}]})

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
            :objectType "Activity"
            :definition {:type "http://foo.org/oat"}}
   :result {:score {:raw 9001}} ;; It's over 9000!
   :context {:contextActivities
             {:parent [{:id "http://foo.org/ca1" :objectType "Activity"
                        :definition {:type "http://foo.org/cpat1"}}
                       {:id "http://foo.org/ca2" :objectType "Activity"
                        :definition {:type "http://foo.org/cpat2"}}]
              :grouping [{:id "http://foo.org/ca3" :objectType "Activity"
                          :definition {:type "http://foo.org/cgat1"}}
                         {:id "http://foo.org/ca4" :objectType "Activity"
                          :definition {:type "http://foo.org/cgat2"}}]
              :category [{:id "http://foo.org/ca5" :objectType "Activity"
                          :definition {:type "http://foo.org/ccat1"}}
                         {:id "http://foo.org/ca6" :objectType "Activity"
                          :definition {:type "http://foo.org/ccat2"}}]
              :other [{:id "http://foo.org/ca7" :objectType "Activity"
                       :definition {:type "http://foo.org/coat1"}}
                      {:id "http://foo.org/ca8" :objectType "Activity"
                       :definition {:type "http://foo.org/coat2"}}]}}
   :attachments [{:usageType "http://foo.org/aut1"}
                 {:usageType "http://foo.org/aut2"}]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util function tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest all-matchable?-test
  (testing "all-matchable? function: return true iff every value is matchable."
    (is (tv/all-matchable? ["foo" "bar" "stan loona"]))
    (is (not (tv/all-matchable? [])))
    (is (not (tv/all-matchable? [nil nil nil])))
    (is (not (tv/all-matchable? [nil nil "what the pineapple"])))))

(deftest none-matchable?-test
  (testing "none-matchable? function: return true iff no value is matchable."
    (is (tv/none-matchable? []))
    (is (tv/none-matchable? [nil nil nil]))
    (is (not (tv/none-matchable? ["foo" "bar"])))
    (is (not (tv/none-matchable? [nil nil "what the pineapple"])))))

(deftest any-matchable?-test
  (testing "any-matchable? function: return true if some values are matchable."
    (is (tv/any-matchable? [nil nil "still good"]))
    (is (tv/any-matchable? ["foo" "bar" "all good"]))
    (is (not (tv/any-matchable? [])))
    (is (not (tv/any-matchable? [nil nil nil])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicate tests.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; name-values = ["Andrew Downes" "Toby "Nichols" "Ena Hills"]
(def name-values (-> ex-statement-3 :actor :member (util/value-map :name)))

(deftest any-values-test
  (testing "any-values function: values MUST include at least one value that is
           given by 'any', ie. the collections need to intersect."
    (is (tv/any-values ["Andrew Downes" "Toby Nichols"] name-values))
    (is (tv/any-values ["Andrew Downes" "Will Hoyt"] name-values))
    (is (not (tv/any-values ["Will Hoyt" "Milt Reder"] name-values)))
    (is (not (tv/any-values [] name-values)))
    ;; any-values is undefined if there are no matchable values
    (is (not (tv/any-values [] [])))
    (is (not (tv/any-values ["Andrew Downes"] [nil])))
    (is (tv/any-values [nil] [nil]))))

(deftest all-values-test
  (testing "all-values function: values MUST all be from the values given by
           'all'."
    (is (tv/all-values ["Andrew Downes" "Toby Nichols" "Ena Hills"]
                       name-values))
    ;; Superset is okay
    (is (tv/all-values ["Andrew Downes" "Toby Nichols" "Ena Hills" "Will Hoyt"]
                       name-values))
    (is (not (tv/all-values ["Andrew Downes" "Toby Nichols"] name-values)))
    (is (not (tv/all-values [] name-values)))
    ;; MUST NOT include any unmatchable values 
    (is (not (tv/all-values ["Andrew Downes" "Toby Nichols" "Ena Hills"] [])))
    (is (not (tv/all-values ["Andrew Downes"] [nil nil])))
    (is (not (tv/all-values [nil] [nil nil])))))

(deftest none-values-test
  (testing "none-values function: values MUST NOT be included in the set given
           by 'none'."
    (is (tv/none-values ["Will Hoyt" "Milt Reder"] name-values))
    (is (not (tv/none-values ["Andrew Downes"] name-values)))
    (is (not (tv/none-values ["Will Hoyt" "Milt Reder" "Ena Hills"]
                             name-values)))
    (is (tv/none-values ["Will Hoyt" "Milt Reder"] []))
    (is (tv/none-values ["Will Hoyt" "Milt Reder"] [nil]))
    (is (not (tv/none-values [nil] [nil])))
    ;; If there is nothing to exclude, we should be okay
    (is (tv/none-values [] name-values))
    (is (tv/none-values [] []))))

;; Predicates for our next tests
(def included-spec (tv/create-included-spec {:presence "included"
                                             :any ["Andrew Downes"]}))
(def excluded-spec (tv/create-excluded-spec {:presence "excluded"}))
(def recommended-spec (tv/create-default-spec {:presence "recommended"
                                               :any ["Andrew Downes"]}))

(deftest create-included-spec-test
  (testing "create-included-spec function: create a predicate when presence is
           'included'. Values MUST have at least one matchable value (and no
           unmatchable values) and MUST follow any/all/none reqs."
    (is (= (s/describe included-spec) '(and all-matchable?
                                            rule-any? rule-all? rule-none?)))
    (is (s/valid? included-spec name-values))
    (is (not (s/valid? included-spec ["Will Hoyt"])))
    (is (not (s/valid? included-spec [])))
    (is (not (s/valid? included-spec ["Andrew Downes" nil])))))

(deftest create-excluded-spec-test
  (testing "create-excluded-spec function: create a predicate when presence is
           'excluded.' There MUST NOT be any matchable values."
    (is (= (s/describe excluded-spec) '(and none-matchable?)))
    (is (s/valid? excluded-spec []))
    (is (s/valid? excluded-spec [nil nil]))
    (is (not (s/valid? excluded-spec name-values)))
    (is (not (s/valid? excluded-spec (conj name-values nil))))))

;; The test for when presence is missing is pretty much the same. 
(deftest create-recommended-spec-test
  (testing "create-recommended-spec function: create a predicate when presence
           is 'recommended'. MUST follow any/all/none reqs."
    (is (= (s/describe recommended-spec))
        '(or :missing none-matchable
             :not-missing (and any-matchable? rule-any? rule-all? rule-none?)))
    (is (s/valid? recommended-spec name-values))
    (is (not (s/valid? recommended-spec ["Will Hoyt"])))))

(deftest create-rule-spec-test
  (testing "create-rule-spec function: Given a rule, create a spec."
    (is (= (s/describe included-spec)
           (s/describe (tv/create-rule-spec {:presence "included"
                                             :any ["Andrew Downes"]}))))
    (is (= (s/describe excluded-spec)
           (s/describe (tv/create-rule-spec {:presence "excluded"}))))
    (is (= (s/describe recommended-spec)
           (s/describe (tv/create-rule-spec {:presence "recommended"
                                             :any ["Andrew Downes"]}))))
    (is (= (s/describe recommended-spec)
           (s/describe (tv/create-rule-spec {:any ["Andrew Downes"]}))))
    (is (= "Exception!" (try (tv/create-rule-spec {:presence "foobar"})
                             (catch Exception e (str "Exception!")))))
    (is (= "Exception!" (try (tv/create-rule-spec {})
                             (catch Exception e (str "Exception!")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath tests.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest evaluate-paths-test
  (testing "evaluate-paths: given a bunch of JSONPaths and a Statement, get
           a vector of evaluated values."
    (is (= (tv/evaluate-paths ex-statement-0 ["$.actor.objectType"])
           ["Agent"]))
    (is (= (tv/evaluate-paths ex-statement-0 ["$.actor.member[*].name"])
           ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]))
    (is (= (tv/evaluate-paths ex-statement-0 ["$.actor.mbox" "$.actor.mbox_sha1sum"])
           ["mailto:email@yetanalytics.io" nil]))
    (is (= (tv/evaluate-paths
            ex-statement-0
            ["$.object.objectType"
             "$.context.contextActivities.parent[*].objectType"
             "$.context.contextActivities.grouping[*].objectType"
             "$.context.contextActivities.category[*].objectType"
             "$.context.contextActivities.other[*].objectType"])
           ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity"
            "Activity" "Activity" "Activity"]))
    (is (= (tv/evaluate-paths ex-statement-0 ["$.foo" "$.object.bar"])
           [nil nil]))))

(deftest find-values-test
  (testing "find-values: given a statement, location and selector, find the
          values evaluated by the JSONPath strings."
    (is (= (tv/find-values ex-statement-0 "$.verb.id")
           ["http://foo.org/verb"]))
    (is (= (tv/find-values ex-statement-0 "$.actor.objectType")
           ["Agent"]))
    (is (= (tv/find-values ex-statement-0 "$.actor.member[*].name")
           ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]))
    (is (= (tv/find-values ex-statement-0 "$.actor.mbox")
           ["mailto:email@yetanalytics.io"]))
    (is (= (tv/find-values ex-statement-0 "$.actor.mbox" nil)
           ["mailto:email@yetanalytics.io"]))
    (is (= (tv/find-values ex-statement-0 "$.actor" "$.mbox")
           ["mailto:email@yetanalytics.io"]))
    (is (= (tv/find-values ex-statement-0 "$.actor" "$.mbox_sha1sum")
           [nil]))
    (is (= (tv/find-values ex-statement-0 "$.actor.mbox | $.actor.mbox_sha1sum")
           ["mailto:email@yetanalytics.io" nil]))
    (is (= (tv/find-values ex-statement-0 "$.actor" "$.mbox | $.mbox_sha1sum")
           ["mailto:email@yetanalytics.io" nil]))
    (is (= (tv/find-values ex-statement-0
                           "$.object.objectType 
                           | $.context.contextActivities.parent[*].objectType 
                           | $.context.contextActivities.grouping[*].objectType
                           | $.context.contextActivities.category[*].objectType
                           | $.context.contextActivities.other[*].objectType")
           ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity"
            "Activity" "Activity" "Activity"]))
    (is (= (tv/find-values ex-statement-0
                           "$.context.contextActivities"
                           "$.parent[*].objectType 
                          | $.grouping[*].objectType
                          | $.category[*].objectType
                          | $.other[*].objectType")
           ["Activity" "Activity" "Activity" "Activity" "Activity"
            "Activity" "Activity" "Activity"]))
    (is (= 4 (count (tv/find-values ex-statement-0
                                    "$.context.contextActivities.parent 
                                   | $.context.contextActivities.grouping
                                   | $.context.contextActivities.category
                                   | $.context.contextActivities.other"))))
    (is (s/valid? (s/coll-of vector? :kind vector?)
                  (tv/find-values ex-statement-0
                                  "$.context.contextActivities.parent 
                                 | $.context.contextActivities.grouping
                                 | $.context.contextActivities.category
                                 | $.context.contextActivities.other")))
    (is (= (tv/find-values ex-statement-0
                           "$.context.contextActivities.parent.fi
                          | $.context.contextActivities.grouping.fy
                                     | $.context.contextActivities.category.fo
                                     | $.context.contextActivities.other.fum")
           [nil nil nil nil]))
    (is (= (set (tv/find-values ex-statement-0 "$..type"))
           #{"http://foo.org/oat"
             "http://foo.org/cpat1" "http://foo.org/cpat2"
             "http://foo.org/cgat1" "http://foo.org/cgat2"
             "http://foo.org/ccat1" "http://foo.org/ccat2"
             "http://foo.org/coat1" "http://foo.org/coat2"}))
    (is (= (tv/find-values ex-statement-0 "$.result.score.raw") [9001]))))

;; FIXME: This is a bug in gga/json-path!
;; (tv/find-values ex-statement-0 "$..raw") will throw an exception
;; Either switch to a more robust lib or warn the user about it

;; Determining Properties test
(deftest add-det-properties
  (testing "add-det-properties function: Add the Determining Properties as 
           rules."
    (is (= (tv/add-det-properties ex-template)
           [{:location "$.verb.id"
             :presence "included"
             :all ["http://foo.org/verb"]
             :determiningProperty "Verb"}
            {:location "$.object.definition.type"
             :presence "included"
             :all ["http://foo.org/oat"]
             :determiningProperty "objectActivityType"}
            {:location "$.context.contextActivities.parent[*].definition.type"
             :presence "included"
             :all ["http://foo.org/cpat1" "http://foo.org/cpat2"]
             :determiningProperty "contextParentActivityType"}
            {:location "$.context.contextActivities.grouping[*].definition.type"
             :presence "included"
             :all ["http://foo.org/cgat1" "http://foo.org/cgat2"]
             :determiningProperty "contextGroupingActivityType"}
            {:location "$.context.contextActivities.category[*].definition.type"
             :presence "included"
             :all ["http://foo.org/ccat1" "http://foo.org/ccat2"]
             :determiningProperty "contextCategoryActivityType"}
            {:location "$.context.contextActivities.other[*].definition.type"
             :presence "included"
             :all ["http://foo.org/coat1" "http://foo.org/coat2"]
             :determiningProperty "contextOtherActivityType"}
            {:location "$.attachments[*].usageType"
             :presence "included"
             :all ["http://foo.org/aut1" "http://foo.org/aut2"]
             :determiningProperty "attachmentUsageType"}
            {:location "$.actor.objectType" :presence "included"}
            {:location "$.actor.member[*].name" :presence "included"
             :any ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder"
                   "Erika Lee" "Boris Boiko"]
             :none ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony"
                    "Jeremy Gardner"]}
            {:location "$.actor"
             :selector "$.mbox"
             :presence "included"}
            {:location "$.actor"
             :selector "$.mbox_sha1sum"
             :presence "excluded"}
            {:location
             "$.object.objectType | $.context.contextActivities.parent[*].objectType | $.context.contextActivities.grouping[*].objectType | $.context.contextActivities.category[*].objectType | $.context.contextActivities.other[*].objectType"
             :presence "included"
             :all ["Activity"]}]))))

(deftest create-rule-validator-test
  (testing "create-rule-validator function: Given a rule, create a validation
           function that accepts Statements"
    (is (function? (tv/create-rule-validator {:presence "included"
                                              :all ["Andrew Downes"]})))
    (is (nil? ((tv/create-rule-validator {:location "$.foo.bar"
                                          :presence "included" :any ["baz"]})
               {:foo {:bar "baz"}})))))

(deftest create-rule-validators-test
  (testing "create-rule-validators function: Create a vector of validation
           functions based off of a Template."
    (is (s/valid? (s/coll-of fn? :kind vector?)
                  (tv/create-rule-validators ex-template)))
    (is (= 12 (count (tv/create-rule-validators ex-template))))))

(deftest validate-statement-test
  (testing "validate-statement function: Validate an entire Statement!"
    (is (tv/validate-statement (tv/create-rule-validators ex-template)
                               ex-statement-0))
    (is (tv/validate-statement
         (tv/create-rule-validators
          {:verb "http://example.com/xapi/verbs#sent-a-statement"})
         ex-statement-1))
    (is (tv/validate-statement
         (tv/create-rule-validators
          {:verb "http://adlnet.gov/expapi/verbs/attempted"})
         ex-statement-2))
    (is (tv/validate-statement
         (tv/create-rule-validators
          {:verb "http://adlnet.gov/expapi/verbs/attended"
           :objectActivityType "http://adlnet.gov/expapi/activities/meeting"
           :contextCategoryActivityType
           ["http://example.com/expapi/activities/meetingcategory"]})
         ex-statement-3))
    (is (tv/validate-statement
         (tv/create-rule-validators
          {:verb "http://adlnet.gov/expapi/verbs/experienced"})
         ex-statement-4))))
