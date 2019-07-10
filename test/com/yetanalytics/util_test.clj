(ns com.yetanalytics.util-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.util :as util]))

(defn ex-predicate [arg1 arg2] (= arg1 arg2))

(deftest cond-on-val-test
  (testing "cond-on-val function test: if the value is nil, ignore predicate."
    (is (function? (util/cond-on-val "some" some?)))
    (is (function? (util/cond-on-val nil some?)))
    (is (function? (util/cond-on-val "some" (partial ex-predicate "foo"))))
    (is (function? (util/cond-on-val nil (partial ex-predicate "foo"))))
    (is (true? ((util/cond-on-val 2 (partial ex-predicate "foo")) "foo")))
    (is (false? ((util/cond-on-val 2 (partial ex-predicate "foo")) "bar")))
    (is (true? ((util/cond-on-val nil (partial ex-predicate "foo")) "bar")))))

(def ex-map-vec [{:odd 1 :even 2} {:odd 3 :even 4}])
;; TODO Add SNSD data

(deftest get-value-map-test
  (testing "get-value-map test: given a key and a vector of maps, we get back
           a vector of the appropriate values."
    (is (= (util/get-value-map ex-map-vec :odd) [1 3])
        (= (util/get-value-map ex-map-vec :even) [2 4]))))

;; TODO Test edn-to-json and read-json
