(ns com.yetanalytics.persephone-test.cli-test.match-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.persephone.cli.match :refer [match]]))

(def profile-uri "test-resources/sample_profiles/calibration.jsonld")
(def statement-uri "test-resources/sample_statements/calibration_1.json")
(def statement-2-uri "test-resources/sample_statements/calibration_2.json")

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

(deftest match-cli-args-test
  (testing "Help Argument"
    (is (not-empty (with-out-str (match '("--help")))))
    (is (= (with-out-str (match '("-h")))
           (with-out-str (match '("-h" "-p" profile-uri))))))
  (testing "Invalid Arguments"
    (is (= "No Profiles specified.\nNo Statements specified.\n"
           (let [s (new java.io.StringWriter)]
             (binding [*err* s]
               (match '())
               (str s)))))
    (is (= (str "Error while parsing option \"-s non-existent.json\": "
                "java.io.FileNotFoundException: non-existent.json (No such file or directory)\n"
                "No Statements specified.\n")
           (let [s (new java.io.StringWriter)]
             (binding [*err* s]
               (match (list "-p" profile-uri "-s" "non-existent.json"))
               (str s)))))
    (is (= (str "Failed to validate \"-p test-resources/sample_statements/calibration_1.json\": "
                (with-out-str
                  (pan/validate-profile
                   (pan/json-profile->edn (slurp statement-uri))
                   :result :print))
                "No Profiles specified.\n")
           (let [s (new java.io.StringWriter)]
             (binding [*err* s]
               (match (list "-p" statement-uri "-s" statement-uri))
               (str s)))))))

(deftest match-cli-test
  (testing "Match Passes"
    (is (true? (match (list "--profile" profile-uri
                            "--statement" statement-uri))))
    (is (true? (match (list "-p" profile-uri
                            "-s" statement-uri
                            "-s" statement-2-uri))))
    (is (true? (match (list "-p" profile-uri
                            "-p" "test-resources/sample_profiles/cmi5.json"
                            "-s" statement-uri))))
    (is (true? (match (list "-p" profile-uri "-s" statement-uri
                            "--pattern-id" pattern-id))))
    (is (true? (match (list "-p" profile-uri "-s" statement-uri "-n"))))
    ;; No patterns => statement vacuously matches against all
    (is (true? (match (list "-p" profile-uri "-s" statement-uri
                            "-i" "http://random-profile.org"))))
    (is (true? (match (list "-p" profile-uri
                            "-s" statement-uri
                            "-s" statement-2-uri
                            "-i" "http://random-pattern.org"))))
    ;; Pattern vacuously matches against the calibration profile even though
    ;; only cmi5 patterns is present
    (is (true? (match (list "-p" profile-uri
                            "-p" "test-resources/sample_profiles/cmi5.json"
                            "-s" statement-uri
                            "-i" "https://w3id.org/xapi/cmi5#toplevel")))))
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
                          "--compile-nfa")))))))
