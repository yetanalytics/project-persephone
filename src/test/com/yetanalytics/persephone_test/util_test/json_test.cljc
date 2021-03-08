(ns com.yetanalytics.persephone-test.util-test.json-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone.utils.json :as json]))

(deftest json->edn-test
  (testing "json->edn function"
    (is (= {"foo" {"bar" "baz"}}
           (json/json->edn "{\"foo\": {\"bar\": \"baz\"}}")))
    (is (= {:foo {:bar "baz"}}
           (json/json->edn "{\"foo\": {\"bar\": \"baz\"}}"
                           :keywordize? true)))
    (is (= {:_foo {:_bar "@baz"}}
           (json/json->edn "{\"@foo\": {\"@bar\": \"@baz\"}}"
                           :keywordize? true)))
    (is (= {"https://foo.org" 2}
           (json/json->edn "{\"https://foo.org\": 2}")))))

(deftest edn->json-test
  (testing "edn->json function"
    (is (= "{\"foo\":{\"bar\":\"baz\"}}"
           (json/edn->json {"foo" {"bar" "baz"}})))
    (is (= "{\"foo\":{\"bar\":\"baz\"}}"
           (json/edn->json {:foo {:bar "baz"}})))
    (is (= "{\"_foo\":{\"_bar\":\"baz\"}}"
           (json/edn->json {:_foo {:_bar "baz"}})))))
