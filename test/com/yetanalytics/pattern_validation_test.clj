(ns com.yetanalytics.pattern-validation-test
  (:require [clojure.test :refer :all]
            [clojure.set :as cset]
            [clojure.zip :as zip]
            [com.yetanalytics.pattern-validation :as pv]
            [com.yetanalytics.utils.fsm :as fsm]))

(def ex-profile
  {:templates [{:id "http://foo.org/t1"
                :type "StatementTemplate"
                :verb "http://foo.org/verb1"}
               {:id "http://foo.org/t2"
                :type "StatementTemplate"
                :verb "http://foo.org/verb2"}
               {:id "http://foo.org/t3"
                :type "StatementTemplate"
                :verb "http://foo.org/verb3"}]
   :patterns [{:id "http://foo.org/p1"
               :type "Pattern"
               :primary true
               :alternates ["http://foo.org/p2"
                            "http://foo.org/t1"]}
              {:id "http://foo.org/p2"
               :type "Pattern"
               :primary false
               :sequence ["http://foo.org/t2"
                          "http://foo.org/t3"]}
              {:id "http://foo.org/p3"
               :type "Pattern"
               :primary true
               :oneOrMore {:id "http://foo.org/p1"}}]})

(deftest mapify-patterns-test
  (testing "mapify-patterns function"
    (is (= (pv/mapify-patterns ex-profile)
           {"http://foo.org/p1" {:id "http://foo.org/p1"
                                 :type "Pattern"
                                 :primary true
                                 :alternates ["http://foo.org/p2"
                                              "http://foo.org/t1"]}
            "http://foo.org/p2" {:id "http://foo.org/p2"
                                 :type "Pattern"
                                 :primary false
                                 :sequence ["http://foo.org/t2"
                                            "http://foo.org/t3"]}
            "http://foo.org/p3" {:id "http://foo.org/p3"
                                 :type "Pattern"
                                 :primary true
                                 :oneOrMore {:id "http://foo.org/p1"}}}))))

(deftest mapify-templates-test
  (testing "mapify-templates function"
    (is (= (pv/mapify-templates ex-profile)
           {"http://foo.org/t1" {:id "http://foo.org/t1"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb1"}
            "http://foo.org/t2" {:id "http://foo.org/t2"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb2"}
            "http://foo.org/t3" {:id "http://foo.org/t3"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb3"}}))))

(deftest mapify-all-test
  (testing "mapify-all function: make a id-object map of Templates and Patterns
           given a Profile."
    (is (= (pv/mapify-all ex-profile)
           {"http://foo.org/p1" {:id "http://foo.org/p1"
                                 :type "Pattern"
                                 :primary true
                                 :alternates ["http://foo.org/p2"
                                              "http://foo.org/t1"]}
            "http://foo.org/p2" {:id "http://foo.org/p2"
                                 :type "Pattern"
                                 :primary false
                                 :sequence ["http://foo.org/t2"
                                            "http://foo.org/t3"]}
            "http://foo.org/p3" {:id "http://foo.org/p3"
                                 :type "Pattern"
                                 :primary true
                                 :oneOrMore {:id "http://foo.org/p1"}}
            "http://foo.org/t1" {:id "http://foo.org/t1"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb1"}
            "http://foo.org/t2" {:id "http://foo.org/t2"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb2"}
            "http://foo.org/t3" {:id "http://foo.org/t3"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb3"}}))))

(deftest create-zipper-test
  (testing "create-zipper function"
    (is (= (-> ex-profile :patterns (get 0))
           {:id "http://foo.org/p1"
            :type "Pattern"
            :primary true
            :alternates ["http://foo.org/p2" "http://foo.org/t1"]}))
    (is (= (-> ex-profile :patterns (get 0) pv/create-zipper zip/node)
           {:id "http://foo.org/p1"
            :type "Pattern"
            :primary true
            :alternates ["http://foo.org/p2" "http://foo.org/t1"]}))
    (is (-> ex-profile :patterns (get 0) pv/create-zipper zip/branch?))
    (is (= (-> ex-profile :patterns (get 0) pv/create-zipper zip/children)
           '("http://foo.org/p2" "http://foo.org/t1")))
    (is (seq? (-> ex-profile :patterns (get 0) pv/create-zipper zip/children)))
    (is (= (-> ex-profile :patterns (get 2) pv/create-zipper zip/node)
           {:id "http://foo.org/p3"
            :type "Pattern"
            :primary true
            :oneOrMore {:id "http://foo.org/p1"}}))
    (is (= (-> ex-profile :patterns (get 2) pv/create-zipper zip/children)
           '("http://foo.org/p1")))
    (is (seq? (-> ex-profile :patterns (get 2) pv/create-zipper zip/children)))))

(deftest update-children-test
  (testing "update-children function"
    (is (= (-> ex-profile :patterns (get 0) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)
           {:id "http://foo.org/p1"
            :type "Pattern"
            :primary true
            :alternates [{:id "http://foo.org/p2"
                          :type "Pattern"
                          :primary false
                          :sequence ["http://foo.org/t2" "http://foo.org/t3"]}
                         {:id "http://foo.org/t1"
                          :type "StatementTemplate"
                          :verb "http://foo.org/verb1"}]}))
    (is (= (-> ex-profile :patterns (get 1) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)
           {:id "http://foo.org/p2"
            :type "Pattern"
            :primary false
            :sequence [{:id "http://foo.org/t2"
                        :type "StatementTemplate"
                        :verb "http://foo.org/verb2"}
                       {:id "http://foo.org/t3"
                        :type "StatementTemplate"
                        :verb "http://foo.org/verb3"}]}))
    (is (= (-> ex-profile :patterns (get 2) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)
           {:id "http://foo.org/p3"
            :type "Pattern"
            :primary true
            :oneOrMore {:id "http://foo.org/p1"
                        :type "Pattern"
                        :primary true
                        :alternates ["http://foo.org/p2"
                                     "http://foo.org/t1"]}}))))

(deftest grow-pattern-tree-test
  (testing "grow-pattern-tree function (implicitly also tests update-children)"
    (is (= (pv/grow-pattern-tree (-> ex-profile :patterns (get 0))
                                 (pv/mapify-all ex-profile))
           {:id "http://foo.org/p1"
            :type "Pattern"
            :primary true
            :alternates [{:id "http://foo.org/p2"
                          :type "Pattern"
                          :primary false
                          :sequence [{:id "http://foo.org/t2"
                                      :type "StatementTemplate"
                                      :verb "http://foo.org/verb2"}
                                     {:id "http://foo.org/t3"
                                      :type "StatementTemplate"
                                      :verb "http://foo.org/verb3"}]}
                         {:id "http://foo.org/t1"
                          :type "StatementTemplate"
                          :verb "http://foo.org/verb1"}]}))
    (is (= (pv/grow-pattern-tree (-> ex-profile :patterns (get 2))
                                 (pv/mapify-all ex-profile))
           {:id "http://foo.org/p3"
            :type "Pattern"
            :primary true
            :oneOrMore [{:id "http://foo.org/p1"
                         :type "Pattern"
                         :primary true
                         :alternates [{:id "http://foo.org/p2"
                                       :type "Pattern"
                                       :primary false
                                       :sequence [{:id "http://foo.org/t2"
                                                   :type "StatementTemplate"
                                                   :verb "http://foo.org/verb2"}
                                                  {:id "http://foo.org/t3"
                                                   :type "StatementTemplate"
                                                   :verb "http://foo.org/verb3"}]}
                                      {:id "http://foo.org/t1"
                                       :type "StatementTemplate"
                                       :verb "http://foo.org/verb1"}]}]}))))

(def template-1-fsm (-> ex-profile :templates (get 0) pv/build-node-fsm))
(def pattern-1-fsm (-> ex-profile :patterns (get 0)
                       (pv/grow-pattern-tree (pv/mapify-all ex-profile))
                       pv/mechanize-pattern))
(def pattern-2-fsm (-> ex-profile :patterns (get 2)
                       (pv/grow-pattern-tree (pv/mapify-all ex-profile))
                       pv/mechanize-pattern))

(deftest build-node-fsm-test
  (testing "build-node-fsm function"
    (is (= (set (keys template-1-fsm))
           #{:symbols :start :accept :graph}))
    (is (= (:accept template-1-fsm)
           (fsm/read-next* template-1-fsm #{(:start template-1-fsm)}
                           {:verb {:id "http://foo.org/verb1"}})))
    (is (= #{}
           (fsm/read-next* template-1-fsm #{(:start template-1-fsm)}
                           {:verb {:id "http://foo.org/verb9"}})))))

(deftest mechanize-pattern-test
  (testing "mechanize-pattern function"
    (is (= (set (keys pattern-1-fsm))
           #{:symbols :start :accept :graph}))
    (is (cset/superset? (:accept pattern-1-fsm)
                        (fsm/read-next* pattern-1-fsm #{(:start pattern-1-fsm)}
                                        {:verb {:id "http://foo.org/verb1"}})))
    (is (cset/superset?
         (:accept pattern-1-fsm)
         (fsm/read-next* pattern-1-fsm
                         (fsm/read-next* pattern-1-fsm #{(:start pattern-1-fsm)}
                                         {:verb {:id "http://foo.org/verb2"}})
                         {:verb {:id "http://foo.org/verb3"}})))
    (is (= #{} (fsm/read-next* pattern-1-fsm #{(:start pattern-1-fsm)}
                               {:verb {:id "http://foo.org/verb9"}})))
    (is (= #{}
           (fsm/read-next* pattern-1-fsm
                           (fsm/read-next* pattern-1-fsm #{(:start pattern-1-fsm)}
                                           {:verb {:id "http://foo.org/verb1"}})
                           {:verb {:id "http://foo.org/verb1"}})))
    (is (= (set (keys pattern-2-fsm))
           #{:symbols :start :accept :graph}))
    (is (cset/superset?
         (:accept pattern-2-fsm)
         (fsm/read-next* pattern-2-fsm #{(:start pattern-2-fsm)}
                         {:verb {:id "http://foo.org/verb1"}})))
    (is (cset/superset?
         (:accept pattern-2-fsm)
         (fsm/read-next* pattern-2-fsm
                         (fsm/read-next* pattern-2-fsm #{(:start pattern-2-fsm)}
                                         {:verb {:id "http://foo.org/verb1"}})
                         {:verb {:id "http://foo.org/verb1"}})))
    (is (= #{}
           (fsm/read-next* pattern-2-fsm
                           (fsm/read-next* pattern-2-fsm #{(:start pattern-2-fsm)}
                                           {:verb {:id "http://foo.org/verb1"}})
                           {:verb {:id "http://foo.org/verb9"}})))))
