(ns com.yetanalytics.persephone-test.cli-test.match-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.persephone.cli.match :refer [match]]
            [com.yetanalytics.persephone-test.test-utils :refer [with-err-str]]))

(def profile-uri "test-resources/sample_profiles/calibration.jsonld")
(def statement-uri "test-resources/sample_statements/calibration_1.json")
(def statement-2-uri "test-resources/sample_statements/calibration_2.json")
(def statement-coll-uri "test-resources/sample_statements/calibration_coll.json")

(def bad-statement-uri "test-resources/sample_statements/adl_1.json")
(def bad-statement-2-uri "test-resources/sample_statements/adl_3.json")

(def pattern-id "https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1")

(def missing-profile-ref-error-str-1
  "----- Pattern Match Error -----
Error Description:  Missing Profile version in context category activity IDs
Statement ID:       fd41c918-b88b-4b20-a0a5-a4c32391aaa0

Category contextActivity IDs:
(None)
")

(def missing-profile-ref-error-str-2
  "----- Pattern Match Error -----
Error Description:  Missing Profile version in context category activity IDs
Statement ID:       6690e6c9-3ef0-4ed3-8b37-7f3964730bee

Category contextActivity IDs:
http://www.example.com/meetings/categories/teammeeting
")

(def pattern-match-failure-str-1
  "----- Pattern Match Failure -----
Primary Pattern ID: https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
Statement ID:       00000000-4000-8000-0000-000000000000

Pattern matching has failed.
")

(def pattern-match-failure-str-2
  "----- Pattern Match Failure -----
Primary Pattern ID: https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
Statement ID:       00000000-4000-8000-0000-000000000000

Statement Templates visited:
  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
Pattern path:
  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
  https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-3
  https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
")

(def pattern-match-failure-str-3
  "----- Pattern Match Failure -----
Primary Pattern ID: https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
Statement ID:       00000000-4000-8000-0000-000000000001

Pattern matching has failed.
")

(def pattern-match-failure-str-4
  "----- Pattern Match Failure -----
Primary Pattern ID: https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
Statement ID:       00000000-4000-8000-0000-000000000001

Statement Templates visited:
  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-1
  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
Pattern path:
  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
  https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-3
  https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
")

(deftest match-cli-args-test
  (testing "Help Argument"
    (is (not-empty (with-out-str (match '("--help")))))
    (is (= (with-out-str (match '("-h")))
           (with-out-str (match '("-h" "-p" profile-uri))))))
  (testing "Invalid Arguments"
    (is (= "No Profiles specified.\nNo Statements specified.\n"
           (with-err-str (match '()))))
    (is (= (str "Error while parsing option \"-s non-existent.json\": "
                "java.io.FileNotFoundException: non-existent.json (No such file or directory)\n"
                "No Statements specified.\n")
           (with-err-str
             (match (list "-p" profile-uri "-s" "non-existent.json")))))
    (is (= (str "Profile errors are present.\n"
                (with-out-str
                  (pan/validate-profile
                   (pan/json-profile->edn (slurp statement-uri))
                   :result :print)))
           (with-err-str
             (match (list "-p" statement-uri "-s" statement-uri)))))
    (is (= "ID error: Profile IDs are not unique\n"
           (with-err-str
             (match (list "-p" profile-uri "-p" profile-uri
                          "-s" statement-uri)))))
    (is (= "Compilation error: no Patterns to match against, or one or more Profiles lacks Patterns\n"
           (with-err-str
             (match (list "-p" profile-uri "-s" statement-uri
                          "-i" "http://random-pattern.org")))
           (with-err-str
             (match (list "-p" profile-uri
                          "-s" statement-uri
                          "-s" statement-2-uri
                          "-i" "http://random-pattern.org")))
           (with-err-str
             (match (list "-p" profile-uri
                          "-p" "test-resources/sample_profiles/cmi5.json"
                          "-s" statement-uri
                          "-i" "https://w3id.org/xapi/cmi5#toplevel")))))))

(deftest match-cli-test
  (testing "Match Passes"
    (is (true? (match (list "--profile" profile-uri
                            "--statement" statement-uri))))
    (is (true? (match (list "-p" profile-uri
                            "-s" statement-uri
                            "-s" statement-2-uri))))
    (is (true? (match (list "-p" profile-uri
                            "-s" statement-coll-uri))))
    (is (true? (match (list "-p" profile-uri
                            "-p" "test-resources/sample_profiles/cmi5.json"
                            "-s" statement-uri))))
    (is (true? (match (list "-p" profile-uri "-s" statement-uri
                            "--pattern-id" pattern-id))))
    (is (true? (match (list "-p" profile-uri "-s" statement-uri "-n")))))
  (testing "Match Fails"
    (is (= missing-profile-ref-error-str-1
           (with-out-str
             (match (list "-p" profile-uri
                          "-s" bad-statement-uri)))))
    (is (= missing-profile-ref-error-str-2
           (with-out-str
             (match (list "-p" profile-uri
                          "-s" bad-statement-2-uri)))))
    (is (= pattern-match-failure-str-1
           (with-out-str
             (match (list "-p" profile-uri
                          "-s" statement-2-uri
                          "-s" statement-uri)))))
    (is (= pattern-match-failure-str-2
           (with-out-str
             (match (list "-p" profile-uri
                          "-s" statement-2-uri
                          "-s" statement-uri
                          "--compile-nfa")))))
    (is (= pattern-match-failure-str-3
           (with-out-str
             (match (list "-p" profile-uri
                          "-s" statement-coll-uri
                          "-s" statement-2-uri)))))
    (is (= pattern-match-failure-str-4
           (with-out-str
             (match (list "-p" profile-uri
                          "-s" statement-coll-uri
                          "-s" statement-2-uri
                          "-n")))))))
