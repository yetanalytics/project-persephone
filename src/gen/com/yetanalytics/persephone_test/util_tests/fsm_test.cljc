(ns com.yetanalytics.persephone-test.util-tests.fsm-test
  (:require #?@(:cljs [[clojure.test.check]
                       [clojure.test.check.generators]
                       [clojure.test.check.properties :include-macros true]])
            [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            #_{:clj-kondo/ignore [:unused-namespace]}
            [clojure.spec.test.alpha :as stest]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone-test.util-tests.fsm-spec :as fs])
  #?(:cljs (:require-macros [com.yetanalytics.persephone-test.util-tests.fsm-test
                             :refer [check]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest fsm-spec-tests
  (testing "NFA specs"
    (is (s/valid? ::fs/nfa {:type        :nfa
                            :symbols     {"a" odd?}
                            :states      #{0 1}
                            :start       0
                            :accepts     #{1}
                            :transitions {0 {"a" #{1}}
                                          1 {}}}))
    ;; Invalid start state
    (is (not (s/valid? ::fs/nfa {:type        :nfa
                                 :symbols     {"a" odd?}
                                 :states      #{0 1}
                                 :start       2
                                 :accepts     #{1}
                                 :transitions {0 {"a" #{1}}
                                               1 {}}})))
    ;; Invalid accept states
    (is (not (s/valid? ::fs/nfa {:type        :nfa
                                 :symbols     {"a" odd?}
                                 :states      #{0 1}
                                 :start       0
                                 :accepts     #{1 2}
                                 :transitions {0 {"a" #{1}}
                                               1 {}}})))
    ;; Missing transition src states
    (is (not (s/valid? ::fs/nfa {:type        :nfa
                                 :symbols     {"a" odd?}
                                 :states      #{0 1}
                                 :start       0
                                 :accepts     #{1 2}
                                 :transitions {0 {"a" #{1}}}})))
    ;; Invalid transition dest states
    (is (not (s/valid? ::fs/nfa {:type        :nfa
                                 :symbols     {"a" odd?}
                                 :states      #{0 1}
                                 :start       0
                                 :accepts     #{1}
                                 :transitions {0 {"a" #{1 2}}
                                               1 {}}})))
    ;; Invalid transition symbol
    (is (not (s/valid? ::fs/nfa {:type        :nfa
                                 :symbols     {"a" odd?}
                                 :states      #{0 1}
                                 :start       0
                                 :accepts     #{1}
                                 :transitions {0 {"b" #{1}}
                                               1 {}}}))))
  (testing "DFA specs"
    (is (s/valid? ::fs/dfa {:type        :dfa
                            :symbols     {"a" odd?}
                            :states      #{0 1}
                            :start       0
                            :accepts     #{0}
                            :transitions {0 {"a" 0}
                                          1 {}}}))
    ;; Destinations cannot be sets
    (is (not (s/valid? ::fs/dfa {:type        :dfa
                                 :symbols     {"a" odd?}
                                 :states      #{0 1}
                                 :start       0
                                 :accepts     #{0}
                                 :transitions {0 {"a" #{0}}
                                               1 {}}})))
    ;; Invalid transition dest states
    (is (not (s/valid? ::fs/dfa {:type        :dfa
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
     (check `fsm/nfa->dfa #?(:clj 200 :cljs 100))
     (check `fsm/minimize-dfa #?(:clj 500 :cljs 250))))

 ;; We do not test fsm/read-next due to the complexity of its spec, namely
 ;; the fact that the state needs to be in the DFA or else an exception will
 ;; be thrown.
 