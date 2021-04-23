(ns com.yetanalytics.persephone-test.template-validation-test
  (:require [clojure.test :refer [deftest testing is are]]
            [com.yetanalytics.pathetic :as path]
            [com.yetanalytics.persephone.utils.json :as json]
            [com.yetanalytics.persephone.utils.errors :as print-errs]
            [com.yetanalytics.persephone.template-validation :as tv
             #?@(:clj [:refer [wrap-pred and-wrapped or-wrapped add-wrapped]]
                 :cljs [:refer-macros [wrap-pred and-wrapped or-wrapped add-wrapped]])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-a? [a x] (= a x))
(defn is-b? [b x] (= b x))

(def is-zero?
  (-> (wrap-pred even?)
      (add-wrapped is-a? 0)
      (add-wrapped is-b? nil)))

(def is-even?
  (-> (wrap-pred even?)
      (add-wrapped is-a? nil)))

(def is-zero-2?
  (and-wrapped (wrap-pred even?) (wrap-pred zero?)))

(def is-even-2?
  (or-wrapped (wrap-pred even?) (wrap-pred zero?)))

(deftest macro-test
  (testing "template validation util macros"
    (are [expected v]
         (= expected (is-zero? v))
      nil 0
      :even? 1
      :is-a? 2)
    (are [expected v]
         (= expected (is-even? v))
      nil 0
      nil 2
      :even? 1)
    (are [expected v]
         (= expected (is-zero-2? v))
      nil 0
      :even? 1
      :zero? 2)
    (are [expected v]
         (= expected (is-even-2? v))
      nil 0
      :even? 1
      nil 2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Template Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:cljs (defn slurp [path]
           (let [fs (js/require "fs")]
             (.readFileSync fs path "utf8"))))

(def ex-statement-1
  (json/json->edn (slurp "test-resources/sample_statements/adl_1.json")))
(def ex-statement-2
  (json/json->edn (slurp "test-resources/sample_statements/adl_2.json")))
(def ex-statement-3
  (json/json->edn (slurp "test-resources/sample_statements/adl_3.json")))
(def ex-statement-4
  (json/json->edn (slurp "test-resources/sample_statements/adl_4.json")))

;; Statement that conforms to ex-template
;; Not a complete Statement, but has the minimum for validation
(def ex-statement-0
  {"id"          "some-uuid"
   "actor"       {"objectType" "Agent"
                  "name"       "Yet Analytics Dev Team"
                  "mbox"       "mailto:email@yetanalytics.io"
                  "member"     [{"name" "Will Hoyt"}
                                {"name" "Milt Reder"}
                                {"name" "John Newman"}
                                {"name" "Henk Reder"}
                                {"name" "Erika Lee"}
                                {"name" "Boris Boiko"}]}
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
   "attachments" [{"usageType" "http://foo.org/aut1"}
                  {"usageType" "http://foo.org/aut2"}]})

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util function tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest all-matchable?-test
  (testing "all-matchable? function: return true iff every value is matchable."
    (is (tv/all-matchable? ["foo" "bar" "stan loona"]))
    (is (tv/all-matchable? [])) ;; vacuously true
    (is (not (tv/all-matchable? [nil nil nil])))
    (is (not (tv/all-matchable? [nil nil "what the pineapple"])))))

(deftest none-matchable?-test
  (testing "none-matchable? function: return true iff no value is matchable."
    (is (tv/none-matchable? []))
    (is (tv/none-matchable? [nil nil nil]))
    (is (not (tv/none-matchable? ["foo" "bar"])))
    (is (not (tv/none-matchable? [nil nil "what the pineapple"])))))

(deftest any-matchable?-test
  (testing "any-matchable? function: return true iff some values are matchable."
    (is (tv/any-matchable? [nil nil "still good"]))
    (is (tv/any-matchable? ["foo" "bar" "all good"]))
    (is (not (tv/any-matchable? [])))
    (is (not (tv/any-matchable? [nil nil nil])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicate tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- value-map
  "Given an array of keys (each corresponding to a level of map
   nesting), return corresponding values from a vector of maps."
  [map-vec & ks]
  (mapv #(get-in % ks) map-vec))

;; $.actor.member[*].name = ["Andrew Downes" "Toby Nichols" "Ena Hills"]
(def name-values
  (value-map (get-in ex-statement-3 ["actor" "member"]) "name"))

(deftest some-any-values?-test
  (testing "some-any-values? fn: values MUST include at least one value that is
           given by 'any', ie. the collections need to intersect."
    (is (tv/some-any-values? #{"Andrew Downes" "Toby Nichols"} name-values))
    (is (tv/some-any-values? #{"Andrew Downes" "Will Hoyt"} name-values))
    (is (not (tv/some-any-values? #{"Will Hoyt" "Milt Reder"} name-values)))
    (is (not (tv/some-any-values? #{} name-values)))
    ;; any-values is undefined if there are no matchable values
    (is (not (tv/some-any-values? #{} [])))
    (is (not (tv/some-any-values? #{"Andrew Downes"} [nil])))))

(deftest only-all-values?-test
  (testing "only-all-values? fn: values MUST all be from the values given by
           'all'."
    (is (tv/only-all-values? #{"Andrew Downes" "Toby Nichols" "Ena Hills"}
                             name-values))
    ;; Superset is okay
    (is (tv/only-all-values? #{"Andrew Downes" "Toby Nichols" "Ena Hills" "Will Hoyt"}
                             name-values))
    (is (not (tv/only-all-values? #{"Andrew Downes" "Toby Nichols"} name-values)))
    (is (not (tv/only-all-values? #{} name-values)))))

(deftest no-none-values?-test
  (testing "no-none-values fn: values MUST NOT be included in the set given
           by 'none'."
    (is (tv/no-none-values? #{"Will Hoyt" "Milt Reder"} name-values))
    (is (not (tv/no-none-values? #{"Andrew Downes"} name-values)))
    (is (not (tv/no-none-values? #{"Will Hoyt" "Milt Reder" "Ena Hills"}
                                 name-values)))
    (is (tv/no-none-values? #{"Will Hoyt" "Milt Reder"} []))
    (is (tv/no-none-values? #{"Will Hoyt" "Milt Reder"} [nil]))
    ;; If there is nothing to exclude, we should be okay
    (is (tv/no-none-values? #{} name-values))
    (is (tv/no-none-values? #{} []))))

(deftest no-unmatch-vals?-test
  (testing "no-unmatch-vals? fn: no unmatchable values allowed."
    (is (tv/no-unmatch-vals? #{"Andrew Downes" "Toby Nichols" "Ena Hills"} []))
    (is (not (tv/no-unmatch-vals? #{"Andrew Downes"} [nil nil])))
    (is (not (tv/no-unmatch-vals? #{} [nil nil])))))

;; Predicates for our next tests
(def included-pred
  (tv/create-included-pred {:presence "included" :any ["Andrew Downes"]}))

(def excluded-pred
  (tv/create-excluded-pred {:presence "excluded"}))

(def recommended-pred
  (tv/create-default-pred {:presence "recommended" :any ["Andrew Downes"]}))

(deftest create-included-pred-test
  (testing "create-included-pred function: create a predicate when presence is
           'included'. Values MUST have at least one matchable value (and no
           unmatchable values) and MUST follow any/all/none reqs."
    (is (nil? (included-pred name-values)))
    (is (= :some-any-values? (included-pred ["Will Hoyt"])))
    (is (= :any-matchable? (included-pred [])))
    (is (= :all-matchable? (included-pred ["Andrew Downes" nil])))))

(deftest create-excluded-pred-test
  (testing "create-excluded-pred function: create a predicate when presence is
           'excluded.' There MUST NOT be any matchable values."
    (is (nil? (excluded-pred [])))
    (is (nil? (excluded-pred [nil nil])))
    (is (= :none-matchable? (excluded-pred name-values)))
    (is (= :none-matchable? (excluded-pred (conj name-values nil))))))

;; The test for when presence is missing is pretty much the same. 
(deftest create-recommended-pred-test
  (testing "create-recommended-pred function: create a predicate when presence
           is 'recommended'. MUST follow any/all/none reqs."
    (is (nil? (recommended-pred [])))
    (is (nil? (recommended-pred name-values)))
    (is (= :some-any-values? (recommended-pred ["Will Hoyt"])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest find-values-test
  (testing "find-values: given a statement, location and selector, find the
          values evaluated by the JSONPath strings."
    (is (= ["http://foo.org/verb"]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.verb.id"))))
    (is (= ["Agent"]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor.objectType"))))
    (is (= ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor.member[*].name"))))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor.mbox"))))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor.mbox")
                           nil)))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor")
                           (path/parse-paths "$[*].mbox"))))
    (is (= [nil]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor")
                           (path/parse-paths "$[*].mbox_sha1sum"))))
    (is (= ["mailto:email@yetanalytics.io" nil]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor.mbox | $.actor.mbox_sha1sum"))))
    (is (= ["mailto:email@yetanalytics.io" nil]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.actor")
                           (path/parse-paths "$[*].mbox | $[*].mbox_sha1sum"))))
    (is (= ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity"]
           (tv/find-values ex-statement-0
                           (path/parse-paths
                            "$.object.objectType 
                            | $.context.contextActivities.parent[*].objectType 
                            | $.context.contextActivities.grouping[*].objectType
                            | $.context.contextActivities.category[*].objectType
                            | $.context.contextActivities.other[*].objectType"))))
    (is (= ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity"]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.context.contextActivities")
                           (path/parse-paths
                            "$[*].parent[*].objectType 
                           | $[*].grouping[*].objectType
                           | $[*].category[*].objectType
                           | $[*].other[*].objectType"))))
    (is (= 4 (count (tv/find-values
                     ex-statement-0
                     (path/parse-paths "$.context.contextActivities.parent 
                                | $.context.contextActivities.grouping
                                | $.context.contextActivities.category
                                | $.context.contextActivities.other")))))
    (is (vector? (first (tv/find-values
                         ex-statement-0
                         (path/parse-paths "$.context.contextActivities.parent 
                                 | $.context.contextActivities.grouping
                                 | $.context.contextActivities.category
                                 | $.context.contextActivities.other")))))
    (is (= [nil nil nil nil]
           (tv/find-values
            ex-statement-0
            (path/parse-paths "$.context.contextActivities.parent.fi
                          | $.context.contextActivities.grouping.fy
                          | $.context.contextActivities.category.fo
                          | $.context.contextActivities.other.fum"))))
    (is (= #{"http://foo.org/oat"
             "http://foo.org/cpat1" "http://foo.org/cpat2"
             "http://foo.org/cgat1" "http://foo.org/cgat2"
             "http://foo.org/ccat1" "http://foo.org/ccat2"
             "http://foo.org/coat1" "http://foo.org/coat2"}
           (set (tv/find-values ex-statement-0
                                (path/parse-paths "$..type")))))
    (is (= [9001]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$.result.score.raw"))))
    (is (= [9001]
           (tv/find-values ex-statement-0
                           (path/parse-paths "$..raw"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Determining Properties tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest add-det-properties
  (testing "add-det-properties function: Add the Determining Properties as 
           rules."
    (is (= [{:location             "$.verb.id"
             :prop-vals            ["http://foo.org/verb"]
             :determining-property "Verb"}
            {:location             "$.object.definition.type"
             :prop-vals            ["http://foo.org/oat"]
             :determining-property "objectActivityType"}
            {:location             "$.context.contextActivities.parent[*].definition.type"
             :prop-vals            ["http://foo.org/cpat1" "http://foo.org/cpat2"]
             :determining-property "contextParentActivityType"}
            {:location             "$.context.contextActivities.grouping[*].definition.type"
             :prop-vals            ["http://foo.org/cgat1" "http://foo.org/cgat2"]
             :determining-property "contextGroupingActivityType"}
            {:location             "$.context.contextActivities.category[*].definition.type"
             :prop-vals            ["http://foo.org/ccat1" "http://foo.org/ccat2"]
             :determining-property "contextCategoryActivityType"}
            {:location             "$.context.contextActivities.other[*].definition.type"
             :prop-vals            ["http://foo.org/coat1" "http://foo.org/coat2"]
             :determining-property "contextOtherActivityType"}
            {:location             "$.attachments[*].usageType"
             :prop-vals            ["http://foo.org/aut1" "http://foo.org/aut2"]
             :determining-property "attachmentUsageType"}
            {:location "$.actor.objectType"
             :presence "included"}
            {:location "$.actor.member[*].name"
             :presence "included"
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
             :all      ["Activity"]}]
           (tv/add-determining-properties ex-template)))
    (is (= [{:location             "$.verb.id"
             :prop-vals            ["http://example.org/verb"]
             :determining-property "Verb"}]
           (tv/add-determining-properties
            {:verb "http://example.org/verb"})))
    ;; Doesn't work with string keys
    (is (= []
           (tv/add-determining-properties
            {"verb" "http://example.org/verb"})))))

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
                                                  :prop-vals ["foo" "bar"]
                                                  :determining-property "X"})]
      ;; MUST include all the Determining Properties [values] in the Statement Template
      (is (nil? (validator-fn ["foo" "bar"])))
      (is (nil? (validator-fn ["foo" "bar" "baz"])))
      (is (nil? (validator-fn ["baz" "bar" "qux ""foo"])))
      (is (nil? (validator-fn [nil "foo" nil "bar" nil])))
      (is (= :every-val-present? (:pred (validator-fn ["foo"]))))
      (is (= :every-val-present? (:pred (validator-fn ["baz" "foo"]))))
      (is (= :every-val-present? (:pred (validator-fn [nil "foo" nil]))))
      (is (= :any-matchable? (:pred (validator-fn [nil])))))))

(deftest create-template-validator-test
  (testing "create-template-validator function: Given a template, create a
            validator function that accepts Statements."
    (is (nil? ((tv/create-template-validator ex-template) ex-statement-0)))
    (is (nil? ((tv/create-template-validator
                {:verb "http://example.com/xapi/verbs#sent-a-statement"})
               ex-statement-1)))
    (is (nil? ((tv/create-template-validator
                {:verb "http://adlnet.gov/expapi/verbs/attempted"})
               ex-statement-2)))
    (is (nil? ((tv/create-template-validator
                {:verb "http://adlnet.gov/expapi/verbs/attended"
                 :objectActivityType "http://adlnet.gov/expapi/activities/meeting"
                 :contextCategoryActivityType
                 ["http://example.com/expapi/activities/meetingcategory"]})
               ex-statement-3)))
    (is (nil? ((tv/create-template-validator
                {:verb "http://adlnet.gov/expapi/verbs/experienced"})
               ex-statement-4)))
    (is (nil? ((tv/create-template-validator {})
               ex-statement-4)))
    (is (some? ((tv/create-template-validator
                {:verb "http://adlnet.gov/expapi/verbs/experienced-not"})
               ex-statement-4)))))

(deftest create-template-predicate-test
  (testing "create-template-predicate function: Given a template, create a
            predicate that accepts Statements."
    (is ((tv/create-template-predicate ex-template) ex-statement-0))
    (is ((tv/create-template-predicate
          {:verb "http://example.com/xapi/verbs#sent-a-statement"})
         ex-statement-1))
    (is ((tv/create-template-predicate
          {:verb "http://adlnet.gov/expapi/verbs/attempted"})
         ex-statement-2))
    (is ((tv/create-template-predicate
          {:verb "http://adlnet.gov/expapi/verbs/attended"
           :objectActivityType "http://adlnet.gov/expapi/activities/meeting"
           :contextCategoryActivityType
           ["http://example.com/expapi/activities/meetingcategory"]})
         ex-statement-3))
    (is ((tv/create-template-predicate
          {:verb "http://adlnet.gov/expapi/verbs/experienced"})
         ex-statement-4))
    (is ((tv/create-template-predicate {})
         ex-statement-4))
    (is (not ((tv/create-template-predicate
               {:verb "http://adlnet.gov/expapi/verbs/experienced-not"})
              ex-statement-4)))))

(deftest create-template-validator-test-2
  (testing "validate-statement function"
    (is (= [{:pred :every-val-present?
             :values ["http://example.com/xapi/verbs#sent-a-statement"]
             :rule {:location "$.verb.id"
                    :prop-vals ["http://foo.org/verb"]
                    :determining-property "Verb"}}
            {:pred :any-matchable?
             :values [nil]
             :rule {:location "$.object.definition.type"
                    :prop-vals ["http://foo.org/oat"]
                    :determining-property "objectActivityType"}}
            {:pred :any-matchable?
             :values [nil]
             :rule {:location "$.context.contextActivities.parent[*].definition.type"
                    :prop-vals ["http://foo.org/cpat1" "http://foo.org/cpat2"]
                    :determining-property "contextParentActivityType"}}
            {:pred :any-matchable?
             :values [nil]
             :rule {:location "$.context.contextActivities.grouping[*].definition.type"
                    :prop-vals ["http://foo.org/cgat1" "http://foo.org/cgat2"]
                    :determining-property "contextGroupingActivityType"}}
            {:pred :any-matchable?
             :values [nil]
             :rule {:location "$.context.contextActivities.category[*].definition.type"
                    :prop-vals ["http://foo.org/ccat1" "http://foo.org/ccat2"]
                    :determining-property "contextCategoryActivityType"}}
            {:pred :any-matchable?
             :values [nil]
             :rule {:location "$.context.contextActivities.other[*].definition.type"
                    :prop-vals ["http://foo.org/coat1" "http://foo.org/coat2"]
                    :determining-property "contextOtherActivityType"}}
            {:pred :any-matchable?
             :values [nil]
             :rule {:location "$.attachments[*].usageType"
                    :prop-vals ["http://foo.org/aut1" "http://foo.org/aut2"]
                    :determining-property "attachmentUsageType"}}
            {:pred :any-matchable?
             :values [nil]
             :rule {:location "$.actor.member[*].name"
                    :presence "included"
                    :any ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]
                    :none ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony" "Jeremy Gardner"]}}
            {:pred :any-matchable?
             :values [nil nil nil nil nil]
             :rule {:location
                    "$.object.objectType | $.context.contextActivities.parent[*].objectType | $.context.contextActivities.grouping[*].objectType | $.context.contextActivities.category[*].objectType | $.context.contextActivities.other[*].objectType"
                    :presence "included"
                    :all ["Activity"]}}]
           ((tv/create-template-validator ex-template) ex-statement-1)))))

(deftest create-template-predicate-test-2
  (testing "valid-statement? function"
    (is (not ((tv/create-template-predicate ex-template) ex-statement-1)))))

(deftest print-error-test
  (testing "printing an error message using the print-error fn"
    (is (= (str "----- Invalid Statement -----\n"
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
             (print-errs/print-error
              ((tv/create-template-validator ex-template) ex-statement-1)
              (:id ex-template)
              (get ex-statement-1 "id")))))))

(deftest statement-ref-templates-test
  (let [id-template-map
        {"stmt-ref-template-0" {:id "stmt-ref-template-0"
                                :objectStatementRefTemplate
                                ["stmt-ref-template-1"
                                 "stmt-template-a"]
                                :contextStatementRefTemplate
                                ["stmt-template-a"
                                 "stmt-ref-template-1"]}
         "stmt-ref-template-1" {:id "stmt-ref-template-1"
                                :objectStatementRefTemplate
                                ["stmt-ref-template-2"]
                                :contextStatementRefTemplate
                                ["stmt-ref-template-2"]}
         "stmt-ref-template-2" {:id "stmt-ref-template-2"
                                :objectStatementRefTemplate
                                ["stmt-ref-template-3"]
                                :contextStatementRefTemplate
                                ["stmt-ref-template-3"]}
         "stmt-ref-template-3" {:id "stmt-ref-template-3"
                                :objectStatementRefTemplate
                                ["stmt-template-a"]
                                :contextStatementRefTemplate
                                ["stmt-template-a"]}
         "stmt-ref-template-4" {:id "stmt-ref-template-4"
                                :objectStatementRefTemplate
                                ["stmt-template-b"
                                 "stmt-ref-template-5"]
                                :contextStatementRefTemplate
                                ["stmt-ref-template-5"]}
         "stmt-ref-template-5" {:id "stmt-ref-template-5"
                                :verb "http://foo.org/verb-bar"
                                :contextStatementRefTemplate
                                ["stmt-template-c"]}
         "stmt-template-a" {:id   "stmt-template-a"
                            :verb "http://foo.org/verb"}
         "stmt-template-b" {:id   "stmt-template-b"
                            :verb "http://foo.org/verb-2"}
         "stmt-template-c" {:id   "stmt-template-c"
                            :verb "http://foo.org/verb-3"}}
        stmt-map
        {"stmt-0" (-> ex-statement-0
                      (assoc "id" "stmt-0")
                      (assoc "object" {"id" "stmt-1"
                                       "objectType" "StatementRef"})
                      (assoc-in ["context" "statement"]
                                {"id" "stmt-1"
                                 "objectType" "StatementRef"}))
         "stmt-1" (-> ex-statement-0
                      (assoc "id" "stmt-1")
                      (assoc "object" {"id" "stmt-2"
                                       "objectType" "StatementRef"})
                      (assoc-in ["context" "statement"]
                                {"id" "stmt-2"
                                 "objectType" "StatementRef"}))
         "stmt-2" (-> ex-statement-0
                      (assoc "id" "stmt-2")
                      (assoc "object" {"id" "stmt-3"
                                       "objectType" "StatementRef"})
                      (assoc-in ["context" "statement"]
                                {"id" "stmt-3"
                                 "objectType" "StatementRef"}))
         "stmt-3" (-> ex-statement-0
                      (assoc "id" "stmt-3")
                      (assoc "object" {"id" "stmt-4"
                                       "objectType" "StatementRef"})
                      (assoc-in ["context" "statement"]
                                {"id" "stmt-4"
                                 "objectType" "StatementRef"}))
         "stmt-4" (-> ex-statement-0
                      (assoc "id" "stmt-4"))}
        make-validator
        (fn [id]
          (tv/create-template-validator
           (get id-template-map id)
           :statement-ref-opts {:get-template-fn  id-template-map
                                :get-statement-fn stmt-map}))
        make-predicate
        (fn [id]
          (tv/create-template-predicate
           (get id-template-map id)
           :statement-ref-opts {:get-template-fn  id-template-map
                                :get-statement-fn stmt-map}))]
    (testing "statement ref template validators - valid"
      (is (nil? ((make-validator "stmt-ref-template-0")
                 (get stmt-map "stmt-0"))))
      (is (nil? ((make-validator "stmt-ref-template-1")
                 (get stmt-map "stmt-1"))))
      (is (nil? ((make-validator "stmt-ref-template-2")
                 (get stmt-map "stmt-2"))))
      (is (nil? ((make-validator "stmt-ref-template-3")
                 (get stmt-map "stmt-3")))))
    (testing "statement ref template predicates - valid"
      (is ((make-predicate "stmt-ref-template-0")
           (get stmt-map "stmt-0")))
      (is ((make-predicate "stmt-ref-template-1")
           (get stmt-map "stmt-1")))
      (is ((make-predicate "stmt-ref-template-2")
           (get stmt-map "stmt-2")))
      (is ((make-predicate "stmt-ref-template-3")
           (get stmt-map "stmt-3"))))
    (testing "statement ref template validator - invalid"
      (let [stmt (-> ex-statement-0
                      (dissoc "object")
                      (dissoc "context"))]
        (is (= [{:pred   :statement-ref?
                 :values stmt
                 :rule   {:location "$.object"
                          :failure  :sref-not-found}}
                {:pred   :statement-ref?
                 :values stmt
                 :rule   {:location "$.context.statement"
                          :failure  :sref-not-found}}]
               ((make-validator "stmt-ref-template-3") stmt))))
      (let [stmt (-> ex-statement-0
                     (assoc-in ["object"]
                               {"objectType" "Foo" "id" "bar"})
                     (assoc-in ["context" "statement"]
                               {"objectType" "Foo" "id" "bar"}))]
        (is (= [{:pred   :statement-ref?
                 :values (get-in stmt ["object"])
                 :rule   {:location "$.object"
                          :failure  :sref-object-type-invalid}}
                {:pred   :statement-ref?
                 :values (get-in stmt ["context" "statement"])
                 :rule   {:location "$.context.statement"
                          :failure  :sref-object-type-invalid}}]
               ((make-validator "stmt-ref-template-3") stmt))))
      (let [stmt (-> ex-statement-0
                     (assoc-in ["object"]
                               {"objectType" "StatementRef"})
                     (assoc-in ["context" "statement"]
                               {"objectType" "StatementRef"}))]
        (is (= [{:pred   :statement-ref?
                 :values (get-in stmt ["object"])
                 :rule   {:location "$.object"
                          :failure  :sref-id-missing}}
                {:pred   :statement-ref?
                 :values (get-in stmt ["context" "statement"])
                 :rule   {:location "$.context.statement"
                          :failure  :sref-id-missing}}]
               ((make-validator "stmt-ref-template-3") stmt))))
      (let [stmt (-> ex-statement-0
                     (assoc-in ["object"]
                               {"objectType" "StatementRef" "id" "baz"})
                     (assoc-in ["context" "statement"]
                               {"objectType" "StatementRef" "id" "qux"}))]
        (is (= [{:pred   :statement-ref?
                 :values (get-in stmt ["object" "id"])
                 :rule   {:location "$.object"
                          :failure  :sref-stmt-not-found}}
                {:pred   :statement-ref?
                 :values (get-in stmt ["context" "statement" "id"])
                 :rule   {:location "$.context.statement"
                          :failure  :sref-stmt-not-found}}]
               ((make-validator "stmt-ref-template-3") stmt))))
      (is (= [{:pred   :every-val-present?
               :values ["http://foo.org/verb"]
               :rule   {:location             "$.verb.id"
                        :prop-vals            ["http://foo.org/verb-bar"]
                        :determining-property "Verb"}}
              {:pred   :statement-ref?
               :values (get stmt-map "stmt-4")
               :rule   {:location "$.context.statement"
                        :failure  :sref-not-found}}]
             ((make-validator "stmt-ref-template-5")
              (get stmt-map "stmt-4"))))
      (is (= [{:pred   :every-val-present?
               :values ["http://foo.org/verb"]
               :rule   {:location             "$.verb.id"
                        :prop-vals            ["http://foo.org/verb-2"]
                        :determining-property "Verb"}}
              {:pred   :every-val-present?
               :values ["http://foo.org/verb"]
               :rule   {:location             "$.verb.id"
                        :prop-vals            ["http://foo.org/verb-bar"]
                        :determining-property "Verb"}}
              {:pred   :statement-ref?
               :values (get stmt-map "stmt-4")
               :rule   {:location "$.context.statement"
                        :failure  :sref-not-found}}
              {:pred   :every-val-present?
               :values ["http://foo.org/verb"]
               :rule   {:location             "$.verb.id"
                        :prop-vals            ["http://foo.org/verb-bar"]
                        :determining-property "Verb"}}
              {:pred   :every-val-present?
               :values ["http://foo.org/verb"]
               :rule   {:location             "$.verb.id"
                        :prop-vals            ["http://foo.org/verb-3"]
                        :determining-property "Verb"}}]
             ((make-validator "stmt-ref-template-4")
              (assoc-in (get stmt-map "stmt-3")
                        ["context" "statement"]
                        {"objectType" "StatementRef"
                         "id" "stmt-3"})))))
    (testing "statement ref template predicate - invalid"
        (is (not ((make-predicate "stmt-ref-template-4")
                  (get stmt-map "stmt-3")))))))

(comment
  ((tv/create-template-validator
    {:id "stmt-ref-template-3"
     :objectStatementRefTemplate
     ["stmt-template-a"]}
    :statement-ref-opts {:get-template-fn  (fn [_] {:id   "stmt-template-a"
                                                    :verb "http://foo.org/verb"})
                         :get-statement-fn (fn [_] ex-statement-0)})
   ex-statement-0
   #_(assoc ex-statement-0 "object" {"id"         "stmt-1"
                                   "objectType" "StatementRef"})))
