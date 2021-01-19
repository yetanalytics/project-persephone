(ns com.yetanalytics.persephone-test.pattern-validation-test
  (:require [clojure.test :refer :all]
            [clojure.zip :as zip]
            [com.yetanalytics.persephone.pattern-validation :as pv]
            [com.yetanalytics.persephone.utils.fsm :as fsm]))

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
    (is (= {"http://foo.org/p1" {:id "http://foo.org/p1"
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
                                 :oneOrMore {:id "http://foo.org/p1"}}}
           (pv/mapify-patterns ex-profile)))))

(deftest mapify-templates-test
  (testing "mapify-templates function"
    (is (= {"http://foo.org/t1" {:id "http://foo.org/t1"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb1"}
            "http://foo.org/t2" {:id "http://foo.org/t2"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb2"}
            "http://foo.org/t3" {:id "http://foo.org/t3"
                                 :type "StatementTemplate"
                                 :verb "http://foo.org/verb3"}}
           (pv/mapify-templates ex-profile)))))

(deftest mapify-all-test
  (testing "mapify-all function: make a id-object map of Templates and Patterns
           given a Profile."
    (is (= {"http://foo.org/p1" {:id "http://foo.org/p1"
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
                                 :verb "http://foo.org/verb3"}}
           (pv/mapify-all ex-profile)))))

(deftest primary-patterns-test
  (testing "primary-patterns function: seq of primary patterns"
    (is (= '({:id "http://foo.org/p1"
              :type "Pattern"
              :primary true
              :alternates ["http://foo.org/p2"
                           "http://foo.org/t1"]}
             {:id "http://foo.org/p3"
              :type "Pattern"
              :primary true
              :oneOrMore {:id "http://foo.org/p1"}})
           (pv/primary-patterns ex-profile)))))

(deftest create-zipper-test
  (testing "create-zipper function"
    (is (= {:id "http://foo.org/p1"
            :type "Pattern"
            :primary true
            :alternates ["http://foo.org/p2" "http://foo.org/t1"]}
           (-> ex-profile :patterns (get 0))))
    (is (= {:id "http://foo.org/p1"
            :type "Pattern"
            :primary true
            :alternates ["http://foo.org/p2" "http://foo.org/t1"]}
           (-> ex-profile :patterns (get 0) pv/create-zipper zip/node)))
    (is (-> ex-profile :patterns (get 0) pv/create-zipper zip/branch?))
    (is (= '("http://foo.org/p2" "http://foo.org/t1")
           (-> ex-profile :patterns (get 0) pv/create-zipper zip/children)))
    (is (seq? (-> ex-profile :patterns (get 0) pv/create-zipper zip/children)))
    (is (= {:id "http://foo.org/p3"
            :type "Pattern"
            :primary true
            :oneOrMore {:id "http://foo.org/p1"}}
           (-> ex-profile :patterns (get 2) pv/create-zipper zip/node)))
    (is (= '("http://foo.org/p1")
           (-> ex-profile :patterns (get 2) pv/create-zipper zip/children)))
    (is (seq? (-> ex-profile :patterns (get 2) pv/create-zipper zip/children)))))

(deftest update-children-test
  (testing "update-children function"
    (is (= {:id "http://foo.org/p1"
            :type "Pattern"
            :primary true
            :alternates [{:id "http://foo.org/p2"
                          :type "Pattern"
                          :primary false
                          :sequence ["http://foo.org/t2" "http://foo.org/t3"]}
                         {:id "http://foo.org/t1"
                          :type "StatementTemplate"
                          :verb "http://foo.org/verb1"}]}
           (-> ex-profile :patterns (get 0) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)))
    (is (= {:id "http://foo.org/p2"
            :type "Pattern"
            :primary false
            :sequence [{:id "http://foo.org/t2"
                        :type "StatementTemplate"
                        :verb "http://foo.org/verb2"}
                       {:id "http://foo.org/t3"
                        :type "StatementTemplate"
                        :verb "http://foo.org/verb3"}]}
           (-> ex-profile :patterns (get 1) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)))
    (is (= (-> ex-profile :patterns (get 2) pv/create-zipper
               (pv/update-children (pv/mapify-all ex-profile)) zip/node)
           {:id "http://foo.org/p3"
            :type "Pattern"
            :primary true
            :oneOrMore [{:id "http://foo.org/p1"
                         :type "Pattern"
                         :primary true
                         :alternates ["http://foo.org/p2"
                                      "http://foo.org/t1"]}]}))))

(deftest grow-pattern-tree-test
  (testing "grow-pattern-tree function (implicitly also tests update-children)"
    (is (= {:id "http://foo.org/p1"
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
                          :verb "http://foo.org/verb1"}]}
           (pv/grow-pattern-tree (-> ex-profile :patterns (get 0))
                                 (pv/mapify-all ex-profile))))
    (is (= {:id "http://foo.org/p3"
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
                                       :verb "http://foo.org/verb1"}]}]}
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
    (is (= {:next-states (-> template-1-fsm :accepts vec)
            :rejected?   false
            :accepted?   true}
           (fsm/read-next template-1-fsm
                          nil
                          {:verb {:id "http://foo.org/verb1"}})))
    (is (= {:next-states []
            :rejected?   true
            :accepted?   false}
           (fsm/read-next template-1-fsm
                          nil
                          {:verb {:id "http://foo.org/verb9"}})))))

(deftest mechanize-pattern-test-1
  (testing "mechanize-pattern function on pattern #1"
    (is (-> (fsm/read-next pattern-1-fsm
                           nil
                           {:verb {:id "http://foo.org/verb1"}})
            :accepted?))
    (is (let [s1 (-> (fsm/read-next pattern-1-fsm
                                    nil
                                    {:verb {:id "http://foo.org/verb2"}})
                     :next-states
                     first)]
          (-> (fsm/read-next pattern-1-fsm
                             s1
                             {:verb {:id "http://foo.org/verb3"}})
              :accepted?)))
    (is (-> (fsm/read-next pattern-1-fsm
                           nil
                           {:verb {:id "http://foo.org/verb9"}})
            :rejected?))
    (is (let [s1 (-> (fsm/read-next pattern-1-fsm
                                    nil
                                    {:verb {:id "http://foo.org/verb1"}})
                     :next-states
                     first)]
          (-> (fsm/read-next pattern-1-fsm
                             s1
                             {:verb {:id "http://foo.org/verb1"}})
              :rejected?)))))

(deftest mechanize-pattern-test-2
  (testing "foo bar"
    (is (-> (fsm/read-next pattern-2-fsm
                           nil
                           {:verb {:id "http://foo.org/verb1"}})
            :accepted?))
    (is (let [s1 (-> (fsm/read-next pattern-2-fsm
                                    nil
                                    {:verb {:id "http://foo.org/verb1"}})
                     :next-states
                     first)]
          (-> (fsm/read-next pattern-2-fsm
                             s1
                             {:verb {:id "http://foo.org/verb1"}})
              :accepted?)))
    (is (let [s1 (-> (fsm/read-next pattern-2-fsm
                                    nil
                                    {:verb {:id "http://foo.org/verb1"}})
                     :next-states
                     first)]
          (-> (fsm/read-next pattern-2-fsm
                             s1
                             {:verb {:id "http://foo.org/verb9"}})
              :rejected?)))))

(deftest profile-to-fsm-test
  (testing "profile-to-fsm function"
    (is (= 2 (count (pv/profile->fsm ex-profile))))
    (is (every? #(= (-> % keys set)
                    #{:type :symbols :states :start :accepts :transitions})
                (pv/profile->fsm ex-profile)))
    (is (not (-> (fsm/read-next (-> ex-profile pv/profile->fsm first)
                                nil
                                {:id "some-stmt-uuid"
                                 :verb {:id "http://foo.org/verb1"}})
                 :rejected?)))))