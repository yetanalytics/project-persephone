(ns com.yetanalytics.persephone-test.util-tests.fsm-test
  (:require [clojure.test :refer [deftest testing is]]
            #?@(:cljs [[clojure.test.check]
                       [clojure.test.check.properties :include-macros true]])
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone.utils.fsm-specs :as fspec]))

;; Spec + util tests

(deftest fsm-spec-tests
  (testing "NFA specs"
    (is (s/valid? ::fspec/nfa {:type        :nfa
                               :symbols     {"a" odd?}
                               :states      #{0 1}
                               :start       0
                               :accepts     #{1}
                               :transitions {0 {"a" #{1}}
                                             1 {}}}))
    ;; Invalid start state
    (is (not (s/valid? ::fspec/nfa {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       2
                                    :accepts     #{1}
                                    :transitions {0 {"a" #{1}}
                                                  1 {}}})))
    ;; Invalid accept states
    (is (not (s/valid? ::fspec/nfa {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1 2}
                                    :transitions {0 {"a" #{1}}
                                                  1 {}}})))
    ;; Missing transition src states
    (is (not (s/valid? ::fspec/nfa {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1 2}
                                    :transitions {0 {"a" #{1}}}})))
    ;; Invalid transition dest states
    (is (not (s/valid? ::fspec/nfa {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1}
                                    :transitions {0 {"a" #{1 2}}
                                                  1 {}}})))
    ;; Invalid transition symbol
    (is (not (s/valid? ::fspec/nfa {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1}
                                    :transitions {0 {"b" #{1}}
                                                  1 {}}}))))
  (testing "DFA specs"
    (is (s/valid? ::fspec/dfa {:type        :dfa
                               :symbols     {"a" odd?}
                               :states      #{0 1}
                               :start       0
                               :accepts     #{0}
                               :transitions {0 {"a" 0}
                                             1 {}}}))
    ;; Destinations cannot be sets
    (is (not (s/valid? ::fspec/dfa {:type        :dfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{0}
                                    :transitions {0 {"a" #{0}}
                                                  1 {}}})))
    ;; Invalid transition dest states
    (is (not (s/valid? ::fspec/dfa {:type        :dfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{0}
                                    :transitions {0 {"a" 0}
                                                  1 {"a" 2}}})))))

(deftest alphatize-states-test
  (testing "State alphatization"
    (is (= [{:type        :nfa
             :symbols     {"a" odd?}
             :states      #{0 1}
             :start       0
             :accepts     #{1}
             :transitions {0 {"b" #{1}} 1 {}}}
            {:type        :nfa
             :symbols     {"b" even?}
             :states      #{2 3}
             :start       2
             :accepts     #{3}
             :transitions {2 {"b" #{3}} 3 {}}}]
           (fsm/alphatize-states
            [{:type        :nfa
              :symbols     {"a" odd?}
              :states      #{0 1}
              :start       0
              :accepts     #{1}
              :transitions {0 {"b" #{1}} 1 {}}}
             {:type        :nfa
              :symbols     {"b" even?}
              :states      #{0 1}
              :start       0
              :accepts     #{1}
              :transitions {0 {"b" #{1}} 1 {}}}])))
    (is (= [{:type        :dfa
             :symbols     {"a" odd?}
             :states      #{0 1}
             :start       0
             :accepts     #{0}
             :transitions {0 {"a" 0}}}]
           (fsm/alphatize-states
            [{:type        :dfa
              :symbols     {"a" odd?}
              :states      #{#{0 1 2 3 4 5} #{6 7 8 9 10}}
              :start       #{0 1 2 3 4 5}
              :accepts     #{#{0 1 2 3 4 5}}
              :transitions {#{0 1 2 3 4 5} {"a" #{0 1 2 3 4 5}}}}])))))

;; Create building blocks

(defn- sort-states [fsm]
  (update fsm :states #(apply sorted-set %)))

;; Predicates
(defn is-a? [c] (= c "a"))
(defn is-b? [c] (= c "b"))
(defn is-c? [c] (= c "c"))

;; Base FSMs
(def a-fsm (sort-states (fsm/transition-nfa "a" is-a?)))
(def b-fsm (sort-states (fsm/transition-nfa "b" is-b?)))
(def c-fsm (sort-states (fsm/transition-nfa "c" is-c?)))

(deftest basic-fsm-test
  (testing "FSM that accepts a single input, like \"a\" or \"b\"."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1}
            :start       0
            :accepts     #{1}
            :transitions {0 {"a" #{1}} 1 {}}}
           a-fsm))
    (is (= {:type        :nfa
            :symbols     {"b" is-b?}
            :states      #{0 1}
            :start       0
            :accepts     #{1}
            :transitions {0 {"b" #{1}} 1 {}}}
           b-fsm))
    (is (= {:type        :nfa
            :symbols     {"c" is-c?}
            :states      #{0 1}
            :start       0
            :accepts     #{1}
            :transitions {0 {"c" #{1}} 1 {}}}
           c-fsm))))

(deftest concat-fsm-test
  (testing "FSM composed of concatenating two or more smaller FSMs."
    (is #?(:clj (thrown? Exception (fsm/concat-nfa []))
           :cljs (thrown? js/Error (fsm/concat-nfa []))))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1}
            :start       0
            :accepts     #{1}
            :transitions {0 {"a" #{1}} 1 {}}}
           (fsm/concat-nfa [a-fsm])))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3}
            :start       0
            :accepts     #{3}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{2}}
                          2 {"b" #{3}}
                          3 {}}}
           (fsm/concat-nfa [a-fsm b-fsm])))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3}
            :start       0
            :accepts     #{3}
            :transitions {0 {"b" #{1}}
                          1 {:epsilon #{2}}
                          2 {"a" #{3}}
                          3 {}}}
           (fsm/concat-nfa [b-fsm a-fsm])))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?
                          "c" is-c?}
            :states      #{0 1 2 3 4 5}
            :start       0
            :accepts     #{5}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{2}}
                          2 {"b" #{3}}
                          3 {:epsilon #{4}}
                          4 {"c" #{5}}
                          5 {}}}
           (fsm/concat-nfa [a-fsm b-fsm c-fsm])))))

(deftest union-fsm-test
  (testing "FSM composed of unioning two or more smaller FSMs."
    (is #?(:clj (thrown? Exception (fsm/union-nfa []))
           :cljs (thrown? js/Error (fsm/union-nfa []))))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accepts     #{3}
            :transitions {2 {:epsilon #{0}}
                          0 {"a" #{1}}
                          1 {:epsilon #{3}}
                          3 {}}}
           (fsm/union-nfa [a-fsm])))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3 4 5}
            :start       4
            :accepts     #{5}
            :transitions {4 {:epsilon #{0 2}}
                          0 {"b" #{1}}
                          2 {"a" #{3}}
                          1 {:epsilon #{5}}
                          3 {:epsilon #{5}}
                          5 {}}}
           (fsm/union-nfa [b-fsm a-fsm])))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3 4 5}
            :start       4
            :accepts     #{5}
            :transitions {4 {:epsilon #{0 2}}
                          0 {"a" #{1}}
                          2 {"b" #{3}}
                          1 {:epsilon #{5}}
                          3 {:epsilon #{5}}
                          5 {}}}
           (fsm/union-nfa [a-fsm b-fsm])))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?
                          "c" is-c?}
            :states      #{0 1 2 3 4 5 6 7}
            :start       6
            :accepts     #{7}
            :transitions {6 {:epsilon #{0 2 4}}
                          0 {"a" #{1}}
                          2 {"b" #{3}}
                          4 {"c" #{5}}
                          1 {:epsilon #{7}}
                          3 {:epsilon #{7}}
                          5 {:epsilon #{7}}
                          7 {}}}
           (fsm/union-nfa [a-fsm b-fsm c-fsm])))
    #_(is (= {:type :nfa
              :symbols {"a" is-a? "b" is-b?}
              :states #{0 1 2 3}
              :start 2
              :accepts #{3}
              :transitions {2 {:epsilon #{0 1}}
                            0 {:epsilon #{3}}
                            1 {:epsilon #{3}}}}
             (fsm/union-nfa [{:type :nfa
                              :symbols {"a" is-a?}
                              :state #{0}
                              :start 0
                              :accepts #{0}
                              :transitions {0 {}}}
                             {:type :nfa
                              :symbols {"b" is-b?}
                              :state #{1}
                              :start 1
                              :accepts #{1}
                              :transitions {1 {}}}])))))

(deftest kleene-fsm-test
  (testing "FSM via applying the Kleene star operation on a smaller FSM."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accepts     #{3}
            :transitions {2 {:epsilon #{0 3}}
                          0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          3 {}}}
           (fsm/kleene-nfa a-fsm)))
    ;; The "Kleene Star Inception" test
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3 4 5 6 7}
            :start       6
            :accepts     #{7}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          2 {:epsilon #{0 3}}
                          3 {:epsilon #{2 5}}
                          4 {:epsilon #{2 5}}
                          5 {:epsilon #{4 7}}
                          6 {:epsilon #{4 7}}
                          7 {}}}
           (-> a-fsm fsm/kleene-nfa fsm/kleene-nfa fsm/kleene-nfa)))))

(deftest optional-fsm-test
  (testing "FSM via applying the optional (?) operation on a smaller FSM."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accepts     #{3}
            :transitions {2 {:epsilon #{0 3}}
                          0 {"a" #{1}}
                          1 {:epsilon #{3}}
                          3 {}}}
           (fsm/optional-nfa a-fsm)))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3 4 5 6 7}
            :start       6
            :accepts     #{7}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{3}}
                          2 {:epsilon #{0 3}}
                          3 {:epsilon #{5}}
                          4 {:epsilon #{2 5}}
                          5 {:epsilon #{7}}
                          6 {:epsilon #{4 7}}
                          7 {}}}
           (-> a-fsm fsm/optional-nfa fsm/optional-nfa fsm/optional-nfa)))))

(deftest plus-fsm-test
  (testing "FSM via applying the one-or-more (+) operation on a smaller FSM."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accepts     #{3}
            :transitions {2 {:epsilon #{0}}
                          0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          3 {}}}
           (fsm/plus-nfa a-fsm)))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3 4 5 6 7}
            :start       6
            :accepts     #{7}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          2 {:epsilon #{0}}
                          3 {:epsilon #{2 5}}
                          4 {:epsilon #{2}}
                          5 {:epsilon #{4 7}}
                          6 {:epsilon #{4}}
                          7 {}}}
           (-> a-fsm fsm/plus-nfa fsm/plus-nfa fsm/plus-nfa)))))

(deftest concat-of-concat-fsm-test
  (testing "Apply the concat operation twice."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?
                          "c" is-c?}
            :states      #{0 1 2 3 4 5}
            :start       0
            :accepts     #{5}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{2}}
                          2 {"b" #{3}}
                          3 {:epsilon #{4}}
                          4 {"c" #{5}}
                          5 {}}}
           (fsm/concat-nfa [a-fsm
                            (sort-states (fsm/concat-nfa [b-fsm c-fsm]))])))
    ;; Following NFAs have overlapping states - alphatization needed
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?
                          "c" is-c?}
            :states      #{0 1 2 3 4 5 6 7}
            :start       0
            :accepts     #{7}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{2}}
                          2 {"b" #{3}}
                          3 {:epsilon #{4}}
                          4 {"b" #{5}}
                          5 {:epsilon #{6}}
                          6 {"c" #{7}}
                          7 {}}}
           (fsm/concat-nfa [(sort-states (fsm/concat-nfa [a-fsm b-fsm]))
                            (sort-states (fsm/concat-nfa [b-fsm c-fsm]))])))))

(deftest union-of-union-fsm-test
  (testing "Apply the union operation twice."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3 4 5 6 7 8 9}
            :start       8
            :accepts     #{9}
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{5}}
                          2 {"b" #{3}}
                          3 {:epsilon #{5}}
                          4 {:epsilon #{0 2}}
                          5 {:epsilon #{9}}
                          6 {"b" #{7}}
                          7 {:epsilon #{9}}
                          8 {:epsilon #{4 6}}
                          9 {}}}
           (fsm/union-nfa [(sort-states (fsm/union-nfa [a-fsm b-fsm]))
                           b-fsm])))))

(deftest concat-of-kleene-test
  (testing "Apply concatenation to Kleene start FSMs."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3 4 5}
            :start       2
            :accepts     #{5}
            :transitions {2 {:epsilon #{0 3}}
                          0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          3 {:epsilon #{4}}
                          4 {"b" #{5}}
                          5 {}}}
           (fsm/concat-nfa [(sort-states (fsm/kleene-nfa a-fsm))
                            b-fsm])))))

;; TODO: Write tests for the other FSM combos

(deftest epsilon-closure-test
  (testing "Epsilon closure of an NFA state."
    (is (= #{0}
           (fsm/epsilon-closure a-fsm 0)))
    (is (= #{1 2}
           (fsm/epsilon-closure (fsm/concat-nfa [a-fsm b-fsm]) 1)))
    (is (= #{0 2 4 6 8}
           (fsm/epsilon-closure (fsm/union-nfa [(fsm/union-nfa [a-fsm b-fsm])
                                                b-fsm])
                                8)))
    (is (= #{0 2 3 4 5 6 7}
           (fsm/epsilon-closure
            (-> a-fsm fsm/kleene-nfa fsm/kleene-nfa fsm/kleene-nfa)
            6)))
    (is (= #{0 1 2 3 4 5 7}
           (fsm/epsilon-closure
            (-> a-fsm fsm/kleene-nfa fsm/kleene-nfa fsm/kleene-nfa)
            1)))))

(deftest move-test
  (testing "Move by one state on an NFA."
    (is (= [1]
           (fsm/nfa-move a-fsm "a" 0)))
    (is (= []
           (fsm/nfa-move a-fsm "b" 0)))
    (is (= []
           (fsm/nfa-move a-fsm "a" 1)))
    (is (= nil
           (fsm/nfa-move a-fsm "a" 2)))
    (is (= [1]
           (fsm/nfa-move (fsm/concat-nfa [a-fsm b-fsm]) "a" 0)))
    (is (= []
           (fsm/nfa-move (fsm/concat-nfa [a-fsm b-fsm]) "a" 1)))
    (is (= [1]
           (fsm/nfa-move (fsm/concat-nfa [a-fsm b-fsm]) "a" 0)))
    (is (= [1 2]
           (fsm/nfa-move {:type        :nfa
                          :symbols     {"a" is-a?}
                          :states      #{0 1 2}
                          :start       0
                          :accept      #{1}
                          :transitions {0 {"a" #{1 2}}}}
                         "a"
                         0)))
    (is (= [1]
           (fsm/nfa-move {:type        :nfa
                          :symbols     {"a" is-a? "b" is-b?}
                          :states      #{0 1 2}
                          :start       0
                          :accept      #{1}
                          :transitions {0 {"a" #{1} "b" #{2}}}}
                         "a"
                         0)))))

(deftest powerset-construction-test
  (testing "Constructing a DFA out of an NFA via the powerset construction."
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0} #{1}}
            :start       #{0}
            :accepts     #{#{1}}
            :transitions {#{0} {"a" #{1}}
                          #{1} {}}}
           (fsm/nfa->dfa a-fsm)))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{#{0} #{1 2} #{3}}
            :start       #{0}
            :accepts     #{#{3}}
            :transitions {#{0}   {"a" #{1 2}}
                          #{1 2} {"b" #{3}}
                          #{3}   {}}}
           (fsm/nfa->dfa (fsm/concat-nfa [a-fsm b-fsm]))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{#{0 2 4} #{1 5} #{3 5}}
            :start       #{0 2 4}
            :accepts     #{#{1 5} #{3 5}}
            :transitions {#{0 2 4} {"a" #{1 5} "b" #{3 5}}
                          #{1 5}   {}
                          #{3 5}   {}}}
           (fsm/nfa->dfa (fsm/union-nfa [a-fsm b-fsm]))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{#{0 2 4 6 8} #{1 5 9} #{3 5 7 9}}
            :start       #{0 2 4 6 8}
            :accepts     #{#{1 5 9} #{3 5 7 9}}
            :transitions {#{0 2 4 6 8} {"a" #{1 5 9} "b" #{3 5 7 9}}
                          #{1 5 9}     {}
                          #{3 5 7 9}   {}}}
           (fsm/nfa->dfa
            (fsm/union-nfa [(fsm/union-nfa [a-fsm b-fsm]) b-fsm]))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0 2 3} #{0 1 3}}
            :start       #{0 2 3}
            :accepts     #{#{0 2 3} #{0 1 3}}
            :transitions {#{0 2 3} {"a" #{0 1 3}}
                          #{0 1 3} {"a" #{0 1 3}}}}
           (fsm/nfa->dfa (fsm/kleene-nfa a-fsm))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0 2 3} #{1 3}}
            :start       #{0 2 3}
            :accepts     #{#{0 2 3} #{1 3}}
            :transitions {#{0 2 3} {"a" #{1 3}}
                          #{1 3}   {}}}
           (fsm/nfa->dfa (fsm/optional-nfa a-fsm))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0 2} #{0 1 3}}
            :start       #{0 2}
            :accepts     #{#{0 1 3}}
            :transitions {#{0 2}   {"a" #{0 1 3}}
                          #{0 1 3} {"a" #{0 1 3}}}}
           (fsm/nfa->dfa (fsm/plus-nfa a-fsm))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0 2 3 4 5 6 7} #{0 1 2 3 4 5 7}}
            :start       #{0 2 3 4 5 6 7}
            :accepts     #{#{0 2 3 4 5 6 7} #{0 1 2 3 4 5 7}}
            :transitions {#{0 2 3 4 5 6 7} {"a" #{0 1 2 3 4 5 7}}
                          #{0 1 2 3 4 5 7} {"a" #{0 1 2 3 4 5 7}}}}
           (fsm/nfa->dfa (-> a-fsm
                             fsm/kleene-nfa
                             fsm/kleene-nfa
                             fsm/kleene-nfa))))
    ;; Example taken from Wikipedia:
    ;; https://en.wikipedia.org/wiki/Powerset_construction
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{#{0 1 2} #{1 3} #{1 2} #{3}}
            :start       #{0 1 2}
            :accepts     #{#{1 3} #{3}}
            :transitions {#{0 1 2} {"a" #{1 3} "b" #{1 3}}
                          #{1 3}   {"a" #{1 2} "b" #{1 3}}
                          #{1 2}   {"a" #{3}   "b" #{1 3}}
                          #{3}     {"a" #{1 2}}}}
           (fsm/nfa->dfa
            {:type        :nfa
             :symbols     {"a" is-a?
                           "b" is-b?}
             :states      #{0 1 2 3}
             :start       0
             :accepts     #{3}
             :transitions {0 {"a" #{1} :epsilon #{2}}
                           1 {"b" #{1 3}}
                           2 {"a" #{3}  :epsilon #{1}}
                           3 {"a" #{2}}}})))))

(deftest minimize-dfa-test
  (testing "The minimize-dfa function and Brzozowkski's Algorithm."
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1}
            :start       0
            :accepts     #{1}
            :transitions {0 {"a" 1
                             "b" 1}
                          1 {}}}
           (fsm/minimize-dfa
            {:type        :dfa
             :symbols     {"a" is-a?
                           "b" is-b?}
             :states      #{#{0} #{1} #{2}}
             :start       #{0}
             :accepts     #{#{1} #{2}}
             :transitions {#{0} {"a" #{1}
                                 "b" #{2}}}})))
    ;; From the Wikipedia page on DFA minimization.
    ;; Note that this has one less state than the Wikipedia picture because the
    ;; missing state is a guarenteed failure state.
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1}
            :start       1
            :accepts     #{0}
            :transitions {1 {"a" 1
                             "b" 0}
                          0 {"a" 0}}}
           (fsm/minimize-dfa
            {:type        :dfa
             :symbols     {"a" is-a?
                           "b" is-b?}
             :states      #{0 1 2 3 4 5}
             :start       0
             :accepts     #{2 3 4}
             :transitions {0 {"a" 1
                              "b" 2}
                           1 {"a" 0
                              "b" 3}
                           2 {"a" 4
                              "b" 5}
                           3 {"a" 4
                              "b" 5}
                           4 {"a" 4
                              "b" 5}
                           5 {"a" 5
                              "b" 5}}})))
    ;; Concatenation: structurally identical
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2}
            :start       2
            :accepts     #{0}
            :transitions {0 {}
                          1 {"b" 0}
                          2 {"a" 1}}}
           (-> [a-fsm b-fsm]
               fsm/concat-nfa
               fsm/nfa->dfa
               fsm/minimize-dfa)))
    ;; Union: accept states consolidated into a single state
    (is (= {:type        :dfa
            :symbols     {"a" is-a?
                          "b" is-b?
                          "c" is-c?}
            :states      #{0 1}
            :start       1
            :accepts     #{0}
            :transitions {1 {"a" 0
                             "b" 0
                             "c" 0}
                          0 {}}}
           (-> [a-fsm b-fsm c-fsm]
               fsm/union-nfa
               fsm/nfa->dfa
               fsm/minimize-dfa)))
    ;; Kleene: one state w/ looping transition
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{0}
            :start       0
            :accepts     #{0}
            :transitions {0 {"a" 0}}}
           (-> a-fsm fsm/kleene-nfa fsm/nfa->dfa fsm/minimize-dfa)))
    ;; Optional: structurally identical
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{0 1}
            :start       0
            :accepts     #{0 1}
            :transitions {0 {"a" 1}
                          1 {}}}
           (-> a-fsm fsm/optional-nfa fsm/nfa->dfa fsm/minimize-dfa)))
    ;; Plus: structually identical
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{0 1}
            :start       1
            :accepts     #{0}
            :transitions {0 {"a" 0}
                          1 {"a" 0}}}
           (-> a-fsm fsm/plus-nfa fsm/nfa->dfa fsm/minimize-dfa)))))

(deftest read-next-test
  (testing "The read-next function."
    (is (= {:states    #{#{1}}
            :accepted? true}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next nil "a"))))
    (is (= {:states      #{}
            :accepted?   false}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next nil "b"))))
    (is (= {:states    #{}
            :accepted? false}
           (-> a-fsm
               fsm/nfa->dfa
               (fsm/read-next {:states #{#{1}} :accepted? true} "a"))))
    (is (= {:states    #{#{3}}
            :accepted? true}
           (let [dfa (-> [a-fsm b-fsm] fsm/concat-nfa fsm/nfa->dfa)
                 read-nxt  (partial fsm/read-next dfa)]
             (-> nil (read-nxt "a") (read-nxt "b"))))))
  (testing "The read-next function when multiple transitions can be accepted"
    (let [num-fsm {:type :dfa
                   :symbols {"even" even? "lt10" (fn [x] (< x 10))}
                   :states #{0 1 2 3 4 5 6}
                   :start 0
                   :accepts #{3 4 5 6}
                   :transitions {0 {"even" 1 "lt10" 2}
                                 1 {"even" 3 "lt10" 4}
                                 2 {"even" 5 "lt10" 6}
                                 3 {}
                                 4 {}
                                 5 {}
                                 6 {}}}
          read-nxt (partial fsm/read-next num-fsm)]
      (is (= {:states    #{1 2}
              :accepted? false}
             (-> nil (read-nxt 2))))
      (is (= {:states    #{3 4 5 6}
              :accepted? true}
             (-> nil (read-nxt 2) (read-nxt 4))))
      (is (= {:states    #{}
              :accepted? false}
             (-> nil (read-nxt 2) (read-nxt 4) (read-nxt 6)))))))

;; Generative tests

(deftest generative-tests
  (let [results
        (stest/check `#{
                        fsm/alphatize-states-fsm
                        fsm/alphatize-states
                        fsm/transition-nfa
                        fsm/concat-nfa
                        fsm/union-nfa
                        fsm/kleene-nfa
                        fsm/optional-nfa
                        fsm/plus-nfa
                        fsm/nfa->dfa
                        fsm/minimize-dfa
                        }
                     {:clojure.spec.test.check/opts {:num-tests 10}})
        {:keys [total check-passed]}
        (stest/summarize-results results)]
    (is (= total check-passed))))
