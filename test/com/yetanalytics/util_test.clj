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
  (testing "value-map test: given keys and a vector of maps, we get back a
           vector of the appropriate values."
    (is (= (util/value-map ex-map-vec :odd) [1 3]))
    (is (= (util/value-map ex-map-vec :even) [2 4]))
    (is (= (util/value-map ex-map-vec :not-exist) [nil nil]))
    ;; Multiple arguments
    (is (= (util/value-map ex-map-vec-2 :map :odd) [1 3]))
    (is (= (util/value-map ex-map-vec-2 :map :even) [2 4]))
    (is (= (util/value-map ex-map-vec-2 :not-exist :odd) [nil nil]))
    (is (= (util/value-map ex-map-vec-2 :map :not-exist) [nil nil]))
    (is (= (util/value-map ex-map-vec :map :odd) [nil nil]))))

;; TODO Test edn-to-json and read-json

(deftest split-json-path-test
  (testing "split-json-path test: given a string of multiple JSONPaths, split
           them along the pipe character."
    (is (= (util/split-json-path "$.foo") ["$.foo"]))
    (is (= (util/split-json-path "$.foo | $.bar") ["$.foo" "$.bar"]))
    (is (= (util/split-json-path "$.foo|$.bar.baz") ["$.foo" "$.bar.baz"]))
    ;; Don't split among pipe chars within brackets
    (is (= (util/split-json-path "$.foo[*] | $.bar.['b|ah']")
           ["$.foo[*]" "$.bar.['b|ah']"]))
    (is (= (util/split-json-path "$.foo | $['bar']") ["$.foo" "$['bar']"]))))

(def edn-example {:store
                  {:book
                   [{:category "reference"
                     :author "Nigel Rees"
                     :title "Sayings of the Century"
                     :price 8.95}
                    {:category "fiction"
                     :author "Evelyn Waugh"
                     :title "Sword of Honour"
                     :price 12.99}]}
                  :expensive 10})

(deftest prepare-path-test
  (testing "prepare-path test: given a JSONPath using bracket notation,
           convert it to dot notation"
    (is (= (util/prepare-path "$['store']['book'][*]['category']")
           "$.store.book[*].category"))
    (is (= (util/prepare-path "$['store']['book'][0]['category']")
           "$.store.book[0].category"))))

(util/read-json [{:foo "bar"} {:baz "boo"}] "$[*].fee")
;; TODO: These test cases should pass, but don't (because read-json is a piece
;; of garbage):
;; "$..price" -> Throws an exception
;; "$.store.book[0,1] -> Only returns first item in array
(deftest read-json-test
  (testing "read-json test: given a JSONPath, correctly evaluate a EDN data
           structure."
    (is (= (util/read-json edn-example "$.store.book[*].author")
           ["Nigel Rees" "Evelyn Waugh"]))
    (is (= (util/read-json edn-example "$.store.book[0].author")
           ["Nigel Rees"]))
    (is (= (util/read-json edn-example "$.store.book[*].price")
           [8.95 12.99]))
    (is (= (util/read-json edn-example "$.store.book[*]")
           [{:category "reference" :author "Nigel Rees"
             :title "Sayings of the Century" :price 8.95}
            {:category "fiction" :author "Evelyn Waugh"
             :title "Sword of Honour" :price 12.99}]))
    (is (= (util/read-json edn-example "$.store.book[0]")
           [{:category "reference" :author "Nigel Rees"
             :title "Sayings of the Century" :price 8.95}]))
    (is (= (util/read-json edn-example "$.*")))

    (is (= (util/read-json edn-example "$.non-existent") [nil]))
    (is (= (util/read-json edn-example "$.store.book[2]") [nil]))
    (is (= (util/read-json edn-example "$.store.book[*].blah") [nil nil]))))

; (util/read-json edn-example "$.store.book[*]..price")
