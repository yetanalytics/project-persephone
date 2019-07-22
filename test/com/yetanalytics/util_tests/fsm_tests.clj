(ns com.yetanalytics.util-tests.fsm-tests
  (:require [clojure.test :refer :all]
            [clojure.set :as cset]
            [ubergraph.core :as uber]
            [com.yetanalytics.utils.fsm :as fsm]))

;; Base FSMs
(def single-odd-fsm (fsm/transition-fsm "o" odd?))
(def single-even-fsm (fsm/transition-fsm "e" even?))

;; Composite FSMs
(def odd-then-even (fsm/sequence-fsm [single-odd-fsm single-even-fsm]))
(def odd-or-even (fsm/alternates-fsm [single-odd-fsm single-even-fsm]))
(def zero-or-more-odd (fsm/zero-or-more-fsm single-odd-fsm))
(def maybe-one-odd (fsm/optional-fsm single-odd-fsm))
(def one-or-more-odd (fsm/one-or-more-fsm single-odd-fsm))

(deftest single-odd-test
  (testing "single-odd-fsm: Test functionality of a single FSM with exactly
           one transition edge. Accepts a single odd number."
    (is (= {"o" odd?} (:symbols single-odd-fsm)))
    (is (= (cset/union #{(:start single-odd-fsm)} (:accept single-odd-fsm))
           (fsm/states single-odd-fsm)))
    (is (= {"o" (:accept single-odd-fsm)}
           (fsm/all-deltas single-odd-fsm (:start single-odd-fsm))))
    (is (= #{(:start single-odd-fsm)}
           (fsm/epsilon-closure single-odd-fsm (:start single-odd-fsm))))
    (is (= (:accept single-odd-fsm)
           (fsm/slurp-transition single-odd-fsm "o" (:accept single-odd-fsm) 1)))
    (is (= (:accept single-odd-fsm)
           (fsm/slurp-symbol single-odd-fsm (:start single-odd-fsm) 1)))
    (is (= (:accept single-odd-fsm)
           (fsm/read-next* single-odd-fsm #{(:start single-odd-fsm)} 1)))
    (is (= #{}
           (fsm/read-next* single-odd-fsm #{(:start single-odd-fsm)} 2)))
    (is (not (empty? (:accept-states (fsm/read-next single-odd-fsm 1)))))
    (is (:rejected-last (->> #{}
                             (fsm/read-next single-odd-fsm 1)
                             (fsm/read-next single-odd-fsm 2))))))

(deftest odd-then-even-test
  (testing "odd-then-even fsm: Test functionality of the concatenation of two
           FSMs. Accepts one odd number, then one even number."
    (is (= {"o" odd? "e" even?} (:symbols odd-then-even)))
    (is (= (:start single-odd-fsm) (:start odd-then-even)))
    (is (= (:accept single-even-fsm) (:accept odd-then-even)))
    (is (= (cset/union #{(:start single-odd-fsm) (:start single-even-fsm)}
                       (:accept single-odd-fsm) (:accept single-even-fsm))
           (fsm/states odd-then-even)))
    (is (= {"o" (:accept single-odd-fsm)}
           (fsm/all-deltas odd-then-even (:start single-odd-fsm))))
    (is (= {"e" (:accept single-even-fsm)}
           (fsm/all-deltas odd-then-even (:start single-even-fsm))))
    (is (= {:epsilon #{(:start single-even-fsm)}}
           (fsm/all-deltas odd-then-even (-> single-odd-fsm :accept vec (get 0)))))
    (is (= {:epsilon #{(:start single-even-fsm)}}
           (fsm/all-deltas odd-then-even (-> single-odd-fsm :accept vec (get 0)))))
    (is (= (cset/union (:accept single-odd-fsm)
                       #{(:start single-even-fsm)})
           (fsm/epsilon-closure odd-then-even
                                (-> single-odd-fsm :accept vec (get 0)))))
    (is (= (:accept odd-then-even)
           (fsm/epsilon-closure odd-then-even
                                (-> single-even-fsm :accept vec (get 0)))))
    (is (= #{(:start single-odd-fsm)}
           (fsm/epsilon-closure odd-then-even (:start odd-then-even))))
    (is (= #{(:start single-even-fsm)}
           (fsm/epsilon-closure odd-then-even (:start single-even-fsm))))
    (is (= (:accept single-odd-fsm)
           (fsm/read-next* odd-then-even #{(:start odd-then-even)} 1)))
    (is (= (:accept single-even-fsm)
           (fsm/read-next* odd-then-even #{(:start single-even-fsm)} 2)))
    (is (= (:accept odd-then-even)
           (fsm/read-next* odd-then-even (:accept single-odd-fsm) 2)))
    (is (= #{}
           (fsm/read-next* odd-then-even #{(:start odd-then-even)} 2)))
    (is (= #{}
           (fsm/read-next* odd-then-even #{(:start single-even-fsm)} 1)))
    (is (= #{}
           (fsm/read-next* odd-then-even (:accept single-odd-fsm) 1)))
    (is (not (:rejected-last (->> #{}
                                  (fsm/read-next odd-then-even 1)
                                  (fsm/read-next odd-then-even 2)))))
    (is (:rejected-last (->> #{}
                             (fsm/read-next odd-then-even 1)
                             (fsm/read-next odd-then-even 3))))
    (is (not (:rejected-last (->> #{}
                                  (fsm/read-next odd-then-even 1)
                                  (fsm/read-next odd-then-even 3)
                                  (fsm/read-next odd-then-even 2)))))
    (is (:rejected-last (->> #{}
                             (fsm/read-next odd-then-even 1)
                             (fsm/read-next odd-then-even 2)
                             (fsm/read-next odd-then-even 4)
                             (fsm/read-next odd-then-even 5))))
    (is (not (empty? (:accept-states (->> #{}
                                          (fsm/read-next odd-then-even 1)
                                          (fsm/read-next odd-then-even 2)
                                          (fsm/read-next odd-then-even 4)
                                          (fsm/read-next odd-then-even 5))))))))

(deftest odd-or-even-test
  (testing "odd-or-even fsm: Test functionality of the union of two FSMs.
           Accepts either one odd number or one even number."
    (is (= {"o" odd? "e" even?} (:symbols odd-or-even)))
    (is (= (cset/union (:accept single-odd-fsm) (:accept single-even-fsm))
           (:accept odd-or-even)))
    (is (not (= (:start single-odd-fsm) (:start odd-or-even))))
    (is (not (= (:start single-even-fsm) (:start odd-or-even))))
    (is (= (cset/union #{(:start odd-then-even)}
                       #{(:start single-odd-fsm) (:start single-even-fsm)}
                       (:accept single-odd-fsm) (:accept single-even-fsm))
           (fsm/states odd-then-even)))
    (is (= {"o" (:accept single-odd-fsm)}
           (fsm/all-deltas odd-or-even (:start single-odd-fsm))))
    (is (= {"e" (:accept single-even-fsm)}
           (fsm/all-deltas odd-or-even (:start single-even-fsm))))
    (is (= {:epsilon #{(:start single-odd-fsm) (:start single-even-fsm)}}
           (fsm/all-deltas odd-or-even (:start odd-or-even))))
    (is (= {}
           (fsm/all-deltas odd-or-even (-> odd-or-even :accept vec (get 0)))))
    (is (= #{(:start odd-or-even) (:start single-odd-fsm) (:start single-even-fsm)}
           (fsm/epsilon-closure odd-or-even (:start odd-or-even))))
    (is (= (:accept single-odd-fsm)
           (fsm/read-next* odd-or-even #{(:start single-odd-fsm)} 1)))
    (is (= (:accept single-even-fsm)
           (fsm/read-next* odd-or-even #{(:start single-even-fsm)} 2)))
    (is (= (:accept single-odd-fsm)
           (fsm/read-next* odd-or-even #{(:start odd-or-even)} 1)))
    (is (= (:accept single-even-fsm)
           (fsm/read-next* odd-or-even #{(:start odd-or-even)} 2)))
    (is (not (:rejected-last (fsm/read-next odd-or-even 1))))
    (is (:rejected-last (->> #{}
                             (fsm/read-next odd-or-even 1)
                             (fsm/read-next odd-or-even 2)
                             (fsm/read-next odd-or-even 4)
                             (fsm/read-next odd-or-even 5))))))

(deftest zero-or-more-odd-test
  (testing "zero-or-more fsm: Test functionality when using the Kleene star
           operator on an FSM. Accepts zero or more odd numbers."
    (is (= {"o" odd?} (:symbols zero-or-more-odd)))
    (is (cset/subset? (:accept single-odd-fsm) (:accept zero-or-more-odd)))
    (is (= 2 (count (:accept zero-or-more-odd))))
    (is (= 4 (count (fsm/states zero-or-more-odd))))
    (is (= (cset/union (fsm/states single-odd-fsm) #{(:start zero-or-more-odd)}
                       (:accept zero-or-more-odd))
           (fsm/states zero-or-more-odd)))
    (is (= {:epsilon #{(:start single-odd-fsm)}}
           (fsm/all-deltas zero-or-more-odd (-> single-odd-fsm :accept vec (get 0)))))
    (is (= {:epsilon (cset/union #{(:start single-odd-fsm)}
                                 (cset/difference (:accept zero-or-more-odd)
                                                  (:accept single-odd-fsm)))}
           (fsm/all-deltas zero-or-more-odd (:start zero-or-more-odd))))
    (is (= (cset/union #{(:start single-odd-fsm)} (:accept single-odd-fsm))
           (fsm/epsilon-closure zero-or-more-odd (-> single-odd-fsm :accept vec (get 0)))))
    (is (= (cset/union #{(:start zero-or-more-odd) (:start single-odd-fsm)}
                       (cset/difference (:accept zero-or-more-odd)
                                        (:accept single-odd-fsm)))
           (fsm/epsilon-closure zero-or-more-odd (:start zero-or-more-odd))))
    (is (= (:accept single-odd-fsm)
           (fsm/read-next* zero-or-more-odd #{(:start zero-or-more-odd)} 1)))
    (is (= #{}
           (fsm/read-next* zero-or-more-odd #{(:start zero-or-more-odd)} 2)))))

(deftest maybe-one-odd-test
  (testing "maybe-one-odd fsm: Test functionality when unioning the FSM with an
           epsilon transition. Accepts either one or one odd number."
    (is (= {"o" odd?} (:symbols maybe-one-odd)))
    (is (cset/subset? (:accept single-odd-fsm) (:accept maybe-one-odd)))
    (is (= 2 (count (:accept maybe-one-odd))))
    (is (= 4 (count (fsm/states maybe-one-odd))))
    (is (= (cset/union (fsm/states single-odd-fsm) #{(:start maybe-one-odd)}
                       (:accept maybe-one-odd))
           (fsm/states maybe-one-odd)))
    (is (= {:epsilon (cset/union #{(:start single-odd-fsm)}
                                 (cset/difference (:accept maybe-one-odd)
                                                  (:accept single-odd-fsm)))}
           (fsm/all-deltas maybe-one-odd (:start maybe-one-odd))))
    (is (= (cset/union #{(:start maybe-one-odd) (:start single-odd-fsm)}
                       (cset/difference (:accept maybe-one-odd)
                                        (:accept single-odd-fsm)))
           (fsm/epsilon-closure maybe-one-odd (:start maybe-one-odd))))
    (is (= (:accept single-odd-fsm)
           (fsm/read-next* maybe-one-odd #{(:start maybe-one-odd)} 1)))
    (is (= #{}
           (fsm/read-next* maybe-one-odd #{(:start maybe-one-odd)} 2)))))

(deftest one-or-more-odd-test
  (testing
   "one-or-more-odd fsm: Test functionality when performing the
           'Kleen plus' operation. Accepts one or more odd numbers."
    (is (= {"o" odd?} (:symbols one-or-more-odd)))
    (is (= (:accept single-odd-fsm) (:accept one-or-more-odd)))
    (is (= (cset/union #{(:start one-or-more-odd)} (fsm/states single-odd-fsm))
           (fsm/states one-or-more-odd)))
    (is (= {:epsilon #{(:start single-odd-fsm)}}
           (fsm/all-deltas one-or-more-odd (-> single-odd-fsm :accept vec (get 0)))))
    (is (= {:epsilon #{(:start single-odd-fsm)}}
           (fsm/all-deltas one-or-more-odd (:start one-or-more-odd))))
    (is (= (cset/union #{(:start single-odd-fsm)} (:accept single-odd-fsm))
           (fsm/epsilon-closure one-or-more-odd (-> single-odd-fsm :accept vec (get 0)))))
    (is (= #{(:start single-odd-fsm) (:start one-or-more-odd)}
           (fsm/epsilon-closure one-or-more-odd (:start one-or-more-odd))))
    (is (= (:accept one-or-more-odd)
           (fsm/read-next* one-or-more-odd #{(:start one-or-more-odd)} 1)))
    (is (= #{}
           (fsm/read-next* one-or-more-odd #{(:start one-or-more-odd)} 2)))))

;; TODO Add even more tests with crazy compositions
