(ns com.yetanalytics.persephone-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone :as p]
            [com.yetanalytics.persephone.utils.json :as jsn]))

;; https://stackoverflow.com/questions/38880796/how-to-load-a-local-file-for-a-clojurescript-test

#?(:cljs
   (defn slurp [path]
     (let [fs (js/require "fs")]
       (.readFileSync fs path "utf8"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Definitions + Basic test
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
    {"category" [{"id" "https://w3id.org/xapi/cmi5/v1.0"}] ; For cmi5 tests
     "parent"   [{"objectType" "Activity"
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
    (is (not (p/validate-statement-vs-template
              (p/template->validator ex-template)
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
(def cmi-templates (:templates (jsn/json->edn cmi-profile :keywordize? true)))

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
      (update-in ["context" "contextActivities" "category"]
                 conj
                 {"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"})))

(def passed-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/passed")
      (update "result" dissoc "completion")
      (assoc-in ["result" "duration"] "PT4H35M59.14S")
      (update-in ["context" "contextActivities" "category"]
                 conj
                 {"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"})))

(def failed-stmt
  (-> ex-statement
      (assoc-in ["verb" "id"] "http://adlnet.gov/expapi/verbs/failed")
      (assoc-in ["result" "success"] false)
      (update "result" dissoc "completion")
      (assoc-in ["result" "duration"] "P8W")
      (update-in ["context" "contextActivities" "category"]
                 conj
                 {"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"})))

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
      (update-in ["context" "contextActivities" "category"]
                 conj
                 {"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"})))

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

(def cmi-tmpl-0 (p/template->validator (get cmi-templates 0)))
(def cmi-tmpl-1 (p/template->validator (get cmi-templates 1)))
(def cmi-tmpl-2 (p/template->validator (get cmi-templates 2)))
(def cmi-tmpl-3 (p/template->validator (get cmi-templates 3)))
(def cmi-tmpl-4 (p/template->validator (get cmi-templates 4)))
(def cmi-tmpl-5 (p/template->validator (get cmi-templates 5)))
(def cmi-tmpl-6 (p/template->validator (get cmi-templates 6)))
(def cmi-tmpl-7 (p/template->validator (get cmi-templates 7)))
(def cmi-tmpl-8 (p/template->validator (get cmi-templates 8)))
(def cmi-tmpl-9 (p/template->validator (get cmi-templates 9)))

(deftest cmi-statements-test
  (testing "validating statements from the cmi5 profile"
    (is (p/validate-statement-vs-template cmi-tmpl-0 ex-statement))
    (is (p/validate-statement-vs-template cmi-tmpl-1 launched-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-2 initialized-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-3 completed-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-4 passed-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-5 failed-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-6 abandoned-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-7 waived-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-8 terminated-stmt))
    (is (p/validate-statement-vs-template cmi-tmpl-9 satisfied-stmt))))

(deftest cmi-statement-test-2
  (testing "calling validate-statement-vs-template with different modes"
    ;; Valid Statement
    (is (= ex-statement
           (p/validate-statement-vs-template
            cmi-tmpl-0
            ex-statement
            :fn-type :option)))
    (is (nil? (p/validate-statement-vs-template
               cmi-tmpl-0
               ex-statement
               :fn-type :result)))
    (is (nil? (p/validate-statement-vs-template
               cmi-tmpl-0
               ex-statement
               :fn-type :assertion)))
    (is (= "" (with-out-str
                (p/validate-statement-vs-template
                 cmi-tmpl-0
                 ex-statement
                 :fn-type :printer))))
    ;; Invalid Statement
    (is (nil? (p/validate-statement-vs-template
               cmi-tmpl-1
               ex-statement
               :fn-type :option)))
    (is (some? (p/validate-statement-vs-template
                cmi-tmpl-1
                ex-statement
                :fn-type :result)))
    (is (= {:pred :every-val-present?
            :vals ["https://example.com/scores"]
            :rule {:location             "$.verb.id"
                   :prop-vals            ["http://adlnet.gov/expapi/verbs/launched"]
                   :determining-property "Verb"}
            :temp "https://w3id.org/xapi/cmi5#launched"
            :stmt "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"}
           (first (p/validate-statement-vs-template
                   cmi-tmpl-1
                   ex-statement
                   :fn-type :result))))
    (is (= (p/validate-statement-vs-template
            cmi-tmpl-1
            ex-statement
            :fn-type :result)
           (try (p/validate-statement-vs-template
                 cmi-tmpl-1
                 ex-statement
                 :fn-type :assertion)
                (catch #?(:clj Exception :cljs js/Error) e (-> e ex-data :errors)))))
    (is (not= "" (with-out-str
                   (p/validate-statement-vs-template
                    cmi-tmpl-1
                    ex-statement
                    :fn-type :printer))))))

(deftest cmi-statement-test-3
  (testing "validating statements from cmi5 profile that exclude moveon"
    (is (not (p/validate-statement-vs-template
              (p/template->validator (get cmi-templates 6))
              (assoc-in
               abandoned-stmt
               ["context" "contextActivities" "category"]
               [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))
    (is (not (p/validate-statement-vs-template
              (p/template->validator (get cmi-templates 8))
              (assoc-in
               terminated-stmt
               ["context" "contextActivities" "category"]
               [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))))

(deftest cmi-statements-vs-profile-test
  (testing "validating statements from the cmi5 profile, against the whole
            profile"
    ;; Valid Statement
    (is (p/validate-statement-vs-profile
         (p/profile->validator cmi-profile)
         ex-statement
         :fn-type :predicate))
    (is (= ex-statement
           (p/validate-statement-vs-profile
            (p/profile->validator cmi-profile)
            ex-statement
            :fn-type :option)))
    (is (nil? (p/validate-statement-vs-profile
               (p/profile->validator cmi-profile)
               ex-statement
               :fn-type :result)))
    (is (nil? (p/validate-statement-vs-profile
               (p/profile->validator cmi-profile)
               ex-statement
               :fn-type :assertion)))
    (is (= ["https://w3id.org/xapi/cmi5#generalrestrictions"]
           (p/validate-statement-vs-profile
            (p/profile->validator cmi-profile)
            ex-statement
            :fn-type :templates)))
    ;; Invalid Statement (just an empty map)
    (is (not (p/validate-statement-vs-profile
              (p/profile->validator cmi-profile)
              {}
              :fn-type :predicate)))
    (is (nil? (p/validate-statement-vs-profile
               (p/profile->validator cmi-profile)
               {}
               :fn-type :option)))
    (is (= [] (p/validate-statement-vs-profile
               (p/profile->validator cmi-profile)
               {}
               :fn-type :templates)))
    (is (= 10 (count
               (p/validate-statement-vs-profile
                (p/profile->validator cmi-profile)
                {}
                :fn-type :result))))
    (is (= {:pred :any-matchable?
            :vals [nil]
            :rule {:location "$.id"
                   :presence "included"}
            :temp "https://w3id.org/xapi/cmi5#generalrestrictions"
            :stmt nil}
           (-> (p/validate-statement-vs-profile
                (p/profile->validator cmi-profile)
                {}
                :fn-type :result)
               (get "https://w3id.org/xapi/cmi5#generalrestrictions")
               first)))
    (is (= (p/validate-statement-vs-profile
            (p/profile->validator cmi-profile)
            {}
            :fn-type :result)
           (try (p/validate-statement-vs-profile
                 (p/profile->validator cmi-profile)
                 {}
                 :fn-type :result)
                (catch #?(:clj Exception :cljs js/Error) e (-> e ex-data :errors)))))))

(def cmi-fsm-map (p/profile->fsms cmi-profile))
(def cmi-fsm (get cmi-fsm-map "https://w3id.org/xapi/cmi5#toplevel"))
(def match-cmi (partial p/match-statement-vs-pattern cmi-fsm))

(deftest pattern-validation-tests
  (testing "Testing validation of a stream of Statements using Patterns from the cmi5 Profile."
    (is (empty? (:states (match-cmi nil ex-statement))))
    ;; Accepted by 'satisfied' Template
    (is (not (:rejected? (match-cmi nil satisfied-stmt))))
    ;; Does not satifiy all rules in the 'satisfied' Template - rejected
    (is (empty? (:states
                 (match-cmi nil (assoc-in
                               ex-statement
                               ["verb" "id"]
                               "http://adlnet.gov/expapi/verbs/satisfied")))))
    ;; Forgot initialized-stmt - rejected
    (is (empty? (-> nil
                    (match-cmi satisfied-stmt)
                    (match-cmi launched-stmt)
                    (match-cmi failed-stmt)
                    :states)))
    ;; Session not completed yet
    (is (not (:accepted? (-> nil
                             (match-cmi satisfied-stmt)
                             (match-cmi launched-stmt)
                             (match-cmi initialized-stmt)
                             (match-cmi failed-stmt)
                             (match-cmi satisfied-stmt)))))
    ;; Just a bunch of satisfieds
    (is (:accepted? (-> nil
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi satisfied-stmt))))
    ;; Waive, then pass
    (is (:accepted? (-> nil
                        (match-cmi waived-stmt)
                        (match-cmi launched-stmt)
                        (match-cmi initialized-stmt)
                        (match-cmi passed-stmt)
                        (match-cmi completed-stmt)
                        (match-cmi terminated-stmt))))
    ;; Completed, then failed (oof!)
    (is (:accepted? (-> nil
                        (match-cmi launched-stmt)
                        (match-cmi initialized-stmt)
                        (match-cmi completed-stmt)
                        (match-cmi failed-stmt)
                        (match-cmi abandoned-stmt))))
    ;; Just straight up failed
    (is (:accepted? (-> nil
                        (match-cmi launched-stmt)
                        (match-cmi initialized-stmt)
                        (match-cmi failed-stmt)
                        (match-cmi abandoned-stmt))))
    ;; Failed, then waived, then finally passed (yay!)
    (is (:accepted? (-> nil
                        (match-cmi satisfied-stmt)
                        (match-cmi launched-stmt)
                        (match-cmi initialized-stmt)
                        (match-cmi failed-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi abandoned-stmt)
                        (match-cmi waived-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi launched-stmt)
                        (match-cmi initialized-stmt)
                        (match-cmi completed-stmt)
                        (match-cmi satisfied-stmt)
                        (match-cmi passed-stmt)
                        (match-cmi terminated-stmt))))))

(def match-cmi-2 (partial p/match-statement-vs-profile cmi-fsm-map))

(def registration-2 "c816c015-e07f-46de-aaa5-47abd9a57e06")
(def registration-3 "c4605182-13bc-47f2-8ccf-3a4d6342926d")
(def sub-reg-1 "7cfef0b6-aea3-45fb-802a-246f1d6d18a3")
(def sub-reg-2 "793c1aec-20e5-4c34-bf74-eae2370e8084")
(def sub-reg-3 "ed0cdb5a-1728-42c5-8fc0-20fc89c5a1c9")

(def satisfied-stmt-2
  (assoc-in satisfied-stmt ["context" "registration"] registration-2))

(def satisfied-stmt-3
  (-> satisfied-stmt
      (assoc-in ["context" "registration"] registration-3)
      (assoc-in ["context" "extensions" p/subreg-iri]
                [{"profile"         "https://w3id.org/xapi/cmi5/v1.0"
                  "subregistration" sub-reg-1}])))

(def satisfied-stmt-4
  (-> satisfied-stmt
      (assoc-in ["context" "registration"] registration-3)
      (assoc-in ["context" "extensions" p/subreg-iri]
                [{"profile"         "https://example.org/profile"
                  "subregistration" sub-reg-3}
                 {"profile"         "https://w3id.org/xapi/cmi5/v1.0"
                  "subregistration" sub-reg-2}])))

(deftest match-statement-vs-profile-test
  (testing "the match-statement-vs-profile function w/ registrations."
    (is (= 2 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (get :no-registration)
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (get registration-2)
                 count)))
    (is (:accepted? (-> {}
                        (match-cmi-2 satisfied-stmt)
                        (match-cmi-2 satisfied-stmt-2)
                        (get registration-2)
                        (get "https://w3id.org/xapi/cmi5#toplevel"))))
    (is (:accepted? (-> {}
                        (match-cmi-2 satisfied-stmt)
                        (match-cmi-2 launched-stmt)
                        (match-cmi-2 initialized-stmt)
                        (match-cmi-2 failed-stmt)
                        (match-cmi-2 satisfied-stmt)
                        (match-cmi-2 abandoned-stmt)
                        (match-cmi-2 waived-stmt)
                        (match-cmi-2 satisfied-stmt)
                        (match-cmi-2 launched-stmt)
                        (match-cmi-2 initialized-stmt)
                        (match-cmi-2 completed-stmt)
                        (match-cmi-2 satisfied-stmt)
                        (match-cmi-2 passed-stmt)
                        (match-cmi-2 terminated-stmt)
                        (get :no-registration)
                        (get "https://w3id.org/xapi/cmi5#toplevel"))))
    (is (:accepted? (-> {}
                        (match-cmi-2 satisfied-stmt)
                        (match-cmi-2 satisfied-stmt-2)
                        (match-cmi-2 launched-stmt)
                        (match-cmi-2 satisfied-stmt-2)
                        (match-cmi-2 initialized-stmt)
                        (match-cmi-2 satisfied-stmt-2)
                        (match-cmi-2 failed-stmt)
                        (match-cmi-2 satisfied-stmt-2)
                        (match-cmi-2 abandoned-stmt)
                        (match-cmi-2 satisfied-stmt-2)
                        (get registration-2)
                        (get "https://w3id.org/xapi/cmi5#toplevel")))))
  (testing "the match-statement-vs-profile function w/ sub-registrations."
    (is (= 4 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 (get [registration-3 sub-reg-1])
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 (get [registration-3 sub-reg-2])
                 count)))
    (is (= 0 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 (get registration-3)
                 count)))
    (is (:accepted? (-> {}
                        (match-cmi-2 satisfied-stmt-3)
                        (match-cmi-2 satisfied-stmt-4)
                        (match-cmi-2 launched-stmt)
                        (match-cmi-2 satisfied-stmt-4)
                        (match-cmi-2 initialized-stmt)
                        (match-cmi-2 satisfied-stmt-4)
                        (match-cmi-2 failed-stmt)
                        (match-cmi-2 satisfied-stmt-4)
                        (match-cmi-2 abandoned-stmt)
                        (match-cmi-2 satisfied-stmt-4)
                        (get [registration-3 sub-reg-2])
                        (get "https://w3id.org/xapi/cmi5#toplevel"))))))

(defn- match-cmi-ex
  [statement]
  (try (match-cmi {} statement)
       (catch #?(:clj Exception :cljs js/Error) e
         (-> e ex-data :kind))))

(defn- match-cmi-2-ex
  [statement]
  (try (match-cmi-2 {} statement)
       (catch #?(:clj Exception :cljs js/Error) e
         (-> e ex-data :kind))))

(deftest pattern-exceptions-test
  (testing "match-statement-vs-pattern exceptions"
    (is (= ::p/missing-profile-reference
           (match-cmi-ex (assoc-in satisfied-stmt
                                   ["context" "contextActivities" "category"]
                                   [])))))
  (testing "match-statement-vs-profile exceptions"
    (is (= ::p/missing-profile-reference
           (match-cmi-2-ex (assoc-in satisfied-stmt
                                     ["context" "contextActivities" "category"]
                                     []))))
    (is (= ::p/invalid-subreg-nonconformant
           (match-cmi-2-ex (assoc-in satisfied-stmt-3
                                     ["context" "extensions" p/subreg-iri]
                                     []))))
    (is (= ::p/invalid-subreg-no-registration
           (match-cmi-2-ex (update satisfied-stmt-3
                                   "context"
                                   dissoc
                                   "registration"))))))

(defn- create-statement-batch
  [statement-coll]
  (loop [stmt-coll  statement-coll
         stmt-coll' []
         idx        10] ; keep two digits
    (if-let [stmt (first stmt-coll)]
      (let [ts    (str "2019-08-10T12:" idx ":00+00:00") ; increment seconds
            stmt' (assoc stmt "timestamp" ts)]
        (recur (rest stmt-coll)
               (conj stmt-coll' stmt')
               (inc idx)))
      stmt-coll')))

(def ^:private statement-batch
  (create-statement-batch [satisfied-stmt
                           launched-stmt
                           initialized-stmt
                           failed-stmt
                           satisfied-stmt
                           abandoned-stmt
                           waived-stmt
                           satisfied-stmt
                           launched-stmt
                           initialized-stmt
                           completed-stmt
                           satisfied-stmt
                           passed-stmt
                           terminated-stmt]))

(deftest statement-batch-test
  (testing "match-statement-batch-vs-pattern function"
    (is (:accepted? (p/match-statement-batch-vs-pattern
                     cmi-fsm
                     nil
                     statement-batch)))
    (is (:accepted? (p/match-statement-batch-vs-pattern
                     cmi-fsm
                     nil
                     (shuffle statement-batch)))))
  (testing "match-statement-batch-vs-profile function"
    (is (:accepted? (get-in (p/match-statement-batch-vs-profile
                             cmi-fsm-map
                             {}
                             statement-batch)
                            [:no-registration
                             "https://w3id.org/xapi/cmi5#toplevel"])))
    (is (:accepted? (get-in (p/match-statement-batch-vs-profile
                             cmi-fsm-map
                             {}
                             (shuffle statement-batch))
                            [:no-registration
                             "https://w3id.org/xapi/cmi5#toplevel"])))
    (is (:accepted? (get-in (p/match-statement-batch-vs-profile
                             cmi-fsm-map
                             {}
                             (create-statement-batch [satisfied-stmt-3
                                                      satisfied-stmt-4
                                                      launched-stmt
                                                      satisfied-stmt-4
                                                      initialized-stmt
                                                      satisfied-stmt-4
                                                      failed-stmt
                                                      satisfied-stmt-4
                                                      abandoned-stmt
                                                      satisfied-stmt-4]))
                            [[registration-3 sub-reg-2]
                             "https://w3id.org/xapi/cmi5#toplevel"])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CATCH Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def catch-profile (slurp "test-resources/sample_profiles/catch.json"))

(comment ;; For pattern https://w3id.org/xapi/catch/patterns#f1-1-01-completion
  "https://w3id.org/xapi/catch/templates#professional-development-event"
  "https://w3id.org/xapi/catch/templates#wrote-reflection"
  "https://w3id.org/xapi/catch/templates#f1-1-01"
  "https://w3id.org/xapi/catch/templates#system-notification-submission"
  "https://w3id.org/xapi/catch/templates#system-notification-progression"
  "https://w3id.org/xapi/catch/templates#system-notification-completion"
  "https://w3id.org/xapi/catch/templates#activity-completion")

(def statement-basics
  {"id"        "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
   "timestamp" "2019-08-09T12:17:00+00:00"
   "actor"     {"objectType" "Agent"
                "name"       "Will Hoyt"
                "mbox"       "mailto:will@yetanalytics.com"}
   "verb"      {"id"      "https://example.com/basic-verb"
                "display" {"en-US" "Scores"}}
   "object"    {"objectType" "Activity"
                "id"         "https://foo.org/some-activity#code"
                "definition" {"name" {"en-US" "Some Activity"}
                              "type" "https://foo.org/some-activity"}}
   "context"   {"contextActivities"
                {"category" [{"objectType" "Activity"
                              "id"         "https://w3id.org/xapi/catch/v1"}]
                 "parent"   [{"objectType" "Activity"
                              "id"         "https://example.com/competency/clojure-skill"
                              "definition" {"name"        {"en" "Skill in the Clojure Language"}
                                            "description" {"en" "This person is skilled in Clojure."}
                                            "type"        "https://w3id.org/xapi/catch/activitytypes/competency"}}]
                 "grouping" [{"objectType" "Activity"
                              "id"         "https://example.com/domain/clojure"
                              "definition" {"name"        {"en" "The World of Clojure"}
                                            "description" {"en" "The environment in which Clojure is used and learned."}
                                            "type"        "https://w3id.org/xapi/catch/activitytypes/domain"}}]}}})

(def presented-advocacy-stmt
  (-> statement-basics
      (assoc-in ["id"] "presented-advocacy-stmt")
      (assoc-in ["verb"] {"id" "https://w3id.org/xapi/catch/verbs/presented"
                          "display" {"en-US" "Presented"}})
      (assoc-in ["object"]
                {"objectType" "Activity"
                 "id" "https://w3id.org/xapi/catch/activitytypes/advocacy-event#1"
                 "definition" {"name" {"en-US" "Advocacy Event 1"}
                               "type" "https://w3id.org/xapi/catch/activitytypes/advocacy-event"}})
      (assoc-in ["context" "extensions" "https://w3id.org/xapi/catch/context-extensions/advocacy-event"]
                "Some metadata")))

(def attended-advocacy-stmt
  (-> statement-basics
      (assoc-in ["id"] "attended-advocacy-stmt")
      (assoc-in ["verb"] {"id" "http://adlnet.gov/expapi/verbs/attended"
                          "display" {"en-US" "Attended"}})
      (assoc-in ["object"]
                {"objectType" "Activity"
                 "id" "https://w3id.org/xapi/catch/activitytypes/advocacy-event#1"
                 "definition" {"name" {"en-US" "Advocacy Event 1"}
                               "type" "https://w3id.org/xapi/catch/activitytypes/advocacy-event"}})
      (assoc-in ["context" "extensions" "https://w3id.org/xapi/catch/context-extensions/advocacy-event"]
                "Some metadata")))

(def test-stmt-1
  (-> statement-basics
      (assoc-in ["id"] "test-stmt-1")
      (assoc-in ["verb"]
                {"objectType" "Verb"
                 "id" "https://w3id.org/xapi/catch/verbs/provided"
                 "display" {"en-US" "Provided"}})
      (assoc-in ["object"]
                {"objectType" "Activity"
                 "id" "foo"
                 "definition" {"name" "Some file name"
                               "type" "https://w3id.org/xapi/catch/activitytypes/evidence"}})
      (assoc-in ["context" "extensions" "https://w3id.org/xapi/catch/context-extensions/advocacy-event"]
                "Insert meta-data here")
      (assoc-in ["context" "platform"] "Some platform")
      (assoc-in ["context" "statement"]
                {"objectType" "StatementRef"
                 "id" "presented-advocacy-stmt"})))

(def test-stmt-2
  (-> test-stmt-1
      (assoc-in ["id"] "test-stmt-2")
      (assoc-in ["context" "statement" "id"] "attended-advocacy-stmt")))

(def test-stmt-3
  (-> test-stmt-1
      (assoc-in ["id"] "test-stmt-3")
      (assoc-in ["context" "statement" "id"] "unknown-stmt")))

(def will-stmt-batch
  [presented-advocacy-stmt
   attended-advocacy-stmt
   test-stmt-1
   test-stmt-2
   test-stmt-3])

(def catch-id-temp-map (p/profile->id-template-map catch-profile))
(def catch-id-stmt-map (p/statement-batch->id-statement-map will-stmt-batch))
(def catch-compiled-profile
  (p/profile->validator catch-profile
                        :statement-ref-fns {:get-statement-fn catch-id-stmt-map
                                            :get-template-fn  catch-id-temp-map}
                        :validate-profile? false))

(def evidence-advocacy-provide-irl
  "https://w3id.org/xapi/catch/templates#evidence-advocacy-provide")

(deftest statement-ref-test
  (testing "validation of Statements with (Context) Statement Refs"
    (is (p/validate-statement-vs-template
         (some (fn [{id :id :as temp}]
                 (when (= id evidence-advocacy-provide-irl)
                   temp))
               catch-compiled-profile)
         test-stmt-1))
    (is (p/validate-statement-vs-template
         (some (fn [{id :id :as temp}]
                 (when (= id evidence-advocacy-provide-irl)
                   temp))
               catch-compiled-profile)
         test-stmt-2))
    (is (not (p/validate-statement-vs-template
              (some (fn [{id :id :as temp}]
                      (when (= id evidence-advocacy-provide-irl)
                        temp))
                    catch-compiled-profile)
              test-stmt-3)))
    (is (= (str "----- Invalid Statement -----\n"
                "Template ID:  https://w3id.org/xapi/catch/templates#evidence-advocacy-provide\n"
                "Statement ID: test-stmt-3\n"
                "\n"
                "Cannot find Statement given by the id in the Context Statement Ref:\n"
                "unknown-stmt\n"
                "-----------------------------\n"
                "Total errors found: 1\n"
                "\n")
           (with-out-str
             (p/validate-statement-vs-template
              (some (fn [{id :id :as temp}]
                      (when (= id "https://w3id.org/xapi/catch/templates#evidence-advocacy-provide")
                        temp))
                    catch-compiled-profile)
              test-stmt-3
              :fn-type :printer))))
    (is (p/validate-statement-vs-profile catch-compiled-profile
                                         test-stmt-1))
    (is (p/validate-statement-vs-profile catch-compiled-profile
                                         test-stmt-2))
    (is (not (p/validate-statement-vs-profile catch-compiled-profile
                                              test-stmt-3)))))
