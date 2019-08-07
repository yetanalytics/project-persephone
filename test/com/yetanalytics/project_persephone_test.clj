(ns com.yetanalytics.project-persephone-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :as pprint]
            [com.yetanalytics.project-persephone :refer :all]
            [com.yetanalytics.pattern-validation :as pv]
            [com.yetanalytics.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Will Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def will-profile (slurp "resources/sample_profiles/will-catch.json"))

(deftest compile-profile-test
  (testing "compile-profile using Will's CATCH profile"
    (is (some? (compile-profile will-profile)))))

(def ex-template
  {:id "https://w3id.org/xapi/catch/templates#score-rubric"
   :type "StatementTemplate"
   :inScheme "https://w3id.org/xapi/catch/v1"
   :prefLabel {:en "score rubric"}
   :definition {:en "This template is for statements that are the result of a mentor reviewing and scoring somthing within the catch application."}
   :contextGroupingActivityType ["https://w3id.org/xapi/catch/activitytypes/domain"]
   :contextParentActivityType ["https://w3id.org/xapi/catch/activitytypes/competency"]
   :rules [{:location "$.context.contextActivities.category['https://w3id.org/xapi/catch/v1']"
            :presence "included"
            :scopeNote {:en "this states the the statement conforms to this profile"}}
           {:location "$.result.score.raw"
            :presence "included"
            :scopeNote {:en "the total number of points awarded.  This value will be determined by the point values associated with the various criteria"}}
           {:location "$.result.score.min"
            :presence "included"
            :scopeNote {:en "the lowest possible number of points"}}
           {:location "$.result.score.max"
            :presence "included"
            :scopeNote {:en "the greatest possible number of points"}}
           {:location "$.result.success"
            :presence "included"
            :scopeNote {:en "should be true if the score threshold was met, false otherwise"}}
           {:location "$.result.response"
            :presence "recommended"
            :scopeNote {:en "the mentors feedback"}}
           {:location "$.result.extensions['https://w3id.org/xapi/catch/result-extensions/rubric-criteria']"
            :presence "included"
            :scopeNote {:en "an array of objects where each object represents one of the criteria being scored.  each object will have a property for the name of the criteria, the possible values (ie. Emerging, Attainment, Excellence) and the selected value."}}
           {:location "$.timestamp"
            :presence "included"
            :scopeNote {:en "the time when the mentor submited the scored rubric"}}]})

(def ex-statement
  {:id "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
   :timestamp "2019-08-09T12:17:00+00:00"
   :actor {:objectType "Agent"
           :name "Will Hoyt"
           :mbox "mailto:will@yetanalytics.com"}
   :verb {:id "https://example.com/scores"
          :display {:en-US "Scores"}}
   :object {:objectType "Agent"
            :name "Kelvin Qian"
            :mbox "mailto:kelvinqian@yetanalytics.com"}
   :result {:score {:raw 99
                    :min 0
                    :max 100}
            :success true
            :response "Good job! Let's get dinner!"
            :timestamp "2019-08-10T12:18:00+00:00"}
   :context {:instructor {:objectType "Agent"
                          :name "Will Hoyt"
                          :mbox "mailto:will@yetanalytics.com"}
             :contextActivities {:parent [{:objectType "Activity"
                                           :id "https://example.com/competency/clojure-skill"
                                           :definition {:name {:en "Skill in the Clojure Language"}
                                                        :description {:en "This person is skilled in Clojure."}
                                                        :type "https://w3id.org/xapi/catch/activitytypes/competency"}}]
                                 :grouping [{:objectType "Activity"
                                             :id "https://example.com/domain/clojure"
                                             :definition {:name {:en "The World of Clojure"}
                                                          :description {:en "The environment in which Clojure is used and learned."}
                                                          :type "https://w3id.org/xapi/catch/activitytypes/domain"}}]}}})

(deftest profile-templates-test
  (testing "profile-templates using Will's CATCH profile"
    (is (vector? (profile-templates will-profile)))
    (is (= 52 (count (profile-templates will-profile))))
    (is (= ex-template (first (profile-templates will-profile))))))

;; FIXME It doesn't seem that our current JSONPath library is able to handle
;; values that are of type string (only keywords). Thus we cannot validate
;; rules involving iri-valued keys. Another reason to migrate to Jayway
(deftest statement-validation-test
  (testing "validate statement using an example Template and Statement"
    (is (not (validate-statement ex-template ex-statement)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CMI Profile
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
