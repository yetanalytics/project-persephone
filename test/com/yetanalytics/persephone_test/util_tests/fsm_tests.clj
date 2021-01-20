(ns com.yetanalytics.persephone-test.util-tests.fsm-tests
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone.utils.fsm :as fsm]))

(deftest alphatize-states-test
  (testing "State alphatization"
    (is (= [{:type        :nfa
             :symbols     {"a" odd?}
             :states      #{0 1}
             :start       0
             :accept      1
             :transitions {0 {"b" #{1}} 1 {}}}
            {:type        :nfa
             :symbols     {"b" even?}
             :states      #{2 3}
             :start       2
             :accept      3
             :transitions {2 {"b" #{3}} 3 {}}}]
           (fsm/alphatize-states
            [{:type        :nfa
              :symbols     {"a" odd?}
              :states      #{0 1}
              :start       0
              :accept      1
              :transitions {0 {"b" #{1}} 1 {}}}
             {:type        :nfa
              :symbols     {"b" even?}
              :states      #{0 1}
              :start       0
              :accept      1
              :transitions {0 {"b" #{1}} 1 {}}}])))
    (is (= [{:type :dfa
             :symbols {"a" odd?}
             :states #{0 1}
             :start 0
             :accepts #{0}
             :transitions {0 {"a" 0}}}]
           (fsm/alphatize-states
            [{:type :dfa
              :symbols {"a" odd?}
              :states #{#{0 1 2 3 4 5} #{6 7 8 9 10}}
              :start #{0 1 2 3 4 5}
              :accepts #{#{0 1 2 3 4 5}}
              :transitions {#{0 1 2 3 4 5} {"a" #{0 1 2 3 4 5}}}}])))))

(fsm/reset-counter)

;; Predicates
(defn is-a? [c] (= c "a"))
(defn is-b? [c] (= c "b"))
(defn is-c? [c] (= c "c"))

;; Base FSMs
(def a-fsm (fsm/transition-nfa "a" is-a?))
(def b-fsm (fsm/transition-nfa "b" is-b?))
(def c-fsm (fsm/transition-nfa "c" is-c?))

(deftest basic-fsm-test
  (testing "FSM that accepts a single input, like \"a\" or \"b\"."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1}
            :start       0
            :accept      1
            :transitions {0 {"a" #{1}} 1 {}}}
           a-fsm))
    (is (= {:type        :nfa
            :symbols     {"b" is-b?}
            :states      #{2 3}
            :start       2
            :accept      3
            :transitions {2 {"b" #{3}} 3 {}}}
           b-fsm))
    (is (= {:type        :nfa
            :symbols     {"c" is-c?}
            :states      #{4 5}
            :start       4
            :accept      5
            :transitions {4 {"c" #{5}} 5 {}}}
           c-fsm))))

(deftest concat-fsm-test
  (testing "FSM composed of concatenating two or more smaller FSMs."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1}
            :start       0
            :accept      1
            :transitions {0 {"a" #{1}} 1 {}}}
           (fsm/concat-nfa [a-fsm])))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3}
            :start       0
            :accept      3
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
            :accept      3
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
            :accept      5
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{2}}
                          2 {"b" #{3}}
                          3 {:epsilon #{4}}
                          4 {"c" #{5}}
                          5 {}}}
           (fsm/concat-nfa [a-fsm b-fsm c-fsm])))))

(deftest union-fsm-test
  (testing "FSM composed of unioning two or more smaller FSMs."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accept      3
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
            :accept      5
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
            :accept      5
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
            :accept      7
            :transitions {6 {:epsilon #{0 2 4}}
                          0 {"a" #{1}}
                          2 {"b" #{3}}
                          4 {"c" #{5}}
                          1 {:epsilon #{7}}
                          3 {:epsilon #{7}}
                          5 {:epsilon #{7}}
                          7 {}}}
           (fsm/union-nfa [a-fsm b-fsm c-fsm])))))

(deftest kleene-fsm-test
  (fsm/reset-counter 2)
  (testing "FSM via applying the Kleene star operation on a smaller FSM."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accept      3
            :transitions {2 {:epsilon #{0 3}}
                          0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          3 {}}}
           (fsm/kleene-nfa a-fsm)))
    ;; The "Kleene Star Inception" test
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 4 5 6 7 8 9}
            :start       8
            :accept      9
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{0 5}}
                          4 {:epsilon #{0 5}}
                          5 {:epsilon #{4 7}}
                          6 {:epsilon #{4 7}}
                          7 {:epsilon #{6 9}}
                          8 {:epsilon #{6 9}}
                          9 {}}}
           (-> a-fsm fsm/kleene-nfa fsm/kleene-nfa fsm/kleene-nfa)))))

(deftest optional-fsm-test
  (fsm/reset-counter 2)
  (testing "FSM via applying the optional (?) operation on a smaller FSM."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accept      3
            :transitions {2 {:epsilon #{0 3}}
                          0 {"a" #{1}}
                          1 {:epsilon #{3}}
                          3 {}}}
           (fsm/optional-nfa a-fsm)))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 4 5 6 7 8 9}
            :start       8
            :accept      9
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{5}}
                          4 {:epsilon #{0 5}}
                          5 {:epsilon #{7}}
                          6 {:epsilon #{4 7}}
                          7 {:epsilon #{9}}
                          8 {:epsilon #{6 9}}
                          9 {}}}
           (-> a-fsm fsm/optional-nfa fsm/optional-nfa fsm/optional-nfa)))))

(deftest plus-fsm-test
  (fsm/reset-counter 2)
  (testing "FSM via applying the one-or-more (+) operation on a smaller FSM."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 2 3}
            :start       2
            :accept      3
            :transitions {2 {:epsilon #{0}}
                          0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          3 {}}}
           (fsm/plus-nfa a-fsm)))
    (is (= {:type        :nfa
            :symbols     {"a" is-a?}
            :states      #{0 1 4 5 6 7 8 9}
            :start       8
            :accept      9
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{0 5}}
                          4 {:epsilon #{0}}
                          5 {:epsilon #{4 7}}
                          6 {:epsilon #{4}}
                          7 {:epsilon #{6 9}}
                          8 {:epsilon #{6}}
                          9 {}}}
           (-> a-fsm fsm/plus-nfa fsm/plus-nfa fsm/plus-nfa)))))

(deftest concat-of-concat-fsm-test
  (testing "Apply the concat operation twice."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?
                          "c" is-c?}
            :states      #{0 1 2 3 4 5}
            :start       0
            :accept      5
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{2}}
                          2 {"b" #{3}}
                          3 {:epsilon #{4}}
                          4 {"c" #{5}}
                          5 {}}}
           (fsm/concat-nfa [a-fsm (fsm/concat-nfa [b-fsm c-fsm])])))
    ;; Following NFAs have overlapping states - alphatization needed
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?
                          "c" is-c?}
            :states      #{0 1 2 3 4 5 6 7}
            :start       0
            :accept      7
            :transitions {0 {"a" #{1}}
                          1 {:epsilon #{2}}
                          2 {"b" #{3}}
                          3 {:epsilon #{4}}
                          4 {"b" #{5}}
                          5 {:epsilon #{6}}
                          6 {"c" #{7}}
                          7 {}}}
           (fsm/concat-nfa [(fsm/concat-nfa [a-fsm b-fsm])
                            (fsm/concat-nfa [b-fsm c-fsm])])))))

(deftest union-of-union-fsm-test
  (testing "Apply the union operation twice."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3 4 5 6 7 8 9}
            :start       8
            :accept      9
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
           (fsm/union-nfa [(fsm/union-nfa [a-fsm b-fsm]) b-fsm])))))

(deftest concat-of-kleene-test
  (testing "Apply concatenation to Kleene start FSMs."
    (is (= {:type        :nfa
            :symbols     {"a" is-a?
                          "b" is-b?}
            :states      #{0 1 2 3 4 5}
            :start       2
            :accept      5
            :transitions {2 {:epsilon #{0 3}}
                          0 {"a" #{1}}
                          1 {:epsilon #{0 3}}
                          3 {:epsilon #{4}}
                          4 {"b" #{5}}
                          5 {}}}
           (fsm/concat-nfa [(fsm/kleene-nfa a-fsm) b-fsm])))))

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
    (is (= #{0 4 5 6 7 8 9}
           (do (fsm/reset-counter 4)
               (fsm/epsilon-closure
                (-> a-fsm fsm/kleene-nfa fsm/kleene-nfa fsm/kleene-nfa)
                8))))
    (is (= #{0 1 4 5 6 7 9}
           (do (fsm/reset-counter 4)
               (fsm/epsilon-closure
                (-> a-fsm fsm/kleene-nfa fsm/kleene-nfa fsm/kleene-nfa)
                1))))))

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
                          :accept      1
                          :transitions {0 {"a" #{1 2}}}}
                         "a"
                         0)))
    (is (= [1]
           (fsm/nfa-move {:type        :nfa
                          :symbols     {"a" is-a? "b" is-b?}
                          :states      #{0 1 2}
                          :start       0
                          :accept      1
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
           (do (fsm/reset-counter 2)
               (fsm/nfa->dfa (fsm/kleene-nfa a-fsm)))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0 2 3} #{1 3}}
            :start       #{0 2 3}
            :accepts     #{#{0 2 3} #{1 3}}
            :transitions {#{0 2 3} {"a" #{1 3}}
                          #{1 3}   {}}}
           (do (fsm/reset-counter 2)
               (fsm/nfa->dfa (fsm/optional-nfa a-fsm)))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0 2} #{0 1 3}}
            :start       #{0 2}
            :accepts     #{#{0 1 3}}
            :transitions {#{0 2}   {"a" #{0 1 3}}
                          #{0 1 3} {"a" #{0 1 3}}}}
           (do (fsm/reset-counter 2)
               (fsm/nfa->dfa (fsm/plus-nfa a-fsm)))))
    (is (= {:type        :dfa
            :symbols     {"a" is-a?}
            :states      #{#{0 4 5 6 7 8 9} #{0 1 4 5 6 7 9}}
            :start       #{0 4 5 6 7 8 9}
            :accepts     #{#{0 4 5 6 7 8 9} #{0 1 4 5 6 7 9}}
            :transitions {#{0 4 5 6 7 8 9} {"a" #{0 1 4 5 6 7 9}}
                          #{0 1 4 5 6 7 9} {"a" #{0 1 4 5 6 7 9}}}}
           (do (fsm/reset-counter 4)
               (fsm/nfa->dfa (-> a-fsm
                                 fsm/kleene-nfa
                                 fsm/kleene-nfa
                                 fsm/kleene-nfa)))))
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
           (do (fsm/reset-counter)
               (fsm/nfa->dfa
                {:type        :nfa
                 :symbols     {"a" is-a?
                               "b" is-b?}
                 :states      #{0 1 2 3}
                 :start       0
                 :accept      3
                 :transitions {0 {"a" #{1} :epsilon #{2}}
                               1 {"b" #{1 3}}
                               2 {"a" #{3}  :epsilon #{1}}
                               3 {"a" #{2}}}}))))))

(deftest read-next-test
  (testing "The read-next function."
    (is (= {:state     #{1}
            :accepted? true}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next nil "a"))))
    (is (= {:state       nil
            :accepted?   false}
           (-> a-fsm fsm/nfa->dfa (fsm/read-next nil "b"))))
    (is (= {:state     nil
            :accepted? false}
           (-> a-fsm
               fsm/nfa->dfa
               (fsm/read-next {:state #{1} :accepted? true} "a"))))
    (is (= {:state     #{3}
            :accepted? true}
           (let [dfa (-> [a-fsm b-fsm] fsm/concat-nfa fsm/nfa->dfa)
                 read-nxt  (partial fsm/read-next dfa)]
             (-> nil (read-nxt "a") (read-nxt "b")))))))