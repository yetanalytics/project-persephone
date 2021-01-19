(ns com.yetanalytics.persephone-test
  (:require [clojure.test :refer :all]
            [com.yetanalytics.persephone :as per]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Will Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def will-profile (slurp "resources/sample_profiles/will-catch.json"))

(deftest compile-profile-test
  (testing "compile-profile using Will's CATCH profile"
    (is (some? (per/compile-profile will-profile)))))

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
  {:id
   "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
   :timestamp
   "2019-08-09T12:17:00+00:00"
   :actor
   {:objectType "Agent"
    :name       "Will Hoyt"
    :mbox       "mailto:will@yetanalytics.com"}
   :verb
   {:id      "https://example.com/scores"
    :display {:en-US "Scores"}}
   :object
   {:objectType "Agent"
    :name       "Kelvin Qian"
    :mbox       "mailto:kelvinqian@yetanalytics.com"}
   :result
   {:score      {:raw 99 :min 0 :max 100}
    :success    true
    :completion true
    :response   "Good job! Let's get dinner!"
    :timestamp  "2019-08-10T12:18:00+00:00"}
   :context
   {:instructor        {:objectType "Agent"
                        :name       "Will Hoyt"
                        :mbox       "mailto:will@yetanalytics.com"}
    :contextActivities {:parent   [{:objectType "Activity"
                                    :id         "https://example.com/competency/clojure-skill"
                                    :definition {:name        {:en "Skill in the Clojure Language"}
                                                 :description {:en "This person is skilled in Clojure."}
                                                 :type        "https://w3id.org/xapi/catch/activitytypes/competency"}}]
                        :grouping [{:objectType "Activity"
                                    :id         "https://example.com/domain/clojure"
                                    :definition {:name        {:en "The World of Clojure"}
                                                 :description {:en "The environment in which Clojure is used and learned."}
                                                 :type        "https://w3id.org/xapi/catch/activitytypes/domain"}}]}}})

(deftest profile-templates-test
  (testing "profile-templates using Will's CATCH profile"
    (is (vector? (per/profile-templates will-profile)))
    (is (= 52 (count (per/profile-templates will-profile))))
    (is (= ex-template (first (per/profile-templates will-profile))))))

;; FIXME It doesn't seem that our current JSONPath library is able to handle
;; values that are of type string (only keywords). Thus we cannot validate
;; rules involving iri-valued keys. Another reason to migrate to Jayway
(deftest statement-validation-test
  (testing "validate statement using an example Template and Statement"
    (is (not (per/validate-statement ex-template ex-statement)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CMI Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; To avoid the above issue with string-valued keys, we made all such rules
;; with these kinds of JSONPath strings 'recommended' instead of 'included'
(def cmi-profile (slurp "resources/sample_profiles/cmi5.json"))
(def cmi-templates (per/profile-templates cmi-profile))
(def cmi-fsm (first (per/compile-profile cmi-profile)))

(def launched-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/launched")
      (update :result dissoc :score)
      (update :result dissoc :success)
      (update :result dissoc :completion)))

(def initialized-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/initialized")
      (update :result dissoc :score)
      (update :result dissoc :success)
      (update :result dissoc :completion)))

(def completed-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/completed")
      (update :result dissoc :score)
      (update :result dissoc :success)
      (assoc-in [:result :completion] true)
      (assoc-in [:result :duration] "PT4H35M59.14S")
      (assoc-in [:context :contextActivities :category]
                [{:id "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def passed-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/passed")
      (update :result dissoc :completion)
      (assoc-in [:result :duration] "PT4H35M59.14S")
      (assoc-in [:context :contextActivities :category]
                [{:id "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def failed-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/failed")
      (assoc-in [:result :success] false)
      (update :result dissoc :completion)
      (assoc-in [:result :duration] "P8W")
      (assoc-in [:context :contextActivities :category]
                [{:id "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def abandoned-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "https://w3id.org/xapi/adl/verbs/abandoned")
      (update :result dissoc :score)
      (update :result dissoc :success)
      (update :result dissoc :completion)
      (assoc-in [:result :duration] "P8W")))

(def waived-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/waived")
      (update :result dissoc :score)
      (assoc-in [:context :contextActivities :category]
                [{:id "https://w3id.org/xapi/cmi5/context/categories/moveon"}])))

(def terminated-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/terminated")
      (update :result dissoc :score)
      (update :result dissoc :success)
      (update :result dissoc :completion)
      (assoc-in [:result :duration] "P8W")))

(def satisfied-stmt
  (-> ex-statement
      (assoc-in [:verb :id] "http://adlnet.gov/expapi/verbs/satisfied")
      (update :result dissoc :score)
      (update :result dissoc :success)
      (update :result dissoc :completion)
      (assoc-in [:object :definition :type] "https://w3id.org/xapi/cmi5/activitytype/course")))

(deftest cmi-statements-test
  (testing "validating statements from the cmi5 profile"
    (is (per/validate-statement (get cmi-templates 0) ex-statement))
    (is (per/validate-statement (get cmi-templates 1) launched-stmt))
    (is (per/validate-statement (get cmi-templates 2) initialized-stmt))
    (is (per/validate-statement (get cmi-templates 3) completed-stmt))
    (is (per/validate-statement (get cmi-templates 4) passed-stmt))
    (is (per/validate-statement (get cmi-templates 5) failed-stmt))
    (is (per/validate-statement (get cmi-templates 6) abandoned-stmt))
    (is (per/validate-statement (get cmi-templates 7) waived-stmt))
    (is (per/validate-statement (get cmi-templates 8) terminated-stmt))
    (is (per/validate-statement (get cmi-templates 9) satisfied-stmt))))

;; FIXME: Test currently fails
(deftest cmi-statement-test-2
  (testing "validating statements from cmi5 profile that exclude moveon"
    (is (not (per/validate-statement
              (get cmi-templates 6)
              (assoc-in
               abandoned-stmt
               [:context :contextActivities :category]
               [{:id "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))
    (is (not (per/validate-statement
              (get cmi-templates 8)
              (assoc-in
               terminated-stmt
               [:context :contextActivities :category]
               [{:id "https://w3id.org/xapi/cmi5/context/categories/moveon"}]))))))

(defn rejected? [state-info] (-> state-info :state nil?))

(def rns-cmi (partial per/read-next-statement cmi-fsm))

(deftest pattern-validation-test
  (testing "Testing validation of a stream of Statements using Patterns from the
            cmi5 Profile."
    (is (rejected? (rns-cmi nil ex-statement)))
    ;; Accepted by 'satisfied' Template
    (is (not (rejected? (rns-cmi nil satisfied-stmt))))
    ;; Does not satifiy all rules in the 'satisfied' Template
    (is (rejected?
         (rns-cmi nil (assoc-in ex-statement
                                [:verb :id]
                                "http://adlnet.gov/expapi/verbs/satisfied"))))
    ;; Forgot initialized-stmt
    (is (nil? (-> nil
                  (rns-cmi satisfied-stmt)
                  (rns-cmi launched-stmt)
                  (rns-cmi failed-stmt)
                  :state)))
    ;; Session not completed yet
    (is (not (:accepted? (-> nil
                             (rns-cmi satisfied-stmt)
                             (rns-cmi launched-stmt)
                             (rns-cmi initialized-stmt)
                             (rns-cmi failed-stmt)
                             (rns-cmi satisfied-stmt)))))
    ;; Waive, then pass
    (is (:accepted? (-> nil
                       (rns-cmi waived-stmt)
                       (rns-cmi launched-stmt)
                       (rns-cmi initialized-stmt)
                       (rns-cmi passed-stmt)
                       (rns-cmi completed-stmt)
                       (rns-cmi terminated-stmt))))
    ;; Failed, then waived, then finally passed
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
