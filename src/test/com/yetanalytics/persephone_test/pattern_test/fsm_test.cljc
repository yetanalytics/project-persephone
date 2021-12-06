(ns com.yetanalytics.persephone-test.pattern-test.fsm-test
  #_{:clj-kondo/ignore [:unused-namespace]} ; need spec.test ns for macros
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.test.check]
            [clojure.test.check.generators]
            [clojure.test.check.properties :include-macros true]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [com.yetanalytics.persephone.pattern.fsm-spec :as fs]
            [com.yetanalytics.persephone.pattern.fsm :as fsm])
  #?(:cljs (:require-macros
            [com.yetanalytics.persephone-test.pattern-test.fsm-test
             :refer [check]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest alphatize-states-test
  (testing "State alphatization"
    (is (= [{:type        :nfa
             :symbols     {"a" odd?}
             :states      #{0 1}
             :start       0
             :accepts     #{1}
             :transitions {0 {"a" #{1}} 1 {}}}
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
              :transitions {0 {"a" #{1}} 1 {}}}
             {:type        :nfa
              :symbols     {"b" even?}
              :states      #{0 1}
              :start       0
              :accepts     #{1}
              :transitions {0 {"b" #{1}} 1 {}}}])))
    (is (= [{:type :nfa
             :symbols {"a" odd?}
             :states #{0}
             :start 0
             :accepts #{0}
             :transitions {0 {}}}
            {:type :nfa
             :symbols {"b" even?}
             :states #{1}
             :start 1
             :accepts #{1}
             :transitions {1 {}}}]
           (fsm/alphatize-states
            [{:type :nfa
              :symbols {"a" odd?}
              :states #{0}
              :start 0
              :accepts #{0}
              :transitions {0 {}}}
             {:type :nfa
              :symbols {"b" even?}
              :states #{1}
              :start 1
              :accepts #{1}
              :transitions {1 {}}}])))
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
              :transitions {#{0 1 2 3 4 5} {"a" #{0 1 2 3 4 5}}}}]))))
  (testing "State metadata alphatization"
    (is (= [{:states {0 :foo 1 :bar}}
            {:states {2 :foo 3 :bar}}]
           (mapv meta (fsm/alphatize-states
                       [(-> {:type        :nfa
                             :symbols     {"a" odd?}
                             :states      #{0 1}
                             :start       0
                             :accepts     #{1}
                             :transitions {0 {"a" #{1}} 1 {}}}
                            (with-meta {:states {0 :foo 1 :bar}}))
                        (-> {:type        :nfa
                             :symbols     {"b" even?}
                             :states      #{0 1}
                             :start       0
                             :accepts     #{1}
                             :transitions {0 {"b" #{1}} 1 {}}}
                            (with-meta {:states {0 :foo 1 :bar}}))]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Building blocks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
    (is (= {:type :nfa
            :symbols {"a" is-a? "b" is-b?}
            :states #{0 1 2 3}
            :start 2
            :accepts #{3}
            :transitions {2 {:epsilon #{0 1}}
                          0 {:epsilon #{3}}
                          1 {:epsilon #{3}}
                          3 {}}}
           (fsm/union-nfa [{:type :nfa
                            :symbols {"a" is-a?}
                            :states #{0}
                            :start 0
                            :accepts #{0}
                            :transitions {0 {}}}
                           {:type :nfa
                            :symbols {"b" is-b?}
                            :states #{1}
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DFA tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest epsilon-closure-test
  (testing "Epsilon closure of an NFA state."
    (is (= #{0}
           (fsm/epsilon-closure a-fsm 0)))
    (is (= #{1 2}
           (fsm/epsilon-closure (fsm/concat-nfa [a-fsm b-fsm]) 1)))
    (is (= #{0 2 4 6 8}
           (fsm/epsilon-closure
            (fsm/union-nfa [(fsm/union-nfa [a-fsm b-fsm])
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

(deftest powerset-construction-test
  (testing "Constructing a DFA out of an NFA via the powerset construction."
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?}
                    :states      #{0 1}
                    :start       1
                    :accepts     #{0}
                    :transitions {0 {}
                                  1 {"a" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{1}
                     :transitions {1 {}
                                   0 {"a" 1}}})
           (fsm/nfa->dfa a-fsm)))
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?
                                  "b" is-b?}
                    :states      #{0 1 2}
                    :start       2
                    :accepts     #{0}
                    :transitions {0 {}
                                  1 {"b" 0}
                                  2 {"a" 1}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?
                                   "b" is-b?}
                     :states      #{0 1 2}
                     :start       0
                     :accepts     #{2}
                     :transitions {0 {"a" 1}
                                   1 {"b" 2}
                                   2 {}}})
           (fsm/nfa->dfa (fsm/concat-nfa [a-fsm b-fsm]))))
    (is (= #?(:clj {:type        :dfa
                    :symbols    {"a" is-a?
                                 "b" is-b?}
                    :states      #{0 1 2}
                    :start       2
                    :accepts     #{0 1}
                    :transitions {0 {} 1 {} 2 {"a" 1 "b" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?
                                   "b" is-b?}
                     :states      #{0 1 2}
                     :start       0
                     :accepts     #{1 2}
                     :transitions {0 {"a" 1 "b" 2} 1 {} 2 {}}})
           (fsm/nfa->dfa (fsm/union-nfa [a-fsm b-fsm]))))
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?
                                  "b" is-b?}
                    :states      #{0 1 2}
                    :start       1
                    :accepts     #{0 2}
                    :transitions {0 {}
                                  1 {"a" 2 "b" 0}
                                  2 {}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?
                                   "b" is-b?}
                     :states      #{0 1 2}
                     :start       0
                     :accepts     #{1 2}
                     :transitions {0 {"a" 1 "b" 2}
                                   1 {}
                                   2 {}}})
           (fsm/nfa->dfa
            (fsm/union-nfa [(fsm/union-nfa [a-fsm b-fsm]) b-fsm]))))
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?}
                    :states      #{0 1}
                    :start       1
                    :accepts     #{0 1}
                    :transitions {0 {"a" 0}
                                  1 {"a" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{0 1}
                     :transitions {0 {"a" 1}
                                   1 {"a" 1}}})
           (fsm/nfa->dfa (fsm/kleene-nfa a-fsm))))
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?}
                    :states      #{0 1}
                    :start       1
                    :accepts     #{0 1}
                    :transitions {0 {}
                                  1 {"a" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{0 1}
                     :transitions {0 {"a" 1}
                                   1 {}}})
           (fsm/nfa->dfa (fsm/optional-nfa a-fsm))))
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?}
                    :states      #{0 1}
                    :start       1
                    :accepts     #{0}
                    :transitions {0 {"a" 0}
                                  1 {"a" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{1}
                     :transitions {0 {"a" 1}
                                   1 {"a" 1}}})
           (fsm/nfa->dfa (fsm/plus-nfa a-fsm))))
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?}
                    :states      #{0 1}
                    :start       1
                    :accepts     #{0 1}
                    :transitions {0 {"a" 0}
                                  1 {"a" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{0 1}
                     :transitions {0 {"a" 1}
                                   1 {"a" 1}}})
           (fsm/nfa->dfa (-> a-fsm
                             fsm/kleene-nfa
                             fsm/kleene-nfa
                             fsm/kleene-nfa))))
    ;; Example taken from Wikipedia:
    ;; https://en.wikipedia.org/wiki/Powerset_construction
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?
                                  "b" is-b?}
                    :states      #{0 1 2 3}
                    :start       1
                    :accepts     #{0 1 2 3}
                    :transitions {0 {"a" 3}
                                  1 {"a" 2 "b" 2}
                                  2 {"a" 3 "b" 2}
                                  3 {"a" 0 "b" 2}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?
                                   "b" is-b?}
                     :states      #{0 1 2 3}
                     :start       0
                     :accepts     #{0 1 2 3}
                     :transitions {0 {"a" 1 "b" 1}
                                   1 {"a" 2 "b" 1}
                                   2 {"a" 3 "b" 1}
                                   3 {"a" 2}}})
           (fsm/nfa->dfa
            {:type        :nfa
             :symbols     {"a" is-a?
                           "b" is-b?}
             :states      #{0 1 2 3}
             :start       0
             :accepts     #{2 3}
             :transitions {0 {"a" #{1} :epsilon #{2}}
                           1 {"b" #{1 3}}
                           2 {"a" #{3}  :epsilon #{1}}
                           3 {"a" #{2}}}})))))

(comment ;; Pre-alphatization version of above result
  {:type        :dfa
   :symbols     {"a" is-a?
                 "b" is-b?}
   :states      #{#{0 1 2} #{1 3} #{1 2} #{3}}
   :start       #{0 1 2}
   :accepts     #{#{0 1 2} #{1 3} #{1 2} #{3}}
   :transitions {#{0 1 2} {"a" #{1 3} "b" #{1 3}}
                 #{1 3}   {"a" #{1 2} "b" #{1 3}}
                 #{1 2}   {"a" #{3}   "b" #{1 3}}
                 #{3}     {"a" #{1 2}}}})

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
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?
                                  "b" is-b?}
                    :states      #{0 1}
                    :start       1
                    :accepts     #{0}
                    :transitions {1 {"a" 1 "b" 0}
                                  0 {"a" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?
                                   "b" is-b?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{1}
                     :transitions {0 {"a" 0 "b" 1}
                                   1 {"a" 1}}})
           (fsm/minimize-dfa
            {:type        :dfa
             :symbols     {"a" is-a?
                           "b" is-b?}
             :states      #{0 1 2 3 4 5}
             :start       0
             :accepts     #{2 3 4}
             :transitions {0 {"a" 1 "b" 2}
                           1 {"a" 0 "b" 3}
                           2 {"a" 4 "b" 5}
                           3 {"a" 4 "b" 5}
                           4 {"a" 4 "b" 5}
                           5 {"a" 5 "b" 5}}})))
    ;; Concatenation: structurally identical
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?
                                  "b" is-b?}
                    :states      #{0 1 2}
                    :start       2
                    :accepts     #{0}
                    :transitions {0 {}
                                  1 {"b" 0}
                                  2 {"a" 1}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?
                                   "b" is-b?}
                     :states      #{0 1 2}
                     :start       0
                     :accepts     #{2}
                     :transitions {0 {"a" 1}
                                   1 {"b" 2}
                                   2 {}}})
           (-> [a-fsm b-fsm]
               fsm/concat-nfa
               fsm/nfa->dfa
               fsm/minimize-dfa)))
    ;; Union: accept states consolidated into a single state
    (is (= #?(:clj {:type        :dfa
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
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?
                                   "b" is-b?
                                   "c" is-c?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{1}
                     :transitions {0 {"a" 1
                                      "b" 1
                                      "c" 1}
                                   1 {}}})
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
    (is (= #?(:clj {:type        :dfa
                    :symbols     {"a" is-a?}
                    :states      #{0 1}
                    :start       1
                    :accepts     #{0}
                    :transitions {0 {"a" 0}
                                  1 {"a" 0}}}
              :cljs {:type        :dfa
                     :symbols     {"a" is-a?}
                     :states      #{0 1}
                     :start       0
                     :accepts     #{1}
                     :transitions {0 {"a" 1}
                                   1 {"a" 1}}})
           (-> a-fsm fsm/plus-nfa fsm/nfa->dfa fsm/minimize-dfa)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Input reading tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest nfa-read-next-test
  (testing "The read-next function"
    (is (= #{{:state     1
              :accepted? true
              :visited   ["a"]}}
           (-> a-fsm (fsm/read-next nil "a"))))
    (testing "when there are only epsilon transitions"
      (let [epsilon-nfa {:type        :nfa
                         :symbols     {}
                         :states      #{0 1 2}
                         :start       0
                         :accepts     #{1 2}
                         :transitions {0 {:epsilon #{1 2}}
                                       1 {:epsilon #{1}}
                                       2 {:epsilon #{0}}}}]
        (is (= #{}
               (fsm/read-next epsilon-nfa nil "a")))))
    (testing "when there are epsilon and regular transitions"
      (let [epsilon-nfa {:type        :nfa
                         :symbols     {"even" even?}
                         :states      #{0 1 2 3 4 5}
                         :start       0
                         :accepts     #{3 4 5}
                         :transitions {0 {"even"   #{1}
                                          :epsilon #{2}}
                                       1 {"even"   #{2}
                                          :epsilon #{3}}
                                       2 {"even"   #{4}
                                          :epsilon #{5}}
                                       3 {"even"   #{3}}
                                       4 {"even"   #{4}}
                                       5 {:epsilon #{5}}}}
            eps-nfa-read (partial fsm/read-next epsilon-nfa)]
        (is (= #{{:state     1
                  :accepted? false
                  :visited   ["even"]}
                 {:state     4
                  :accepted? true
                  :visited   ["even"]}}
               (-> nil
                   (eps-nfa-read 0))))
        (is (= #{{:state     2
                  :accepted? false
                  :visited   ["even" "even"]}
                 {:state     3
                  :accepted? true
                  :visited   ["even" "even"]}
                 {:state     4
                  :accepted? true
                  :visited   ["even" "even"]}}
               (-> nil
                   (eps-nfa-read 0)
                   (eps-nfa-read 0))))
        (is (= #{{:state     3
                  :accepted? true
                  :visited   ["even" "even" "even"]}
                 {:state     4
                  :accepted? true
                  :visited   ["even" "even" "even"]}}
               (-> nil
                   (eps-nfa-read 0)
                   (eps-nfa-read 0)
                   (eps-nfa-read 0))))))))

(deftest dfa-read-next-test
  (testing "The read-next function."
    (is (= #{{:state     #?(:clj 0 :cljs 1)
              :accepted? true
              :visited   ["a"]}}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next nil "a"))))
    (is (= #{}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next nil "b"))))
    (is (= #{}
           (-> a-fsm
               fsm/nfa->dfa
               (fsm/read-next #{{:state     #?(:clj 0 :cljs 1)
                                 :accepted? true
                                 :visited   []}}
                              "a"))))
    (is (= #{{:state     #?(:clj 0 :cljs 2)
              :accepted? true
              :visited   ["a" "b"]}}
           (let [dfa      (-> [a-fsm b-fsm] fsm/concat-nfa fsm/nfa->dfa)
                 read-nxt (partial fsm/read-next dfa)]
             (-> nil (read-nxt "a") (read-nxt "b"))))))
  (testing "The read-next function on edge cases"
    (is (= #{}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next #{} "a"))))
    (is (= #{}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next
                                   #{{:state     #?(:clj 0 :cljs 1)
                                      :accepted? true
                                      :visited   []}}
                                   nil)))))
  (testing "The read-next function when multiple transitions can be accepted"
    (let [num-fsm {:type        :dfa
                   :symbols     {"even" even? "lt10" (fn [x] (< x 10))}
                   :states      #{0 1 2 3 4 5 6}
                   :start       0
                   :accepts     #{3 4 5 6}
                   :transitions {0 {"even" 1 "lt10" 2}
                                 1 {"even" 3 "lt10" 4}
                                 2 {"even" 5 "lt10" 6}
                                 3 {}
                                 4 {}
                                 5 {}
                                 6 {}}}
          read-nxt (partial fsm/read-next num-fsm)]
      (is (= #{{:state 1 :accepted? false :visited ["even"]}
               {:state 2 :accepted? false :visited ["lt10"]}}
             (-> nil (read-nxt 2))))
      (is (= #{{:state 3 :accepted? true :visited ["even" "even"]}
               {:state 4 :accepted? true :visited ["even" "lt10"]}
               {:state 5 :accepted? true :visited ["lt10" "even"]}
               {:state 6 :accepted? true :visited ["lt10" "lt10"]}}
             (-> nil (read-nxt 2) (read-nxt 4))))
      (is (= #{}
             (-> nil (read-nxt 2) (read-nxt 4) (read-nxt 6)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest fsm-spec-tests
  (testing "NFA specs"
    (is (s/valid? fs/nfa-spec {:type        :nfa
                               :symbols     {"a" odd?}
                               :states      #{0 1}
                               :start       0
                               :accepts     #{1}
                               :transitions {0 {"a" #{1}}
                                             1 {}}}))
    ;; Invalid start state
    (is (not (s/valid? fs/nfa-spec {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       2
                                    :accepts     #{1}
                                    :transitions {0 {"a" #{1}}
                                                  1 {}}})))
    ;; Invalid accept states
    (is (not (s/valid? fs/nfa-spec {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1 2}
                                    :transitions {0 {"a" #{1}}
                                                  1 {}}})))
    ;; Missing transition src states
    (is (not (s/valid? fs/nfa-spec {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1 2}
                                    :transitions {0 {"a" #{1}}}})))
    ;; Invalid transition dest states
    (is (not (s/valid? fs/nfa-spec {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1}
                                    :transitions {0 {"a" #{1 2}}
                                                  1 {}}})))
    ;; Invalid transition symbol
    (is (not (s/valid? fs/nfa-spec {:type        :nfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{1}
                                    :transitions {0 {"b" #{1}}
                                                  1 {}}}))))
  (testing "DFA specs"
    (is (s/valid? fs/dfa-spec {:type        :dfa
                               :symbols     {"a" odd?}
                               :states      #{0 1}
                               :start       0
                               :accepts     #{0}
                               :transitions {0 {"a" 0}
                                             1 {}}}))
    ;; Destinations cannot be sets
    (is (not (s/valid? fs/dfa-spec {:type        :dfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{0}
                                    :transitions {0 {"a" #{0}}
                                                  1 {}}})))
    ;; Invalid transition dest states
    (is (not (s/valid? fs/dfa-spec {:type        :dfa
                                    :symbols     {"a" odd?}
                                    :states      #{0 1}
                                    :start       0
                                    :accepts     #{0}
                                    :transitions {0 {"a" 0}
                                                  1 {"a" 2}}})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generative tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defmacro check [fn-sym num-tests]
     `(let [results#
            (stest/check ~fn-sym
                         {:clojure.spec.test.check/opts
                          {:num-tests ~num-tests
                           :seed (rand-int 2000000000)}})
            check-passed#
            (:check-passed (stest/summarize-results results#))]
        (is (= 1 check-passed#)))))

(deftest generative-tests
  (testing "Generative tests for FSM specs"
    (check `fsm/alphatize-states-fsm #?(:clj 100 :cljs 50))
    (check `fsm/alphatize-states #?(:clj 50 :cljs 25))
    (check `fsm/transition-nfa #?(:clj 1000 :cljs 500))
    (check `fsm/concat-nfa #?(:clj 50 :cljs 25))
    (check `fsm/union-nfa #?(:clj 50 :cljs 25))
    (check `fsm/kleene-nfa #?(:clj 100 :cljs 50))
    (check `fsm/optional-nfa #?(:clj 100 :cljs 50))
    (check `fsm/plus-nfa #?(:clj 100 :cljs 50))
    (check `fsm/epsilon-closure #?(:clj 100 :cljs 50))
    (check `fsm/nfa->dfa #?(:clj 200 :cljs 100))
    (check `fsm/minimize-dfa #?(:clj 500 :cljs 250))))

;; We do not test fsm/read-next due to the complexity of its spec, namely
;; the fact that the state needs to be in the DFA or else an exception will
;; be thrown.
