(ns com.yetanalytics.persephone-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.pan.utils.json :as json]))

;; https://stackoverflow.com/questions/38880796/how-to-load-a-local-file-for-a-clojurescript-test

#?(:cljs
   (defn slurp [path]
     (let [fs (js/require "fs")]
       (.readFileSync fs path "utf8"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Will Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def will-profile (slurp "test-resources/sample_profiles/will-catch.json"))

(comment (deftest compile-profile-test
           (testing "compile-profile using Will's CATCH profile"
             (is (some? (per/compile-profile will-profile))))))

(def ex-template
  {:id
   "https://w3id.org/xapi/catch/templates#score-rubric"
   :type
   "StatementTemplate"
   :inScheme
   "https://w3id.org/xapi/catch/v1"
   :prefLabel
   {:en "score rubric"}
   :definition
   {:en "This template is for statements that are the result of a mentor reviewing and scoring somthing within the catch application."}
   :contextGroupingActivityType
   ["https://w3id.org/xapi/catch/activitytypes/domain"]
   :contextParentActivityType
   ["https://w3id.org/xapi/catch/activitytypes/competency"]
   :rules
   [{:location  "$.context.contextActivities.category['https://w3id.org/xapi/catch/v1']"
     :presence  "included"
     :scopeNote {:en "this states the the statement conforms to this profile"}}
    {:location  "$.result.score.raw"
     :presence  "included"
     :scopeNote {:en "the total number of points awarded.  This value will be determined by the point values associated with the various criteria"}}
    {:location  "$.result.score.min"
     :presence  "included"
     :scopeNote {:en "the lowest possible number of points"}}
    {:location  "$.result.score.max"
     :presence  "included"
     :scopeNote {:en "the greatest possible number of points"}}
    {:location  "$.result.success"
     :presence  "included"
     :scopeNote {:en "should be true if the score threshold was met, false otherwise"}}
    {:location  "$.result.response"
     :presence  "recommended"
     :scopeNote {:en "the mentors feedback"}}
    {:location  "$.result.extensions['https://w3id.org/xapi/catch/result-extensions/rubric-criteria']"
     :presence  "included"
     :scopeNote {:en "an array of objects where each object represents one of the criteria being scored.  each object will have a property for the name of the criteria, the possible values (ie. Emerging, Attainment, Excellence) and the selected value."}}
    {:location  "$.timestamp"
     :presence  "included"
     :scopeNote {:en "the time when the mentor submited the scored rubric"}}]})

(def ex-statement
  {"id" "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
   "timestamp" "2019-08-09T12:17:00+00:00"
   "actor"
   {"objectType" "Agent"
    "name"       "Will Hoyt"
    "mbox"       "mailto:will@yetanalytics.com"}
   "verb"
   {"id"      "https://example.com/scores"
    "display" {"en-US" "Scores"}}
   "object"
   {"objectType" "Agent"
    "name"       "Kelvin Qian"
    "mbox"       "mailto:kelvinqian@yetanalytics.com"}
   "result"
   {"score"      {"raw" 99 "min" 0 "max" 100}
    "success"    true
    "completion" true
    "response"   "Good job! Let's get dinner!"
    "timestamp"  "2019-08-10T12:18:00+00:00"}
   "context"
   {"instructor"
    {"objectType" "Agent"
     "name"       "Will Hoyt"
     "mbox"       "mailto:will@yetanalytics.com"}
    "contextActivities"
    {"parent"   [{"objectType" "Activity"
                  "id"         "https://example.com/competency/clojure-skill"
                  "definition" {"name"        {"en" "Skill in the Clojure Language"}
                                "description" {"en" "This person is skilled in Clojure."}
                                "type"        "https://w3id.org/xapi/catch/activitytypes/competency"}}]
     "grouping" [{"objectType" "Activity"
                  "id"         "https://example.com/domain/clojure"
                  "definition" {"name"        {"en" "The World of Clojure"}
                                "description" {"en" "The environment in which Clojure is used and learned."}
                                "type"        "https://w3id.org/xapi/catch/activitytypes/domain"}}]}
    "extensions"
    {"https://w3id.org/xapi/cmi5/context/extensions/sessionid" 74}}})


(deftest statement-validation-test
  (testing "validate statement using an example Template and Statement"
    (is (not (per/validate-statement-vs-template ex-template
                                                 ex-statement)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CMI Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; A short summary of the CMI profile:
;; 
;; Statement Templates:
;; - general-restrictions
;; - launched
;; - initialized
;; - completed
;; - passed
;; - failed
;; - abandoned
;; - waived
;; - terminated
;; - satisfied
;;
;; Patterns:
;; - satisfieds = satisfied*
;; - waived-session = satisfieds waived satisfieds
;; - no-result-session = launched initialized terminated-or-abandoned
;; - completion-no-success-session = launched initialized completed satisfieds terminated-or-abandoned
;; - passed-session = launched initialized completed-and-passed satisfieds terminated-or-abandoned
;; - completion-passed-session = launched initialized completed-and-passed satisfieds terminated-or-abandoned
;; - failed-session = launched initialized failed terminated-or-abandoned
;; - completion-maybe-failed-session = launched initialized completed-and-maybe-failed satisfieds terminated-or-abandoned
;; - terminated-or-abandoned = terminated | abandoned
;; - completed-and-passed = completed-then-passed | passed-then-completed
;; - completed-then-passed = completed satisfieds passed
;; - passed-then-completed = passed satisfieds completed
;; - completed-and-maybe-failed = maybe-completed-then-failed | failed-then-maybe-completed
;; - maybe-completed-then-failed = maybe-completed satisfieds failed
;; - failed-then-maybe-completed = failed maybe-completed
;; - maybe-completed = completed?
;; - typical-session = completion-maybe-failed-session | completion-passed-session | failed-session | no-result-session | passed-session | completion-no-success-session | waived-session
;; - typical-sessions = typical-session*
;; - pattern = satisfieds typical-sessions

;; To avoid the above issue with string-valued keys, we made all such rules
;; with these kinds of JSONPath strings 'recommended' instead of 'included'
(def cmi-profile (slurp "test-resources/sample_profiles/cmi5.json"))
(def cmi-templates (:templates (json/convert-json cmi-profile "_")))

; Note: we need to add ['*'] to the original JSONPath specs in the "all" rules.
(def launched-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/launched")
      (update "result" dissoc "score")
      (update "result" dissoc "success")
      (update "result" dissoc "completion")
      (assoc-in ["context" "extensions" "https://w3id.org/xapi/cmi5/context/extensions/launchmode"]
                ["Normal" "Browse" "Review"])
      (assoc-in ["context" "extensions" "https://w3id.org/xapi/cmi5/context/extensions/launchurl"]
                "https://http://adlnet.gov/launchurl")
      (assoc-in ["context" "extensions" "https://w3id.org/xapi/cmi5/context/extensions/moveon"]
                ["Passed" "Completed" "CompletedAndPassed" "CompletedOrPassed" "NotApplicable"])
      (assoc-in ["context" "extensions" "https://w3id.org/xapi/cmi5/context/extensions/launchparameters"]
                {"parameter" true})))

(def initialized-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/initialized")
      (update "result" dissoc "score")
      (update "result" dissoc "success")
      (update "result" dissoc "completion")))

(def completed-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/completed")
      (update "result" dissoc "score")
      (update "result" dissoc "success")
      (assoc-in ["result" "completion"] true)
      (assoc-in ["result" "duration"] "PT4H35M59.14S")
      (assoc-in ["context" "contextActivities" "category"]
                [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def passed-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/passed")
      (update "result" dissoc "completion")
      (assoc-in ["result" "duration"] "PT4H35M59.14S")
      (assoc-in ["context" "contextActivities" "category"]
                [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def failed-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/failed")
      (assoc-in ["result" "success"] false)
      (update "result" dissoc "completion")
      (assoc-in ["result" "duration"] "P8W")
      (assoc-in ["context" "contextActivities" "category"]
                [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def abandoned-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "https://w3id.org/xapi/adl/verbs/abandoned")
      (update "result" dissoc "score")
      (update "result" dissoc "success")
      (update "result" dissoc "completion")
      (assoc-in ["result" "duration"] "P8W")))

(def waived-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/waived")
      (update "result" dissoc "score")
      (assoc-in ["result" "extensions" "https://w3id.org/xapi/cmi5/result/extensions/reason"]
                {"en-US" "Prerequisites not met."})
      (assoc-in ["context" "contextActivities" "category"]
                [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def terminated-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/terminated")
      (update "result" dissoc "score")
      (update "result" dissoc "success")
      (update "result" dissoc "completion")
      (assoc-in ["result" "duration"] "P8W")))

(def satisfied-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/satisfied")
      (update "result" dissoc "score")
      (update "result" dissoc "success")
      (update "result" dissoc "completion")
      (assoc-in ["object" "definition" "type"]
                "https://w3id.org/xapi/cmi5/activitytype/course")))

(def cmi-tmpl-0 (get cmi-templates 0))
(def cmi-tmpl-1 (get cmi-templates 1))
(def cmi-tmpl-2 (get cmi-templates 2))
(def cmi-tmpl-3 (get cmi-templates 3))
(def cmi-tmpl-4 (get cmi-templates 4))
(def cmi-tmpl-5 (get cmi-templates 5))
(def cmi-tmpl-6 (get cmi-templates 6))
(def cmi-tmpl-7 (get cmi-templates 7))
(def cmi-tmpl-8 (get cmi-templates 8))
(def cmi-tmpl-9 (get cmi-templates 9))

(deftest cmi-statements-test
  (testing "validating statements from the cmi5 profile"
    (is (per/validate-statement-vs-template cmi-tmpl-0 ex-statement))
    (is (per/validate-statement-vs-template cmi-tmpl-1 launched-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-2 initialized-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-3 completed-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-4 passed-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-5 failed-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-6 abandoned-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-7 waived-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-8 terminated-stmt))
    (is (per/validate-statement-vs-template cmi-tmpl-9 satisfied-stmt))))

(deftest cmi-statement-test-2
  (testing "calling validate-statement-vs-template with different modes"
    ;; Valid Statement
    (is (= ex-statement
           (per/validate-statement-vs-template cmi-tmpl-0
                                               ex-statement
                                               :fn-type :option)))
    (is (nil? (per/validate-statement-vs-template cmi-tmpl-0
                                                  ex-statement
                                                  :fn-type :result)))
    (is (nil? (per/validate-statement-vs-template cmi-tmpl-0
                                                  ex-statement
                                                  :fn-type :assertion)))
    (is (= "" (with-out-str
                (per/validate-statement-vs-template cmi-tmpl-0
                                                    ex-statement
                                                    :fn-type :printer))))
    ;; Invalid Statement
    (is (nil? (per/validate-statement-vs-template cmi-tmpl-1
                                                  ex-statement
                                                  :fn-type :option)))
    (is (some? (per/validate-statement-vs-template cmi-tmpl-1
                                                   ex-statement
                                                   :fn-type :result)))
    (is (= {:pred   "only-all-values?"
            :values ["https://example.com/scores"]
            :rule
            {:location "$.verb.id"
             :presence "included"
             :all      ["http://adlnet.gov/expapi/verbs/launched"]
             :determiningProperty "Verb"}}
           (first (per/validate-statement-vs-template cmi-tmpl-1
                                                      ex-statement
                                                      :fn-type :result))))
    (is (= (per/validate-statement-vs-template cmi-tmpl-1
                                               ex-statement
                                               :fn-type :result)
           (try (per/validate-statement-vs-template cmi-tmpl-1
                                                    ex-statement
                                                    :fn-type :assertion)
                (catch #?(:clj Exception :cljs js/Error) e (-> e ex-data :errors)))))
    (is (not= "" (with-out-str
                   (per/validate-statement-vs-template cmi-tmpl-1
                                                       ex-statement
                                                       :fn-type :printer))))))

(deftest cmi-statement-test-3
  (testing "validating statements from cmi5 profile that exclude moveon"
    (is (not (per/validate-statement-vs-template
              (get cmi-templates 6)
              (assoc-in
               abandoned-stmt
               ["context" "contextActivities" "category"]
               [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))
    (is (not (per/validate-statement-vs-template
              (get cmi-templates 8)
              (assoc-in
               terminated-stmt
               ["context" "contextActivities" "category"]
               [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))))

(deftest cmi-statements-vs-profile-test
  (testing "validating statements from the cmi5 profile, against the whole
            profile"
    ;; Valid Statement
    (is (per/validate-statement-vs-profile cmi-profile
                                           ex-statement
                                           :fn-type :predicate
                                           :validate-profile? false))
    (is (= ex-statement
           (per/validate-statement-vs-profile cmi-profile
                                              ex-statement
                                              :fn-type :option
                                              :validate-profile? false)))
    (is (nil? (per/validate-statement-vs-profile cmi-profile
                                                 ex-statement
                                                 :fn-type :result
                                                 :validate-profile? false)))
    (is (nil? (per/validate-statement-vs-profile cmi-profile
                                                 ex-statement
                                                 :fn-type :assertion
                                                 :validate-profile? false)))
    (is (= ["https://w3id.org/xapi/cmi5#generalrestrictions"]
           (per/validate-statement-vs-profile cmi-profile
                                              ex-statement
                                              :fn-type :templates
                                              :validate-profile? false)))
    ;; Invalid Statement (just an empty map)
    (is (not (per/validate-statement-vs-profile cmi-profile
                                                {}
                                                :fn-type :predicate
                                                :validate-profile? false)))
    (is (nil? (per/validate-statement-vs-profile cmi-profile
                                                 {}
                                                 :fn-type :option
                                                 :validate-profile? false)))
    (is (= [] (per/validate-statement-vs-profile cmi-profile
                                                 {}
                                                 :fn-type :templates
                                                 :validate-profile? false)))
    (is (= 10 (count
               (per/validate-statement-vs-profile cmi-profile
                                                  {}
                                                  :fn-type :result
                                                  :validate-profile? false))))
    (is (= {:pred   "any-matchable?"
            :values [nil]
            :rule   {:location "$.id" :presence "included"}}
           (-> (per/validate-statement-vs-profile cmi-profile
                                                  {}
                                                  :fn-type :result
                                                  :validate-profile? false)
               first
               first)))
    (is (= (per/validate-statement-vs-profile cmi-profile
                                              {}
                                              :fn-type :result
                                              :validate-profile? false)
           (try (per/validate-statement-vs-profile cmi-profile
                                                   {}
                                                   :fn-type :result
                                                   :validate-profile? false)
                (catch #?(:clj Exception :cljs js/Error) e (-> e ex-data :errors)))))))

(def cmi-fsm (first (per/compile-profile cmi-profile)))
(def rns-cmi (partial per/match-next-statement* cmi-fsm))

(deftest pattern-validation-tests
  (testing "Testing validation of a stream of Statements using Patterns from the cmi5 Profile."
    (is (:rejected? (rns-cmi nil ex-statement)))
    ;; Accepted by 'satisfied' Template
    (is (not (:rejected? (rns-cmi nil satisfied-stmt))))
    ;; Does not satifiy all rules in the 'satisfied' Template
    (is (:rejected?
         (rns-cmi nil (assoc-in ex-statement
                                ["verb" "id"]
                                "http://adlnet.gov/expapi/verbs/satisfied"))))
    ;; Forgot initialized-stmt
    (is (:rejected? (-> nil
                  (rns-cmi satisfied-stmt)
                  (rns-cmi launched-stmt)
                  (rns-cmi failed-stmt))))
    ;; Session not completed yet
    (is (not (:accepted? (-> nil
                             (rns-cmi satisfied-stmt)
                             (rns-cmi launched-stmt)
                             (rns-cmi initialized-stmt)
                             (rns-cmi failed-stmt)
                             (rns-cmi satisfied-stmt)))))
    ;; Just a bunch of satisfieds
    (is (:accepted? (-> nil
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi satisfied-stmt))))
    ;; Waive, then pass
    (is (:accepted? (-> nil
                        (rns-cmi waived-stmt)
                        (rns-cmi launched-stmt)
                        (rns-cmi initialized-stmt)
                        (rns-cmi passed-stmt)
                        (rns-cmi completed-stmt)
                        (rns-cmi terminated-stmt))))
    ;; Completed, then failed (oof!)
    (is (:accepted? (-> nil
                        (rns-cmi launched-stmt)
                        (rns-cmi initialized-stmt)
                        (rns-cmi completed-stmt)
                        (rns-cmi failed-stmt)
                        (rns-cmi abandoned-stmt))))
    ;; Just straight up failed
    (is (:accepted? (-> nil
                        (rns-cmi launched-stmt)
                        (rns-cmi initialized-stmt)
                        (rns-cmi failed-stmt)
                        (rns-cmi abandoned-stmt))))
    ;; Failed, then waived, then finally passed (yay!)
    (is (:accepted? (-> nil
                        (rns-cmi satisfied-stmt)
                        (rns-cmi launched-stmt)
                        (rns-cmi initialized-stmt)
                        (rns-cmi failed-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi abandoned-stmt)
                        (rns-cmi waived-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi launched-stmt)
                        (rns-cmi initialized-stmt)
                        (rns-cmi completed-stmt)
                        (rns-cmi satisfied-stmt)
                        (rns-cmi passed-stmt)
                        (rns-cmi terminated-stmt))))))

(def rns-cmi-2 (partial per/match-next-statement cmi-fsm))

(def satisfied-stmt-2
  (assoc-in satisfied-stmt ["context" "registration"] "registration-2"))

(deftest match-next-statement-test
  (testing "the match-next-statement function"
    (is (:accepted? (-> {}
                        (rns-cmi-2 satisfied-stmt)
                        (rns-cmi-2 launched-stmt)
                        (rns-cmi-2 initialized-stmt)
                        (rns-cmi-2 failed-stmt)
                        (rns-cmi-2 satisfied-stmt)
                        (rns-cmi-2 abandoned-stmt)
                        (rns-cmi-2 waived-stmt)
                        (rns-cmi-2 satisfied-stmt)
                        (rns-cmi-2 launched-stmt)
                        (rns-cmi-2 initialized-stmt)
                        (rns-cmi-2 completed-stmt)
                        (rns-cmi-2 satisfied-stmt)
                        (rns-cmi-2 passed-stmt)
                        (rns-cmi-2 terminated-stmt)
                        :no-registration)))
    (is (:accepted? (-> {}
                        (rns-cmi-2 satisfied-stmt)
                        (rns-cmi-2 satisfied-stmt-2)
                        (get "registration-2"))))
    (is (:accepted? (-> {}
                        (rns-cmi-2 satisfied-stmt)
                        (rns-cmi-2 satisfied-stmt-2)
                        (rns-cmi-2 launched-stmt)
                        (rns-cmi-2 satisfied-stmt-2)
                        (rns-cmi-2 initialized-stmt)
                        (rns-cmi-2 satisfied-stmt-2)
                        (rns-cmi-2 failed-stmt)
                        (rns-cmi-2 satisfied-stmt-2)
                        (rns-cmi-2 abandoned-stmt)
                        (rns-cmi-2 satisfied-stmt-2)
                        (get "registration-2"))))))

;; TODO: Add DATASIM tests
