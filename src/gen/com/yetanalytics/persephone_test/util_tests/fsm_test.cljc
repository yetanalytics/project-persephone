(ns com.yetanalytics.persephone-test.util-tests.fsm-test
  (:require #?@(:cljs [[clojure.test.check]
                       [clojure.test.check.generators]
                       [clojure.test.check.properties :include-macros true]])
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone-test.util-tests.fsm-spec :as fs]))

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

(comment
  (defn check [fn-sym num-tests]
    (let [result
          (stest/check fn-sym
                       {:clojure.spec.test.check/opts
                        {:num-tests num-tests
                         :seed (rand-int 2000000000)}})
          {:keys [total check-passed]}
          (stest/summarize-results result)]
      (is (= total check-passed)))))

(stest/check `fsm/concat-nfa)

(deftest generative-tests
  (testing "Generative tests for FSM specs"
    (let [results
          (stest/check `#{fsm/alphatize-states-fsm
                          fsm/alphatize-states
                          fsm/transition-nfa
                          fsm/concat-nfa
                          fsm/union-nfa
                          fsm/kleene-nfa
                          fsm/optional-nfa
                          fsm/plus-nfa
                          fsm/nfa->dfa
                          fsm/minimize-dfa}
                       {:clojure.spec.test.check/opts
                        {:num-tests #?(:clj 100 :cljs 10)
                         :seed (rand-int 2000000000)}})
          {:keys [total check-passed]}
          (stest/summarize-results results)]
      (is (= total check-passed)))))
