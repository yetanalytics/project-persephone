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
(def ex-map-vec-2 [{:map {:odd 1 :even 2}} {:map {:odd 3 :even 4}}])

;; TODO Add SNSD data

(deftest value-map-test
  (testing "value-map test: given a key and a vector of maps, we get back a
           vector of the appropriate values."
    (is (= (util/value-map ex-map-vec :odd) [1 3]))
    (is (= (util/value-map ex-map-vec :even) [2 4]))
    (is (= (util/value-map ex-map-vec :not-exist) [nil nil]))
    (is (= (util/value-map-double ex-map-vec :map :odd) [nil nil]))))

(deftest value-map-double-test
  (testing "value-map-double test: given a key and a vector of maps with a
           nested map inside, we get back a vector of values."
    (is (= (util/value-map-double ex-map-vec-2 :map :odd) [1 3]))
    (is (= (util/value-map-double ex-map-vec-2 :map :even) [2 4]))
    (is (= (util/value-map-double ex-map-vec-2 :not-exist :odd) [nil nil]))
    (is (= (util/value-map-double ex-map-vec-2 :map :not-exist) [nil nil]))))

;; TODO Test edn-to-json and read-json
