(ns com.yetanalytics.persephone-test.template-validation-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.persephone.utils.json :as json]
            [com.yetanalytics.persephone.template-validation :as tv
             #?@(:clj [:refer [add-spec]]
                 :cljs [:refer-macros [add-spec]])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- gt-y? [y x] (< y x))
(defn- lt-y? [y x] (> y x))
(s/def ::odd-gt0-lt10 (-> (s/spec odd?)
                          (add-spec gt-y? 0)
                          (add-spec lt-y? 10)))
(def y-nil nil)

(deftest add-spec-test
  (testing "add-spec test: given a spec, predicate, and value, create a new
            s/and spec if the value is not nil."
    (is (s/valid? ::odd-gt0-lt10 3))
    (is (= "odd?" (-> (s/explain-data ::odd-gt0-lt10 2)
                      tv/pred-name-of-error)))
    (is (= "gt-y?" (-> (s/explain-data ::odd-gt0-lt10 -1)
                       tv/pred-name-of-error)))
    (is (= "lt-y?" (-> (s/explain-data ::odd-gt0-lt10 11)
                       tv/pred-name-of-error)))
    (is (s/valid? (tv/add-spec (s/spec odd?) gt-y? y-nil) 11))))

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

;; $.actor.member[*].name = ["Andrew Downes" "Toby Nichols" "Ena Hills"]
(def name-values
  (tv/value-map (get-in ex-statement-3 ["actor" "member"]) "name"))

(deftest some-any-values?-test
  (testing "some-any-values? fn: values MUST include at least one value that is
           given by 'any', ie. the collections need to intersect."
    (is (tv/some-any-values? ["Andrew Downes" "Toby Nichols"] name-values))
    (is (tv/some-any-values? ["Andrew Downes" "Will Hoyt"] name-values))
    (is (not (tv/some-any-values? ["Will Hoyt" "Milt Reder"] name-values)))
    (is (not (tv/some-any-values? [] name-values)))
    ;; any-values is undefined if there are no matchable values
    (is (not (tv/some-any-values? [] [])))
    (is (not (tv/some-any-values? ["Andrew Downes"] [nil])))))

(deftest only-all-values?-test
  (testing "only-all-values? fn: values MUST all be from the values given by
           'all'."
    (is (tv/only-all-values? ["Andrew Downes" "Toby Nichols" "Ena Hills"]
                        name-values))
    ;; Superset is okay
    (is (tv/only-all-values? ["Andrew Downes" "Toby Nichols" "Ena Hills" "Will Hoyt"]
                        name-values))
    (is (not (tv/only-all-values? ["Andrew Downes" "Toby Nichols"] name-values)))
    (is (not (tv/only-all-values? [] name-values)))))

(deftest no-none-values?-test
  (testing "no-none-values fn: values MUST NOT be included in the set given
           by 'none'."
    (is (tv/no-none-values? ["Will Hoyt" "Milt Reder"] name-values))
    (is (not (tv/no-none-values? ["Andrew Downes"] name-values)))
    (is (not (tv/no-none-values? ["Will Hoyt" "Milt Reder" "Ena Hills"]
                              name-values)))
    (is (tv/no-none-values? ["Will Hoyt" "Milt Reder"] []))
    (is (tv/no-none-values? ["Will Hoyt" "Milt Reder"] [nil]))
    ;; If there is nothing to exclude, we should be okay
    (is (tv/no-none-values? [] name-values))
    (is (tv/no-none-values? [] []))))

(deftest no-unmatch-vals?-test
  (testing "no-unmatch-vals? fn: no unmatchable values allowed."
    (is (tv/no-unmatch-vals? ["Andrew Downes" "Toby Nichols" "Ena Hills"] []))
    (is (not (tv/no-unmatch-vals? ["Andrew Downes"] [nil nil])))
    (is (not (tv/no-unmatch-vals? [] [nil nil])))))

;; Predicates for our next tests
(def included-spec
  (tv/create-included-spec {:presence "included" :any ["Andrew Downes"]}))

(def excluded-spec
  (tv/create-excluded-spec {:presence "excluded"}))

(def recommended-spec
  (tv/create-default-spec {:presence "recommended" :any ["Andrew Downes"]}))

(deftest create-included-spec-test
  (testing "create-included-spec function: create a predicate when presence is
           'included'. Values MUST have at least one matchable value (and no
           unmatchable values) and MUST follow any/all/none reqs."
    (is (s/valid? included-spec name-values))
    (is (not (s/valid? included-spec ["Will Hoyt"])))
    (is (not (s/valid? included-spec [])))
    (is (not (s/valid? included-spec ["Andrew Downes" nil])))))

(deftest create-excluded-spec-test
  (testing "create-excluded-spec function: create a predicate when presence is
           'excluded.' There MUST NOT be any matchable values."
    (is (s/valid? excluded-spec []))
    (is (s/valid? excluded-spec [nil nil]))
    (is (not (s/valid? excluded-spec name-values)))
    (is (not (s/valid? excluded-spec (conj name-values nil))))))

;; The test for when presence is missing is pretty much the same. 
(deftest create-recommended-spec-test
  (testing "create-recommended-spec function: create a predicate when presence
           is 'recommended'. MUST follow any/all/none reqs."
    (is (s/valid? recommended-spec []))
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
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (tv/create-rule-spec {:presence "foobar"})))
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (tv/create-rule-spec {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest find-values-test
  (testing "find-values: given a statement, location and selector, find the
          values evaluated by the JSONPath strings."
    (is (= ["http://foo.org/verb"]
           (tv/find-values ex-statement-0 "$.verb.id")))
    (is (= ["Agent"]
           (tv/find-values ex-statement-0 "$.actor.objectType")))
    (is (= ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]
           (tv/find-values ex-statement-0 "$.actor.member[*].name")))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0 "$.actor.mbox")))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0 "$.actor.mbox" nil)))
    (is (= ["mailto:email@yetanalytics.io"]
           (tv/find-values ex-statement-0 "$.actor" "$.mbox")))
    (is (= [nil]
           (tv/find-values ex-statement-0 "$.actor" "$.mbox_sha1sum")))
    (is (= ["mailto:email@yetanalytics.io" nil]
           (tv/find-values ex-statement-0 "$.actor.mbox | $.actor.mbox_sha1sum")))
    (is (= ["mailto:email@yetanalytics.io" nil]
           (tv/find-values ex-statement-0 "$.actor" "$.mbox | $.mbox_sha1sum")))
    (is (= ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity"]
           (tv/find-values ex-statement-0
                           "$.object.objectType 
                           | $.context.contextActivities.parent[*].objectType 
                           | $.context.contextActivities.grouping[*].objectType
                           | $.context.contextActivities.category[*].objectType
                           | $.context.contextActivities.other[*].objectType")))
    (is (= ["Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity" "Activity"]
           (tv/find-values ex-statement-0
                           "$.context.contextActivities"
                           "$.parent[*].objectType 
                          | $.grouping[*].objectType
                          | $.category[*].objectType
                          | $.other[*].objectType")))
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
    (is (= [nil nil nil nil]
           (tv/find-values ex-statement-0
                           "$.context.contextActivities.parent.fi
                          | $.context.contextActivities.grouping.fy
                                     | $.context.contextActivities.category.fo
                                     | $.context.contextActivities.other.fum")))
    (is (= #{"http://foo.org/oat"
             "http://foo.org/cpat1" "http://foo.org/cpat2"
             "http://foo.org/cgat1" "http://foo.org/cgat2"
             "http://foo.org/ccat1" "http://foo.org/ccat2"
             "http://foo.org/coat1" "http://foo.org/coat2"}
           (set (tv/find-values ex-statement-0 "$..type"))))
    (is (= [9001]
           (tv/find-values ex-statement-0 "$.result.score.raw")))
    (is (= [9001]
           (tv/find-values ex-statement-0 "$..raw")))))

;; Determining Properties test
(deftest add-det-properties
  (testing "add-det-properties function: Add the Determining Properties as 
           rules."
    (is (= [{:location            "$.verb.id"
             :presence            "included"
             :all                 ["http://foo.org/verb"]
             :determiningProperty "Verb"}
            {:location            "$.object.definition.type"
             :presence            "included"
             :all                 ["http://foo.org/oat"]
             :determiningProperty "objectActivityType"}
            {:location            "$.context.contextActivities.parent[*].definition.type"
             :presence            "included"
             :all                 ["http://foo.org/cpat1" "http://foo.org/cpat2"]
             :determiningProperty "contextParentActivityType"}
            {:location            "$.context.contextActivities.grouping[*].definition.type"
             :presence            "included"
             :all                 ["http://foo.org/cgat1" "http://foo.org/cgat2"]
             :determiningProperty "contextGroupingActivityType"}
            {:location            "$.context.contextActivities.category[*].definition.type"
             :presence            "included"
             :all                 ["http://foo.org/ccat1" "http://foo.org/ccat2"]
             :determiningProperty "contextCategoryActivityType"}
            {:location            "$.context.contextActivities.other[*].definition.type"
             :presence            "included"
             :all                 ["http://foo.org/coat1" "http://foo.org/coat2"]
             :determiningProperty "contextOtherActivityType"}
            {:location            "$.attachments[*].usageType"
             :presence            "included"
             :all                 ["http://foo.org/aut1" "http://foo.org/aut2"]
             :determiningProperty "attachmentUsageType"}
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
           (tv/add-det-properties ex-template)))))

;; This set of tests is a spec for the Statement Template rule logic.
;; NOTE: validator-fn returns nil on success, error data on failure.
;;       The :pred field is the name of the predicate that failed.
(deftest create-rule-validator-test
  (testing "create-rule-validator function: Given a rule, create a validation
            function that accepts Statements."
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "included"
                                                  :any ["foo" "baz"]})]
      ;; MUST include at least one matchable value if presence is included
      (is (= "any-matchable?" (:pred (validator-fn []))))
      (is (= "any-matchable?" (:pred (validator-fn [nil nil]))))
      ;; MUST NOT include any unmatchable values if presence is included
      (is (= "all-matchable?" (:pred (validator-fn [nil "foo" nil]))))
      ;; MUST, if any is provided, include at least one value in any as one of the matchable values
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (nil? (validator-fn ["foo" "bar"])))
      (is (= "some-any-values?" (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "included"
                                                  :all ["foo" "baz"]})]
      ;; MUST include at least one matchable value if presence is included
      (is (= "any-matchable?" (:pred (validator-fn []))))
      (is (= "any-matchable?" (:pred (validator-fn [nil nil]))))
      ;; MUST NOT include any unmatchable values if presence is included
      (is (= "all-matchable?" (:pred (validator-fn [nil "foo" nil]))))
      ;; MUST, if all is provided, only include values in all as matchable values
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (= "only-all-values?" (:pred (validator-fn ["foo" "bar"]))))
      (is (= "only-all-values?" (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "included"
                                                  :none ["foo"]})]
      ;; MUST include at least one matchable value if presence is included
      (is (= "any-matchable?" (:pred (validator-fn []))))
      (is (= "any-matchable?" (:pred (validator-fn [nil nil]))))
      ;; MUST NOT include any unmatchable values if presence is included
      (is (= "all-matchable?" (:pred (validator-fn [nil "foo" nil]))))
      (is (= "all-matchable?" (:pred (validator-fn [nil "bar" nil]))))
      ;; MUST NOT, if none is provided, include any values in none as matchable values
      (is (nil? (validator-fn ["bar"])))
      (is (= "no-none-values?" (:pred (validator-fn ["foo" "bar"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "excluded"})]
      ;; MUST NOT include any matchable values if presence is excluded
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      (is (= "none-matchable?" (:pred (validator-fn [nil "foo" nil])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "recommended"
                                                  :any ["foo" "baz"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      ;; MUST, if any is provided, include at least one value in any as one of the matchable values
      (is (nil? (validator-fn [nil "foo" nil])))
      (is (nil? (validator-fn ["foo" "bar" "baz"])))
      (is (= "some-any-values?" (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "recommended"
                                                  :all ["foo" "baz"]})]
      (is (nil? (:pred (validator-fn []))))
      (is (nil? (:pred (validator-fn [nil nil]))))
      ;; MUST NOT, if all is provided, include any unmatchable values
      (is (= "no-unmatch-vals?" (:pred (validator-fn [nil "foo" nil]))))       
      ;; MUST, if all is provided, only include values in all as matchable values
      (is (nil? (validator-fn ["foo"])))
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (= "only-all-values?" (:pred (validator-fn ["foo" "bar"]))))
      (is (= "only-all-values?" (:pred (validator-fn ["foo" "bar" "baz"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :presence "recommended"
                                                  :none ["foo"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      (is (nil? (validator-fn [nil "bar" nil])))
      ;; MUST NOT, if none is provided, include any values in none as matchable values
      (is (nil? (validator-fn ["bar" "baz" "qux"])))
      (is (= "no-none-values?" (:pred (validator-fn ["foo"]))))
      (is (= "no-none-values?" (:pred (validator-fn ["foo" "bar"]))))
      (is (= "no-none-values?" (:pred (validator-fn [nil "foo" nil])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :any ["foo" "baz"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      ;; MUST, if any is provided, include at least one value in any as one of the matchable values
      (is (nil? (validator-fn [nil "foo" nil])))
      (is (nil? (validator-fn ["foo" "bar" "baz"])))
      (is (= "some-any-values?" (:pred (validator-fn ["bar" "qux"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :all ["foo" "baz"]})]
      (is (nil? (:pred (validator-fn []))))
      (is (nil? (:pred (validator-fn [nil nil]))))
      ;; MUST NOT, if all is provided, include any unmatchable values
      (is (= "no-unmatch-vals?" (:pred (validator-fn [nil "foo" nil]))))
      ;; MUST, if all is provided, only include values in all as matchable values
      (is (nil? (validator-fn ["foo"])))
      (is (nil? (validator-fn ["foo" "baz"])))
      (is (= "only-all-values?" (:pred (validator-fn ["foo" "bar"])))))
    (let [validator-fn (tv/create-rule-validator {:location "$.*"
                                                  :none ["foo"]})]
      (is (nil? (validator-fn [])))
      (is (nil? (validator-fn [nil nil])))
      (is (nil? (validator-fn [nil "bar" nil])))
      ;; MUST NOT, if none is provided, include any values in none as matchable values
      (is (nil? (validator-fn ["bar" "baz" "qux"])))
      (is (= "no-none-values?" (:pred (validator-fn ["foo"]))))
      (is (= "no-none-values?" (:pred (validator-fn ["foo" "bar"]))))
      (is (= "no-none-values?" (:pred (validator-fn [nil "foo" nil])))))))

(deftest create-rule-validators-test
  (testing "create-rule-validators function: Create a vector of validation
           functions based off of a Template."
    (is (s/valid? (s/coll-of fn? :kind vector?)
                  (tv/create-rule-validators ex-template)))
    (is (= 12 (count (tv/create-rule-validators ex-template))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest validate-statement-test
  (testing "validate-statement function: Validate an entire Statement!"
    (is (nil? (tv/validate-statement ex-template ex-statement-0)))
    (is (nil? (tv/validate-statement
               {"verb" "http://example.com/xapi/verbs#sent-a-statement"}
               ex-statement-1)))
    (is (nil? (tv/validate-statement
               {"verb" "http://adlnet.gov/expapi/verbs/attempted"}
               ex-statement-2)))
    (is (nil? (tv/validate-statement
               {"verb" "http://adlnet.gov/expapi/verbs/attended"
                "objectActivityType" "http://adlnet.gov/expapi/activities/meeting"
                "contextCategoryActivityType"
                ["http://example.com/expapi/activities/meetingcategory"]}
               ex-statement-3)))
    (is (nil? (tv/validate-statement
               {"verb" "http://adlnet.gov/expapi/verbs/experienced"}
               ex-statement-4)))))

(deftest valid-statement?-test
  (testing "valid-statement? function: Predicate on an entire Statement!"
    (is (tv/valid-statement? ex-template ex-statement-0))
    (is (tv/valid-statement?
          {"verb" "http://example.com/xapi/verbs#sent-a-statement"}
          ex-statement-1))
    (is (tv/valid-statement?
          {"verb" "http://adlnet.gov/expapi/verbs/attempted"}
          ex-statement-2))
    (is (tv/valid-statement?
          {"verb" "http://adlnet.gov/expapi/verbs/attended"
           "objectActivityType" "http://adlnet.gov/expapi/activities/meeting"
           "contextCategoryActivityType"
           ["http://example.com/expapi/activities/meetingcategory"]}
          ex-statement-3))
    (is (tv/valid-statement?
          {"verb" "http://adlnet.gov/expapi/verbs/experienced"}
          ex-statement-4))))

(def err-vec (tv/validate-statement ex-template ex-statement-1))

(deftest validate-statement-test-2
  (testing "validate-statement function"
    (is (= [{:pred "only-all-values?"
             :values ["http://example.com/xapi/verbs#sent-a-statement"]
             :rule {:location "$.verb.id"
                    :presence "included"
                    :all ["http://foo.org/verb"]
                    :determiningProperty "Verb"}}
            {:pred "any-matchable?"
             :values [nil]
             :rule
             {:location "$.object.definition.type"
              :presence "included"
              :all ["http://foo.org/oat"]
              :determiningProperty "objectActivityType"}}
            {:pred "any-matchable?"
             :values [nil]
             :rule
             {:location "$.context.contextActivities.parent[*].definition.type"
              :presence "included"
              :all ["http://foo.org/cpat1" "http://foo.org/cpat2"]
              :determiningProperty "contextParentActivityType"}}
            {:pred "any-matchable?"
             :values [nil]
             :rule
             {:location "$.context.contextActivities.grouping[*].definition.type"
              :presence "included"
              :all ["http://foo.org/cgat1" "http://foo.org/cgat2"]
              :determiningProperty "contextGroupingActivityType"}}
            {:pred "any-matchable?"
             :values [nil]
             :rule
             {:location "$.context.contextActivities.category[*].definition.type"
              :presence "included"
              :all ["http://foo.org/ccat1" "http://foo.org/ccat2"]
              :determiningProperty "contextCategoryActivityType"}}
            {:pred "any-matchable?"
             :values [nil]
             :rule
             {:location "$.context.contextActivities.other[*].definition.type"
              :presence "included"
              :all ["http://foo.org/coat1" "http://foo.org/coat2"]
              :determiningProperty "contextOtherActivityType"}}
            {:pred "any-matchable?"
             :values [nil]
             :rule
             {:location "$.attachments[*].usageType"
              :presence "included"
              :all ["http://foo.org/aut1" "http://foo.org/aut2"]
              :determiningProperty "attachmentUsageType"}}
            {:pred "any-matchable?"
             :values [nil]
             :rule
             {:location "$.actor.member[*].name"
              :presence "included"
              :any ["Will Hoyt" "Milt Reder" "John Newman" "Henk Reder" "Erika Lee" "Boris Boiko"]
              :none ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony" "Jeremy Gardner"]}}
            {:pred "any-matchable?"
             :values [nil nil nil nil nil]
             :rule
             {:location
              "$.object.objectType | $.context.contextActivities.parent[*].objectType | $.context.contextActivities.grouping[*].objectType | $.context.contextActivities.category[*].objectType | $.context.contextActivities.other[*].objectType"
              :presence "included"
              :all ["Activity"]}}]
           (filterv some? err-vec)))))

(deftest valid-statement?-test-2
  (testing "valid-statement? function"
    (is (not (tv/valid-statement? ex-template ex-statement-1)))))

(deftest print-error-test
  (testing "printing an error message using the print-error fn"
    (is (= (str "----- Invalid Statement -----\n"
                "Statement ID: fd41c918-b88b-4b20-a0a5-a4c32391aaa0\n"
                "Template ID: http://foo.org/example/template\n"
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
           (with-out-str (tv/print-error ex-template ex-statement-1 err-vec))))))
