(ns com.yetanalytics.project-persephone-test
  (:require [clojure.test :refer :all]
            [com.yetanalytics.project-persephone :refer :all]))

(def will-profile (slurp "resources/sample_profiles/will-catch.json"))

(deftest compile-profile-test
  (testing "compile-profile using Will's CATCH profile"
    (is (some? (compile-profile will-profile)))))
