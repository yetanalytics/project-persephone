(ns com.yetanalytics.persephone-test.util-tests.json-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone.utils.json :as json]))

;; TODO Test edn-to-json and read-json

(deftest split-json-path-test
  (testing "split-json-path test: given a string of multiple JSONPaths, split
           them along the pipe character."
    (is (= (json/split-json-path "$.foo") ["$.foo"]))
    (is (= (json/split-json-path "$.foo | $.bar") ["$.foo" "$.bar"]))
    (is (= (json/split-json-path "$.foo|$.bar.baz") ["$.foo" "$.bar.baz"]))
    ;; Don't split among pipe chars within brackets
    (is (= (json/split-json-path "$.foo[*] | $.bar.['b|ah']")
           ["$.foo[*]" "$.bar.['b|ah']"]))
    (is (= (json/split-json-path "$.foo | $['bar']") ["$.foo" "$['bar']"]))))

;; Partial Gossener example
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
    (is (= (json/prepare-path "$['store']['book'][*]['category']")
           "$.store.book[*].category"))
    (is (= (json/prepare-path "$['store']['book'][0]['category']")
           "$.store.book[0].category"))))

(json/read-json [{:foo "bar"} {:baz "boo"}] "$[*].fee")
;; TODO: These test cases should pass, but don't (because read-json is a piece
;; of garbage):
;; "$..price" -> Throws an exception
;; "$.store.book[0,1] -> Only returns first item in array
(deftest read-json-test
  (testing "read-json test: given a JSONPath, correctly evaluate a EDN data
           structure."
    (is (= (json/read-json edn-example "$.store.book[*].author")
           ["Nigel Rees" "Evelyn Waugh"]))
    (is (= (json/read-json edn-example "$.store.book[0].author")
           ["Nigel Rees"]))
    (is (= (json/read-json edn-example "$.store.book[*].price")
           [8.95 12.99]))
    (is (= (json/read-json edn-example "$.store.book[*]")
           [{:category "reference" :author "Nigel Rees"
             :title "Sayings of the Century" :price 8.95}
            {:category "fiction" :author "Evelyn Waugh"
             :title "Sword of Honour" :price 12.99}]))
    (is (= (json/read-json edn-example "$.store.book[0]")
           [{:category "reference" :author "Nigel Rees"
             :title "Sayings of the Century" :price 8.95}]))
    (is (= (json/read-json edn-example "$['store']['book'][*]['author']")
           (json/read-json edn-example "$.store.book.*.author")))
    ;; Ummatchable values
    (is (= (json/read-json edn-example "$.non-existent") [nil]))
    (is (= (json/read-json edn-example "$.store.book[2]") [nil]))
    (is (= (json/read-json edn-example "$.store.book[*].blah") [nil nil]))))
