(ns com.yetanalytics.persephone-test.cli-test.validate-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.persephone.cli.validate :refer [validate]]))

(def profile-uri "test-resources/sample_profiles/calibration.jsonld")
(def statement-uri "test-resources/sample_statements/calibration_1.json")
(def statement-2-uri "test-resources/sample_statements/calibration_2.json")

(def template-1-id "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-1")
(def template-2-id "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2")
(def template-3-id "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-3")

(def template-2-fail-str
  "----- Statement Validation Failure -----
Template ID:  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
Statement ID: 00000000-4000-8000-0000-000000000000

Template Verb property was not matched.
 template Verb:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#didnt
 statement Verb:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#did

Template rule was not followed:
  {:any [\"https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-2\"],
   :location \"$.object.id\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-1

Template rule was not followed:
  {:any [\"Activity 2\"],
   :location \"$.object.definition.name.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   Activity 1

Template rule was not followed:
  {:any [\"The second Activity\"],
   :location \"$.object.definition.description.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   The first Activity
")

(def template-3-fail-str
  "
----- Statement Validation Failure -----
Template ID:  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-3
Statement ID: 00000000-4000-8000-0000-000000000000

Template rule was not followed:
  {:any [\"https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-3\"],
   :location \"$.object.id\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-1

Template rule was not followed:
  {:any [\"Activity 3\"],
   :location \"$.object.definition.name.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   Activity 1

Template rule was not followed:
  {:any [\"The third Activity\"],
   :location \"$.object.definition.description.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   The first Activity
")

(deftest validate-cli-args-test
  (testing "Help Argument"
    (is (not-empty (with-out-str (validate (list "--help")))))
    (is (= (with-out-str
             (validate (list "-h")))
           (with-out-str
             (validate (list "-h" "-p" profile-uri "-s" statement-uri))))))
  (testing "Invalid Arguments"
    (is (= "No Profiles specified.\nNo Statement specified.\n"
           (let [s (new java.io.StringWriter)]
             (binding [*err* s]
               (validate '())
               (str s)))))
    (is (= (str "Error while parsing option \"-s non-existent.json\": "
                "java.io.FileNotFoundException: non-existent.json (No such file or directory)\n"
                "No Statement specified.\n")
           (let [s (new java.io.StringWriter)]
             (binding [*err* s]
               (validate (list "-p" profile-uri "-s" "non-existent.json"))
               (str s)))))
    (is (= (str "Failed to validate \"-p test-resources/sample_statements/calibration_1.json\": "
                (with-out-str
                  (pan/validate-profile
                   (pan/json-profile->edn (slurp statement-uri))
                   :result :print))
                "No Profiles specified.\n")
           (let [s (new java.io.StringWriter)]
             (binding [*err* s]
               (validate (list "-p" statement-uri "-s" statement-uri))
               (str s)))))))

(deftest validate-cli-test
  (testing "Validation Passes"
    (is (validate (list "--profile" profile-uri
                        "--statement" statement-uri)))
    (is (validate (list "-p" profile-uri
                        "-p" "test-resources/sample_profiles/catch.json"
                        "-p" "test-resources/sample_profiles/cmi5.json"
                        "-s" statement-uri)))
    (is (validate (list "-p" profile-uri "-s" statement-uri
                        "--template-id" template-1-id)))
    (is (validate (list "-p" profile-uri "-s" statement-uri
                        "-i" template-1-id "-i" template-2-id)))
    (is (validate (list "-p" profile-uri "-s" statement-uri
                        "--short-circuit")))
    ;; The calibration profile does not have any Statement Ref properties
    ;; so --extra-statements just gets ignored
    (is (validate (list "-p" profile-uri "-s" statement-uri
                        "--extra-statements" statement-2-uri
                        "-e" statement-2-uri)))
    ;; No templates => statement vacuously validates against all
    (is (validate (list "-p" profile-uri "-s" statement-uri
                        "-i" "http://random-template.org"))))
  (testing "Validation Fails"
    (is (= (str template-2-fail-str
                "\n-----------------------------"
                "\nTotal errors found: 4\n\n")
           (with-out-str
             (validate
              (list "-p" profile-uri "-s" statement-uri "-i" template-2-id)))))
    (is (= (str template-2-fail-str
                template-3-fail-str
                "\n-----------------------------"
                "\nTotal errors found: 7\n\n")
           (with-out-str
             (validate (list "-p" profile-uri "-s" statement-uri
                             "-i" template-2-id
                             "-i" template-3-id)))))
    (is (= (str template-2-fail-str
                template-3-fail-str
                "\n-----------------------------"
                "\nTotal errors found: 7\n\n")
           (with-out-str
             (validate (list "-p" profile-uri "-s" statement-uri
                             "--all-valid")))))
    (is (= (str template-2-fail-str
                "\n-----------------------------"
                "\nTotal errors found: 4\n\n")
           (with-out-str
             (validate
              (list "-p" profile-uri "-s" statement-uri "-a" "-c")))))))
