(ns com.yetanalytics.persephone-test.pattern-validation-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.zip :as zip]
            [com.yetanalytics.persephone.pattern-validation :as pv]
            [com.yetanalytics.persephone.utils.fsm :as fsm]))

(def ex-profile
  {:templates [{:id   "http://foo.org/t1"
                :type "StatementTemplate"
                :verb "http://foo.org/verb1"}
               {:id   "http://foo.org/t2"
                :type "StatementTemplate"
                :verb "http://foo.org/verb2"}
               {:id   "http://foo.org/t3"
                :type "StatementTemplate"
                :verb "http://foo.org/verb3"}]
   :patterns  [{:id         "http://foo.org/p1"
                :type       "Pattern"
                :primary    true
                :alternates ["http://foo.org/p2"
                             "http://foo.org/t1"]}
               {:id       "http://foo.org/p2"
                :type     "Pattern"
                :primary  false
                :sequence ["http://foo.org/t2"
                           "http://foo.org/t3"]}
               {:id        "http://foo.org/p3"
                :type      "Pattern"
                :primary   true
                :oneOrMore "http://foo.org/p1"}]})

(deftest mapify-patterns-test
  (testing "mapify-patterns function"
    (is (= {"http://foo.org/p1" {:id         "http://foo.org/p1"
                                 :type       "Pattern"
                                 :primary    true
                                 :alternates ["http://foo.org/p2"
                                              "http://foo.org/t1"]}
            "http://foo.org/p2" {:id       "http://foo.org/p2"
                                 :type     "Pattern"
                                 :primary  false
                                 :sequence ["http://foo.org/t2"
                                            "http://foo.org/t3"]}
            "http://foo.org/p3" {:id        "http://foo.org/p3"
                                 :type      "Pattern"
                                 :primary   true
                                 :oneOrMore "http://foo.org/p1"}}
           (pv/mapify-patterns ex-profile)))))

(deftest mapify-templates-test
  (testing "mapify-templates function"
    (is (= {"http://foo.org/t1" {:id   "http://foo.org/t1"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb1"}
            "http://foo.org/t2" {:id   "http://foo.org/t2"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb2"}
            "http://foo.org/t3" {:id   "http://foo.org/t3"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb3"}}
           (pv/mapify-templates ex-profile)))))

(deftest mapify-all-test
  (testing "mapify-all function: make a id-object map of Templates and Patterns
           given a Profile."
    (is (= {"http://foo.org/p1" {:id         "http://foo.org/p1"
                                 :type       "Pattern"
                                 :primary    true
                                 :alternates ["http://foo.org/p2"
                                              "http://foo.org/t1"]}
            "http://foo.org/p2" {:id       "http://foo.org/p2"
                                 :type     "Pattern"
                                 :primary  false
                                 :sequence ["http://foo.org/t2"
                                            "http://foo.org/t3"]}
            "http://foo.org/p3" {:id        "http://foo.org/p3"
                                 :type      "Pattern"
                                 :primary   true
                                 :oneOrMore "http://foo.org/p1"}
            "http://foo.org/t1" {:id   "http://foo.org/t1"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb1"}
            "http://foo.org/t2" {:id   "http://foo.org/t2"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb2"}
            "http://foo.org/t3" {:id   "http://foo.org/t3"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb3"}}
           (pv/mapify-all ex-profile)))))

(deftest primary-patterns-test
  (testing "primary-patterns function: seq of primary patterns"
    (is (= '({:id         "http://foo.org/p1"
              :type       "Pattern"
              :primary    true
              :alternates ["http://foo.org/p2"
                           "http://foo.org/t1"]}
             {:id        "http://foo.org/p3"
              :type      "Pattern"
              :primary   true
              :oneOrMore "http://foo.org/p1"})
           (pv/primary-patterns ex-profile)))))

(deftest create-zipper-test
  (testing "create-zipper function"
    (is (= {:id         "http://foo.org/p1"
            :type       "Pattern"
            :primary    true
            :alternates ["http://foo.org/p2" "http://foo.org/t1"]}
           (-> ex-profile :patterns (get 0))))
    (is (= {:id         "http://foo.org/p1"
            :type       "Pattern"
            :primary    true
            :alternates ["http://foo.org/p2" "http://foo.org/t1"]}
           (-> ex-profile :patterns (get 0) pv/create-zipper zip/node)))
    (is (-> ex-profile :patterns (get 0) pv/create-zipper zip/branch?))
    (is (= '("http://foo.org/p2" "http://foo.org/t1")
           (-> ex-profile :patterns (get 0) pv/create-zipper zip/children)))
    (is (seq? (-> ex-profile :patterns (get 0) pv/create-zipper zip/children)))
    (is (= {:id        "http://foo.org/p3"
            :type      "Pattern"
            :primary   true
            :oneOrMore "http://foo.org/p1"}
           (-> ex-profile :patterns (get 2) pv/create-zipper zip/node)))
    (is (= '("http://foo.org/p1")
           (-> ex-profile :patterns (get 2) pv/create-zipper zip/children)))
    (is (seq? (-> ex-profile :patterns (get 2) pv/create-zipper zip/children)))))

(deftest update-children-test
  (testing "update-children function"
    (is (= {:id         "http://foo.org/p1"
            :type       "Pattern"
            :primary    true
            :alternates [{:id       "http://foo.org/p2"
                          :type     "Pattern"
                          :primary  false
                          :sequence ["http://foo.org/t2" "http://foo.org/t3"]}
                         {:id   "http://foo.org/t1"
                          :type "StatementTemplate"
                          :verb "http://foo.org/verb1"}]}
           (-> ex-profile :patterns (get 0) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)))
    (is (= {:id       "http://foo.org/p2"
            :type     "Pattern"
            :primary  false
            :sequence [{:id   "http://foo.org/t2"
                        :type "StatementTemplate"
                        :verb "http://foo.org/verb2"}
                       {:id   "http://foo.org/t3"
                        :type "StatementTemplate"
                        :verb "http://foo.org/verb3"}]}
           (-> ex-profile :patterns (get 1) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)))
    (is (= {:id        "http://foo.org/p3"
            :type      "Pattern"
            :primary   true
            :oneOrMore {:id         "http://foo.org/p1"
                        :type       "Pattern"
                        :primary    true
                        :alternates ["http://foo.org/p2"
                                     "http://foo.org/t1"]}}
           (-> ex-profile :patterns (get 2) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)))))

(deftest grow-pattern-tree-test
  (testing "grow-pattern-tree function (implicitly also tests update-children)"
    (is (= {:id         "http://foo.org/p1"
            :type       "Pattern"
            :primary    true
            :alternates [{:id       "http://foo.org/p2"
                          :type     "Pattern"
                          :primary  false
                          :sequence [{:id   "http://foo.org/t2"
                                      :type "StatementTemplate"
                                      :verb "http://foo.org/verb2"}
                                     {:id   "http://foo.org/t3"
                                      :type "StatementTemplate"
                                      :verb "http://foo.org/verb3"}]}
                         {:id   "http://foo.org/t1"
                          :type "StatementTemplate"
                          :verb "http://foo.org/verb1"}]}
           (pv/grow-pattern-tree (-> ex-profile :patterns (get 0))
                                 (pv/mapify-all ex-profile))))
    (is (= {:id        "http://foo.org/p3"
            :type      "Pattern"
            :primary   true
            :oneOrMore {:id         "http://foo.org/p1"
                        :type       "Pattern"
                        :primary    true
                        :alternates [{:id       "http://foo.org/p2"
                                      :type     "Pattern"
                                      :primary  false
                                      :sequence [{:id   "http://foo.org/t2"
                                                  :type "StatementTemplate"
                                                  :verb "http://foo.org/verb2"}
                                                 {:id   "http://foo.org/t3"
                                                  :type "StatementTemplate"
                                                  :verb "http://foo.org/verb3"}]}
                                     {:id   "http://foo.org/t1"
                                      :type "StatementTemplate"
                                      :verb "http://foo.org/verb1"}]}}
           (pv/grow-pattern-tree (-> ex-profile :patterns (get 2))
                                 (pv/mapify-all ex-profile))))))

(def template-1-fsm
  (-> ex-profile :templates (get 0) pv/pattern->fsm fsm/nfa->dfa))

(def pattern-1-fsm
  (-> ex-profile
      :patterns
      (get 0)
      (pv/grow-pattern-tree (pv/mapify-all ex-profile))
      pv/pattern-tree->fsm))

(def pattern-2-fsm
  (-> ex-profile
      :patterns
      (get 2)
      (pv/grow-pattern-tree (pv/mapify-all ex-profile))
      pv/pattern-tree->fsm))

(deftest build-node-fsm-test
  (testing "build-node-fsm function"
    (is (= {:state     (-> template-1-fsm :accepts first)
            :accepted? true}
           (fsm/read-next template-1-fsm
                          nil
                          {"verb" {"id" "http://foo.org/verb1"}})))
    (is (= {:state     nil
            :accepted? false}
           (fsm/read-next template-1-fsm
                          nil
                          {"verb" {"id" "http://foo.org/verb9"}})))))

(deftest mechanize-pattern-test-1
  (testing "mechanize-pattern function on pattern #1"
    (let [read-nxt (partial fsm/read-next pattern-1-fsm)]
      (is (-> nil
              (read-nxt {"verb" {"id" "http://foo.org/verb1"}})
              :accepted?))
      (is (-> nil
              (read-nxt {"verb" {"id" "http://foo.org/verb2"}})
              (read-nxt {"verb" {"id" "http://foo.org/verb3"}})
              :accepted?))
      (is (-> nil
              (read-nxt {"verb" {"id" "http://foo.org/verb9"}})
              :state
              nil?))
      (is (-> nil
              (read-nxt {"verb" {"id" "http://foo.org/verb1"}})
              (read-nxt {"verb" {"id" "http://foo.org/verb1"}})
              :state
              nil?)))))

(deftest mechanize-pattern-test-2
  (testing "mechanize-pattern function on pattern #2"
    (let [read-nxt (partial fsm/read-next pattern-2-fsm)]
      (is (-> nil
              (read-nxt {"verb" {"id" "http://foo.org/verb1"}})
              :accepted?))
      (is (-> nil
              (read-nxt {"verb" {"id" "http://foo.org/verb1"}})
              (read-nxt {"verb" {"id" "http://foo.org/verb1"}})
              :accepted?))
      (is (-> nil
              (read-nxt {"verb" {"id" "http://foo.org/verb1"}})
              (read-nxt {"verb" {"id" "http://foo.org/verb9"}})
              :state
              nil?)))))

(deftest profile-to-fsm-test
  (testing "profile-to-fsm function"
    (let [pattern-fsms (pv/profile->fsms ex-profile)
          read-nxt-1   (partial fsm/read-next (first pattern-fsms))
          read-nxt-2   (partial fsm/read-next (second pattern-fsms))
          stmt-1       {"id" "some-stmt-uuid"
                        "verb" {"id" "http://foo.org/verb1"}}
          stmt-2       {"id"   "some-stmt-uuid"
                        "verb" {"id" "http://foo.org/verb2"}}
          stmt-3       {"id"   "some-stmt-uuid"
                        "verb" {"id" "http://foo.org/verb3"}}]
      (is (= 2 (count pattern-fsms)))
      (is (every? #(= (-> % keys set)
                      #{:type :symbols :states :start :accepts :transitions})
                  (pv/profile->fsms ex-profile)))
      (is (-> nil
              (read-nxt-1 stmt-1)
              :accepted?))
      (is (-> nil
              (read-nxt-1 stmt-2)
              (read-nxt-1 stmt-3)
              :accepted?))
      (is (-> nil
              (read-nxt-2 stmt-1)
              :accepted?))
      (is (-> nil
              (read-nxt-2 stmt-1)
              (read-nxt-2 stmt-1)
              (read-nxt-2 stmt-1)
              (read-nxt-2 stmt-1)
              :accepted?)))))
