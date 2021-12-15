(ns com.yetanalytics.persephone-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone :as p]
            [com.yetanalytics.persephone.pattern.errors :as perrs]
            [com.yetanalytics.persephone.utils.json :as jsn]
            [com.yetanalytics.persephone.utils.statement :as stmt]))

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
    (is (not (p/validate-statement
              (p/compile-profiles->validators
               [{:templates [ex-template]}]
               :validate-profiles? false)
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

;; Statment Template tests

;; To avoid the above issue with string-valued keys, we made all such rules
;; with these kinds of JSONPath strings 'recommended' instead of 'included'
(def cmi-profile (slurp "test-resources/sample_profiles/cmi5.json"))
(def cmi-templates (:templates (jsn/json->edn cmi-profile :keywordize? true)))

(def cmi-profile-id "https://w3id.org/xapi/cmi5")
(def cmi-version-id "https://w3id.org/xapi/cmi5/v1.0")
(def cmi-pattern-id "https://w3id.org/xapi/cmi5#toplevel")

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

(def complete-stmt
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

(def cmi-tmpls (p/compile-profiles->validators [cmi-profile]))

(defn- cmi-prof->validator
  [template-id]
  (p/compile-profiles->validators [cmi-profile]
                                  :selected-templates [template-id]))

(def cmi-validator
  (p/compile-profiles->validators [cmi-profile]))

(def cmi-tmpl-0 (cmi-prof->validator "https://w3id.org/xapi/cmi5#generalrestrictions"))
(def cmi-tmpl-1 (cmi-prof->validator "https://w3id.org/xapi/cmi5#launched"))
(def cmi-tmpl-2 (cmi-prof->validator "https://w3id.org/xapi/cmi5#initialized"))
(def cmi-tmpl-3 (cmi-prof->validator "https://w3id.org/xapi/cmi5#completed"))
(def cmi-tmpl-4 (cmi-prof->validator "https://w3id.org/xapi/cmi5#passed"))
(def cmi-tmpl-5 (cmi-prof->validator "https://w3id.org/xapi/cmi5#failed"))
(def cmi-tmpl-6 (cmi-prof->validator "https://w3id.org/xapi/cmi5#abandoned"))
(def cmi-tmpl-7 (cmi-prof->validator "https://w3id.org/xapi/cmi5#waived"))
(def cmi-tmpl-8 (cmi-prof->validator "https://w3id.org/xapi/cmi5#terminated"))
(def cmi-tmpl-9 (cmi-prof->validator "https://w3id.org/xapi/cmi5#satisfied"))

(deftest cmi-statements-test
  (testing "validating statements from the cmi5 profile"
    (is (p/validate-statement cmi-tmpl-0 ex-statement))
    (is (p/validate-statement cmi-tmpl-1 launched-stmt))
    (is (p/validate-statement cmi-tmpl-2 initialized-stmt))
    (is (p/validate-statement cmi-tmpl-3 complete-stmt))
    (is (p/validate-statement cmi-tmpl-4 passed-stmt))
    (is (p/validate-statement cmi-tmpl-5 failed-stmt))
    (is (p/validate-statement cmi-tmpl-6 abandoned-stmt))
    (is (p/validate-statement cmi-tmpl-7 waived-stmt))
    (is (p/validate-statement cmi-tmpl-8 terminated-stmt))
    (is (p/validate-statement cmi-tmpl-9 satisfied-stmt))))

(deftest cmi-statement-test-2
  (testing "calling validate-statement-vs-template with different modes"
    (testing "on valid statements"
      (is (= ex-statement
             (p/validate-statement
              cmi-tmpl-0
              ex-statement
              :fn-type :option)))
      (is (nil? (p/validate-statement
                 cmi-tmpl-0
                 ex-statement
                 :fn-type :result)))
      (is (nil? (p/validate-statement
                 cmi-tmpl-0
                 ex-statement
                 :fn-type :assertion)))
      (is (= "" (with-out-str
                  (p/validate-statement
                   cmi-tmpl-0
                   ex-statement
                   :fn-type :printer)))))
    (testing "on invalid statements"
      (is (not (p/validate-statement
                (concat cmi-tmpl-0 cmi-tmpl-1)
                ex-statement
                :all-valid? true)))
      (is (nil? (p/validate-statement
                 cmi-tmpl-1
                 ex-statement
                 :fn-type :option)))
      (is (some? (p/validate-statement
                  cmi-tmpl-1
                  ex-statement
                  :fn-type :result)))
      (is (= {:pred :every-val-present?
              :vals ["https://example.com/scores"]
              :prop {:location   "$.verb.id"
                     :match-vals ["http://adlnet.gov/expapi/verbs/launched"]
                     :det-prop   "Verb"}
              :temp "https://w3id.org/xapi/cmi5#launched"
              :stmt "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"}
             (-> (p/validate-statement
                  cmi-tmpl-1
                  ex-statement
                  :fn-type :result)
                 (get "https://w3id.org/xapi/cmi5#launched")
                 first)))
      (testing "(any-valid vs all-valid)"
        (is (nil? (p/validate-statement
                   (concat cmi-tmpl-0 cmi-tmpl-1)
                   ex-statement
                   :fn-type :result)))
        (is (some? (p/validate-statement
                    (concat cmi-tmpl-0 cmi-tmpl-1)
                    ex-statement
                    :fn-type :result
                    :all-valid? true))))
      (testing "(short-circuit vs non-short-circuit)"
        (is (= 2 (-> (p/validate-statement
                      (concat cmi-tmpl-1 cmi-tmpl-2)
                      ex-statement
                      :fn-type :result)
                     vals
                     count)))
        (is (= 1 (-> (p/validate-statement
                      (concat cmi-tmpl-1 cmi-tmpl-2)
                      ex-statement
                      :fn-type :result
                      :short-circuit? true)
                     vals
                     count))))
      (is (= (p/validate-statement
              cmi-tmpl-1
              ex-statement
              :fn-type :result)
             (try (p/validate-statement
                   cmi-tmpl-1
                   ex-statement
                   :fn-type :assertion)
                  (catch #?(:clj Exception :cljs js/Error) e (-> e ex-data :errors)))))
      (is (not= "" (with-out-str
                     (p/validate-statement
                      cmi-tmpl-1
                      ex-statement
                      :fn-type :printer)))))))

(deftest cmi-statement-test-3
  (testing "validating statements from cmi5 profile that exclude moveon"
    (is (not (p/validate-statement
              cmi-tmpl-6
              (assoc-in
               abandoned-stmt
               ["context" "contextActivities" "category"]
               [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))
    (is (not (p/validate-statement
              cmi-tmpl-8
              (assoc-in
               terminated-stmt
               ["context" "contextActivities" "category"]
               [{"id" "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))))

(deftest cmi-statements-vs-profile-test
  (testing "validating statements from the cmi5 profile, against the whole
            profile"
    ;; Valid Statement
    (is (p/validate-statement cmi-validator
                              ex-statement
                              :fn-type :predicate))
    (is (= ex-statement
           (p/validate-statement cmi-validator
                                 ex-statement
                                 :fn-type :option)))
    (is (nil? (p/validate-statement cmi-validator
                                    ex-statement
                                    :fn-type :result)))
    (is (nil? (p/validate-statement cmi-validator
                                    ex-statement
                                    :fn-type :assertion)))
    (is (= ["https://w3id.org/xapi/cmi5#generalrestrictions"]
           (p/validate-statement cmi-validator
                                 ex-statement
                                 :fn-type :templates)))
    ;; Invalid Statement (just an empty map)
    (is (not (p/validate-statement cmi-validator
                                   {}
                                   :fn-type :predicate)))
    (is (nil? (p/validate-statement cmi-validator
                                    {}
                                    :fn-type :option)))
    (is (= [] (p/validate-statement cmi-validator
                                    {}
                                    :fn-type :templates)))
    (is (= 10 (count
               (p/validate-statement cmi-validator
                                     {}
                                     :fn-type :result))))
    (is (= {:pred :any-matchable?
            :vals [nil]
            :rule {:location "$.id"
                   :presence "included"}
            :temp "https://w3id.org/xapi/cmi5#generalrestrictions"
            :stmt nil}
           (-> (p/validate-statement cmi-validator
                                     {}
                                     :fn-type :result)
               (get "https://w3id.org/xapi/cmi5#generalrestrictions")
               first)))
    (is (= (p/validate-statement cmi-validator
                                 {}
                                 :fn-type :result)
           (try (p/validate-statement cmi-validator
                                      {}
                                      :fn-type :result)
                (catch #?(:clj Exception :cljs js/Error) e (-> e ex-data :errors)))))))

;; Pattern Matching Tests

(def cmi-fsm-map (p/compile-profiles->fsms [cmi-profile]
                                           :compile-nfa? true
                                           :select-patterns [cmi-pattern-id]))
(def match-cmi (partial p/match-statement cmi-fsm-map))

(def failure-msg-1
  "----- Pattern Match Failure -----
Primary Pattern ID: https://w3id.org/xapi/cmi5#toplevel
Statement ID:       fd41c918-b88b-4b20-a0a5-a4c32391aaa0

Statement Templates visited:
  https://w3id.org/xapi/cmi5#satisfied
  https://w3id.org/xapi/cmi5#launched
  https://w3id.org/xapi/cmi5#initialized
  https://w3id.org/xapi/cmi5#completed
  https://w3id.org/xapi/cmi5#satisfied
  https://w3id.org/xapi/cmi5#terminated
Pattern path:
  https://w3id.org/xapi/cmi5#terminated
  https://w3id.org/xapi/cmi5#terminatedorabandoned
  https://w3id.org/xapi/cmi5#completionnosuccesssession
  https://w3id.org/xapi/cmi5#typicalsession
  https://w3id.org/xapi/cmi5#typicalsessions
  https://w3id.org/xapi/cmi5#toplevel")

(deftest pattern-match-test
  (testing "Testing matching of a stream of Statements using Patterns from the cmi5 Profile."
    (is (-> (match-cmi nil ex-statement)
            (get-in [:states-map :no-registration cmi-pattern-id])
            empty?))
    ;; Accepted by 'satisfied' Template
    (is (-> (match-cmi nil satisfied-stmt)
            :accepts
            not-empty))
    ;; Does not satifiy all rules in the 'satisfied' Template - rejected
    (is (-> (match-cmi nil (assoc-in
                            ex-statement
                            ["verb" "id"]
                            "http://adlnet.gov/expapi/verbs/satisfied"))
            :rejects
            not-empty))
    ;; Forgot initialized-stmt - rejected
    (is (-> nil
            (match-cmi satisfied-stmt)
            (match-cmi launched-stmt)
            (match-cmi failed-stmt)
            :rejects
            not-empty))
    ;; Session not completed yet
    (is (-> nil
            (match-cmi satisfied-stmt)
            (match-cmi launched-stmt)
            (match-cmi initialized-stmt)
            (match-cmi failed-stmt)
            (match-cmi satisfied-stmt)
            :accepts
            empty?))
    ;; Just a bunch of satisfieds
    (is (-> nil
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi satisfied-stmt)
            :accepts
            not-empty))
    ;; Waive, then pass
    (is (-> nil
            (match-cmi waived-stmt)
            (match-cmi launched-stmt)
            (match-cmi initialized-stmt)
            (match-cmi passed-stmt)
            (match-cmi complete-stmt)
            (match-cmi terminated-stmt)
            :accepts
            not-empty))
    ;; Completed, then failed (oof!)
    (is (-> nil
            (match-cmi launched-stmt)
            (match-cmi initialized-stmt)
            (match-cmi complete-stmt)
            (match-cmi failed-stmt)
            (match-cmi abandoned-stmt)
            :accepts
            not-empty))
    ;; Just straight up failed
    (is (-> nil
            (match-cmi launched-stmt)
            (match-cmi initialized-stmt)
            (match-cmi failed-stmt)
            (match-cmi abandoned-stmt)
            :accepts
            not-empty))
    ;; Failed, then waived, then finally passed (yay!)
    (is (-> nil
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
            (match-cmi complete-stmt)
            (match-cmi satisfied-stmt)
            (match-cmi passed-stmt)
            (match-cmi terminated-stmt)
            :accepts
            not-empty)))
  (testing "Error message output associated with the cmi5 Profile"
    (is (= {:failure {:statement "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
                      :pattern   "https://w3id.org/xapi/cmi5#toplevel"}}
           (-> (p/match-statement (assoc-in cmi-fsm-map
                                            [cmi-version-id cmi-pattern-id :nfa]
                                            nil)
                                  nil
                                  ex-statement)
               (get-in [:states-map :no-registration cmi-pattern-id])
               meta)))
    (is (= {:failure {:statement "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
                      :pattern   "https://w3id.org/xapi/cmi5#toplevel"
                      :traces    []}}
           (-> (match-cmi nil ex-statement)
               (get-in [:states-map :no-registration cmi-pattern-id])
               meta)))
    ;; The first failure happens on the "completion-no-success" pattern.
    ;; The second failure happens either on the "completion-maybe-failed"
    ;; or the "failed" pattern.
    (let [errs-1 (-> nil
                     (match-cmi satisfied-stmt)
                     (match-cmi launched-stmt)
                     (match-cmi initialized-stmt)
                     (match-cmi complete-stmt)
                     (match-cmi satisfied-stmt)
                     (match-cmi terminated-stmt)
                     (match-cmi failed-stmt)
                     (get-in [:states-map :no-registration cmi-pattern-id])
                     meta)]
      (is (= {:failure
              {:statement "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
               :pattern   "https://w3id.org/xapi/cmi5#toplevel"
               :traces
               [{:templates
                 ["https://w3id.org/xapi/cmi5#satisfied"
                  "https://w3id.org/xapi/cmi5#launched"
                  "https://w3id.org/xapi/cmi5#initialized"
                  "https://w3id.org/xapi/cmi5#completed"
                  "https://w3id.org/xapi/cmi5#satisfied"
                  "https://w3id.org/xapi/cmi5#terminated"]
                 :patterns
                 [["https://w3id.org/xapi/cmi5#terminated"
                   "https://w3id.org/xapi/cmi5#terminatedorabandoned"
                   "https://w3id.org/xapi/cmi5#completionnosuccesssession"
                   "https://w3id.org/xapi/cmi5#typicalsession"
                   "https://w3id.org/xapi/cmi5#typicalsessions"
                   "https://w3id.org/xapi/cmi5#toplevel"]]}]}}
             errs-1))
      (is (= failure-msg-1
             (perrs/error-msg-str (:failure errs-1)))))
    (let [errs-2 (-> nil
                     (match-cmi satisfied-stmt)
                     (match-cmi launched-stmt)
                     (match-cmi initialized-stmt)
                     (match-cmi failed-stmt)
                     (match-cmi terminated-stmt)
                     (match-cmi complete-stmt)
                     (get-in [:states-map :no-registration cmi-pattern-id])
                     meta)]
      (is (= {:failure
              {:statement "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
               :pattern   "https://w3id.org/xapi/cmi5#toplevel"
               :traces
               [{:templates
                 ["https://w3id.org/xapi/cmi5#satisfied"
                  "https://w3id.org/xapi/cmi5#launched"
                  "https://w3id.org/xapi/cmi5#initialized"
                  "https://w3id.org/xapi/cmi5#failed"
                  "https://w3id.org/xapi/cmi5#terminated"]
                 :patterns
                 #{["https://w3id.org/xapi/cmi5#terminated"
                    "https://w3id.org/xapi/cmi5#terminatedorabandoned"
                    "https://w3id.org/xapi/cmi5#completionmaybefailedsession"
                    "https://w3id.org/xapi/cmi5#typicalsession"
                    "https://w3id.org/xapi/cmi5#typicalsessions"
                    "https://w3id.org/xapi/cmi5#toplevel"]
                   ["https://w3id.org/xapi/cmi5#terminated"
                    "https://w3id.org/xapi/cmi5#terminatedorabandoned"
                    "https://w3id.org/xapi/cmi5#failedsession"
                    "https://w3id.org/xapi/cmi5#typicalsession"
                    "https://w3id.org/xapi/cmi5#typicalsessions"
                    "https://w3id.org/xapi/cmi5#toplevel"]}}]}}
             (-> errs-2
                 (update-in [:failure :traces] vec)
                 (update-in [:failure :traces 0 :patterns] set))))
      ;; Patterns ordering will be different in clj and cljs, so don't bother
      ;; with exact string matching.
      (is (string? (perrs/error-msg-str (:failure errs-2)))))
    (testing ":print? kwarg set to true"
      (let [match-cmi-print (fn [si stmt]
                              (p/match-statement cmi-fsm-map
                                                 si
                                                 stmt
                                                 :print? true))]
        (is (string?
             (with-out-str
               (-> nil
                   (match-cmi-print satisfied-stmt)
                   (match-cmi-print launched-stmt)
                   (match-cmi-print initialized-stmt)
                   (match-cmi-print complete-stmt)
                   (match-cmi-print satisfied-stmt)
                   (match-cmi-print terminated-stmt)
                   (match-cmi-print failed-stmt)))))))))

;; Profile Matching Tests

(def match-cmi-2 (partial p/match-statement cmi-fsm-map))

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
      (assoc-in ["context" "extensions" stmt/subreg-iri]
                [{"profile"         "https://w3id.org/xapi/cmi5/v1.0"
                  "subregistration" sub-reg-1}])))

(def satisfied-stmt-4
  (-> satisfied-stmt
      (assoc-in ["context" "registration"] registration-3)
      (assoc-in ["context" "extensions" stmt/subreg-iri]
                [{"profile"         "https://example.org/profile"
                  "subregistration" sub-reg-3}
                 {"profile"         "https://w3id.org/xapi/cmi5/v1.0"
                  "subregistration" sub-reg-2}])))

(deftest profile-match-test
  (testing "the match-statement function w/ registrations against the cmi5 profile."
    (is (= 2 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (get :states-map)
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (get-in [:states-map :no-registration])
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (get-in [:states-map registration-2])
                 count)))
    (is (every? :accepted?
                (-> {}
                    (match-cmi-2 satisfied-stmt)
                    (match-cmi-2 satisfied-stmt-2)
                    (get-in [:states-map registration-2 cmi-pattern-id]))))
    (is (every? :accepted?
                (-> {}
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
                    (match-cmi-2 complete-stmt)
                    (match-cmi-2 satisfied-stmt)
                    (match-cmi-2 passed-stmt)
                    (match-cmi-2 terminated-stmt)
                    (get-in [:states-map :no-registration cmi-pattern-id]))))
    (is (every? :accepted?
                (-> {}
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
                    (get-in [:states-map registration-2 cmi-pattern-id])))))
  (testing "the match-statement-vs-profile function w/ sub-registrations."
    (is (= 4 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 (get :states-map)
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 (get-in [:states-map [registration-3 sub-reg-1]])
                 count)))
    (is (= 1 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 (get-in [:states-map [registration-3 sub-reg-2]])
                 count)))
    (is (= 0 (-> {}
                 (match-cmi-2 satisfied-stmt)
                 (match-cmi-2 satisfied-stmt-2)
                 (match-cmi-2 satisfied-stmt-3)
                 (match-cmi-2 satisfied-stmt-4)
                 (get-in [:states-map registration-3])
                 count)))
    (is (every? :accepted?
                (-> {}
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
                    (get-in [:states-map
                             [registration-3 sub-reg-2]
                             cmi-pattern-id]))))))

(deftest pattern-exceptions-test
  (testing "match-statement-vs-pattern exceptions"
    (let [bad-stmt (assoc-in satisfied-stmt
                             ["context" "contextActivities" "category"]
                             [])]
      (is (= ::stmt/missing-profile-reference
             (->> bad-stmt
                  (match-cmi #{})
                  :error
                  :type)))
      (is (= bad-stmt
             (->> bad-stmt
                  (match-cmi #{})
                  :error
                  :statement)))))
  (testing "match-statement-vs-profile exceptions"
    (is (= ::stmt/missing-profile-reference
           (->> (assoc-in satisfied-stmt
                          ["context" "contextActivities" "category"]
                          [])
                (match-cmi-2 {})
                :error
                :type)))
    (is (= ::stmt/invalid-subreg-nonconformant
           (->> (assoc-in satisfied-stmt-3
                          ["context" "extensions" stmt/subreg-iri]
                          [])
                (match-cmi-2 {})
                :error
                :type)))
    (is (= ::stmt/invalid-subreg-no-registration
           (->> (update satisfied-stmt-3
                        "context"
                        dissoc
                        "registration")
                (match-cmi-2 {})
                :error
                :type))))
  (testing "error input returns the same"
    (is (= {:error ::stmt/missing-profile-reference}
           (match-cmi {:error ::stmt/missing-profile-reference} {})))
    (is (= {:error ::stmt/missing-profile-reference}
           (match-cmi-2 {:error ::stmt/missing-profile-reference} {})))))

;; Batch Matching Tests

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
                           complete-stmt
                           satisfied-stmt
                           passed-stmt
                           terminated-stmt]))

(deftest match-statement-batch-test
  (testing "`match-statement-batch` function - single pattern"
    (is (-> (p/match-statement-batch cmi-fsm-map
                                     nil
                                     statement-batch)
            (get :accepts)
            not-empty))
    (is (-> (p/match-statement-batch cmi-fsm-map
                                     nil
                                     (shuffle statement-batch))
            (get :accepts)
            not-empty)))
  (testing "`match-statement-batch` function - whole profile"
    (is (every? :accepted?
                (get-in (p/match-statement-batch cmi-fsm-map
                                                 {}
                                                 statement-batch)
                        [:states-map :no-registration cmi-pattern-id])))
    (is (every? :accepted?
                (get-in (p/match-statement-batch
                         cmi-fsm-map
                         {}
                         (shuffle statement-batch))
                        [:states-map :no-registration cmi-pattern-id])))
    (is (every? :accepted?
                (get-in (p/match-statement-batch
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
                        [:states-map
                         [registration-3 sub-reg-2]
                         cmi-pattern-id])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CATCH Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def catch-profile (slurp "test-resources/sample_profiles/catch.json"))
(def catch-profile-id "https://w3id.org/xapi/catch")

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
                              "id"         "https://w3id.org/xapi/catch/activities/competency/dle-research-goals-models-benifits"
                              "definition" {"type" "https://w3id.org/xapi/catch/activitytypes/competency"}}]
                 "grouping" [{"objectType" "Activity"
                              "id"         "https://w3id.org/xapi/catch/activities/domain/planning-and-preparation"
                              "definition" {"type" "https://w3id.org/xapi/catch/activitytypes/domain"}}]}}})

;; Statement Template tests

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

(def catch-stmt-batch
  [presented-advocacy-stmt
   attended-advocacy-stmt
   test-stmt-1
   test-stmt-2
   test-stmt-3])

(def catch-id-temp-map (p/profile->id-template-map catch-profile))
(def catch-id-stmt-map (p/statement-batch->id-statement-map catch-stmt-batch))

(def evidence-advocacy-provide-irl
  "https://w3id.org/xapi/catch/templates#evidence-advocacy-provide")

(def catch-compiled-profile-1
  (p/compile-profiles->validators
   [catch-profile]
   :statement-ref-fns {:get-statement-fn catch-id-stmt-map
                       :get-template-fn  catch-id-temp-map}
   :validate-profile? false
   :selected-templates [evidence-advocacy-provide-irl]))

(def catch-compiled-profile-2
  (p/compile-profiles->validators
   [catch-profile]
   :statement-ref-fns {:get-statement-fn catch-id-stmt-map
                       :get-template-fn  catch-id-temp-map}
   :validate-profile? false))

(deftest statement-ref-test
  (testing "validation of Statements with (Context) Statement Refs"
    (is (p/validate-statement
         catch-compiled-profile-1
         test-stmt-1))
    (is (p/validate-statement
         catch-compiled-profile-1
         test-stmt-2))
    (is (not (p/validate-statement
              catch-compiled-profile-1
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
             (p/validate-statement
              catch-compiled-profile-1
              test-stmt-3
              :fn-type :printer))))
    (is (p/validate-statement catch-compiled-profile-2
                              test-stmt-1))
    (is (p/validate-statement catch-compiled-profile-2
                              test-stmt-2))
    (is (not (p/validate-statement catch-compiled-profile-2
                                   test-stmt-3)))))

;; Pattern tests

(comment
  ;; For pattern https://w3id.org/xapi/catch/patterns#f1-1-01-completion
  "https://w3id.org/xapi/catch/templates#professional-development-event"
  "https://w3id.org/xapi/catch/templates#wrote-reflection"
  "https://w3id.org/xapi/catch/templates#f1-1-01"
  "https://w3id.org/xapi/catch/templates#system-notification-submission"
  "https://w3id.org/xapi/catch/templates#system-notification-progression"
  "https://w3id.org/xapi/catch/templates#system-notification-completion"
  "https://w3id.org/xapi/catch/templates#activity-completion")

(def prof-dev-stmt
  (-> statement-basics
      (assoc-in ["id"] "prof-dev-stmt")
      (assoc-in ["verb"]
                {"objectType" "Verb"
                 "id" "http://adlnet.gov/expapi/verbs/attended"
                 "display" {"en-US" "Attended"}})
      (assoc-in ["object"]
                {"id"
                 "https://w3id.org/xapi/catch/activitytypes/professional-development#1"
                 "definition"
                 {"name" "Professional Development 1"
                  "type" "https://w3id.org/xapi/catch/activitytypes/professional-development"}})
      (assoc-in ["result" "duration"]
                "PT4H35M59.14S")
      (assoc-in ["context" "platform"]
                "Some Platform")))

(def reflection-stmt
  (-> statement-basics
      (assoc-in ["id"] "reflection-stmt")
      (assoc-in ["verb"]
                {"id" "https://w3id.org/xapi/catch/verbs/reflected"})
      (assoc-in ["result" "response"]
                "Some Reflection Response")
      (assoc-in ["context" "statement"]
                {"objectType" "StatementRef"
                 "id" "prof-dev-stmt"})))

(def checkin-stmt
  (-> statement-basics
      (assoc-in ["id"] "main-stmt")
      (assoc-in ["verb"]
                {"objectType" "Verb"
                 "id" "https://w3id.org/xapi/catch/verbs/submitted"})
      (assoc-in ["object"]
                {"objectType" "Activity"
                 "id" "https://w3id.org/xapi/catch/activitytypes/check-in#1"
                 "definition"
                 {"name" {"en-US" "Check-In"}
                  "type" "https://w3id.org/xapi/catch/activitytypes/check-in"}})
      (assoc-in ["context" "statement"]
                {"objectType" "StatementRef"
                 "id" "prof-dev-stmt"})))

(def notify-submission-stmt
  (-> statement-basics
      (assoc-in ["id"] "notify-submission-stmt")
      (assoc-in ["verb"]
                {"id" "https://w3id.org/xapi/catch/verbs/notified"
                 "display" {"en-US" "Notified"}})
      (assoc-in ["object"]
                {"objectType" "Agent"
                 "name"       "Will Hoyt"
                 "mbox"       "mailto:will@yetanalytics.com"})
      (assoc-in ["result" "success"] true)
      (assoc-in ["context" "statement"]
                {"objectType" "StatementRef"
                 "id" "main-stmt"})))

(def notify-progression-stmt
  (-> statement-basics
      (assoc-in ["id"] "notify-progression-stmt")
      (assoc-in ["verb"]
                {"id" "https://w3id.org/xapi/catch/verbs/notified"})
      (assoc-in ["object"]
                {"objectType" "Agent"
                 "name"       "Will Hoyt"
                 "mbox"       "mailto:will@yetanalytics.com"})
      (assoc-in ["result" "score" "raw"] 1)
      (assoc-in ["result" "score" "min"] 0)
      (assoc-in ["result" "score" "max"] 1)
      (assoc-in ["context" "statement"]
                {"objectType" "StatementRef"
                 "id" "notify-submission-stmt"})))

(def notify-completion-stmt
  (-> statement-basics
      (assoc-in ["id"] "notify-progression-stmt")
      (assoc-in ["verb"]
                {"id" "https://w3id.org/xapi/catch/verbs/notified"})
      (assoc-in ["object"]
                {"objectType" "Agent"
                 "name"       "Will Hoyt"
                 "mbox"       "mailto:will@yetanalytics.com"})
      (assoc-in ["result" "completion"] true)
      (assoc-in ["context" "statement"]
                {"objectType" "StatementRef"
                 "id" "notify-submission-stmt"})))

(def checkin-complete-stmt
  (-> statement-basics
      (assoc-in ["id"] "completed-stmt")
      (assoc-in ["verb"]
                {"objectType" "Verb"
                 "id" "http://adlnet.gov/expapi/verbs/completed"
                 "display" {"en-US" "Completed"}})
      (assoc-in ["object"]
                {"objectType" "Activity"
                 "id" "https://w3id.org/xapi/catch/activitytypes/check-in#1"
                 "definition"
                 {"name" {"en-US" "Reflection"}
                  "description" {"en-US" "Some reflection"}
                  "type" "https://w3id.org/xapi/catch/activitytypes/check-in"}})))

(def catch-stmt-batch-2
  [prof-dev-stmt
   reflection-stmt
   checkin-stmt
   notify-submission-stmt
   notify-progression-stmt
   notify-completion-stmt
   checkin-complete-stmt])

(def catch-id-stmt-map-2
  (p/statement-batch->id-statement-map catch-stmt-batch-2))

;; Add extra profile to test `:selected-profiles`
(def catch-fsm
  (p/compile-profiles->fsms
   [cmi-profile catch-profile]
   :statement-ref-fns {:get-statement-fn catch-id-stmt-map-2
                       :get-template-fn  catch-id-temp-map}
   :compile-nfa?      true
   :validate-profile? false
   :selected-profiles [catch-profile-id]))

(deftest compile-profile-test
  (testing "the `compile-profiles->fsms` function"
    (is (= 1 (count catch-fsm)))
    (is (->> catch-fsm
             vals
             first
             vals
             (every? (fn [{:keys [id dfa nfa nfa-meta]}]
                       (and id dfa nfa nfa-meta)))))))

(deftest statement-ref-pattern-test
  (testing "matching patterns with Statement Refs"
    (let [pat-id "https://w3id.org/xapi/catch/patterns#f1-1-01-completion"]
      (is (every? :accepted?
                  (-> (p/match-statement-batch
                       catch-fsm
                       {}
                       catch-stmt-batch-2)
                      (get-in [:states-map :no-registration pat-id])))))))

(comment
  (def compiled-profile (p/compile-profiles->fsms [cmi-profile]))
  (p/match-statement compiled-profile
                     {}
                     satisfied-stmt)
  )
