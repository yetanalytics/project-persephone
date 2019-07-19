(ns com.yetanalytics.util-tests.fsm-tests
  (:require [clojure.test :refer :all]
            [clojure.set :as cset]
            [ubergraph.core :as uber]
            [com.yetanalytics.utils.fsm :as fsm]))

; (defn vowel? [c] (contains? #{\a \e \i \o \u} c))
; (defn consonant? [c] (contains? #{\b \c \d \f \g \h \j \k \l \m \n \p \q \r \s
;                                   \t \v \w \x \y \z} c))

;; Base FSMs
(def single-odd-fsm (fsm/transition-fsm "o" odd?))
(def single-even-fsm (fsm/transition-fsm "e" even?))

;; Composite FSMs
(def odd-then-even (fsm/sequence-fsm [single-odd-fsm single-even-fsm]))
(def odd-or-even (fsm/alternates-fsm [single-odd-fsm single-even-fsm]))
(def maybe-one-odd (fsm/optional-fsm single-odd-fsm))
(def zero-or-more-odd (fsm/zero-or-more-fsm single-odd-fsm))
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
    (is (= (:accept single-odd-fsm)
           (let [single-odd-fn (fsm/fsm-function single-odd-fsm)]
             (-> #{} (single-odd-fn 1) (single-odd-fn 2)))))))

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
    (is (= (:accept odd-then-even)
           (let [odd-then-even-fn (fsm/fsm-function odd-then-even)]
             (-> #{} (odd-then-even-fn 1) (odd-then-even-fn 2)))))
    (is (= (:accept odd-then-even)
           (let [odd-then-even-fn (fsm/fsm-function odd-then-even)]
             (-> #{} (odd-then-even-fn 1) (odd-then-even-fn 3) (odd-then-even-fn 2)))))
    (is (= (:accept odd-then-even)
           (let [odd-then-even-fn (fsm/fsm-function odd-then-even)]
             (-> #{} (odd-then-even-fn 1) (odd-then-even-fn 2)
                 (odd-then-even-fn 4) (odd-then-even-fn 5)))))))

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
           (fsm/read-next odd-or-even nil 1)))
    (is (= (:accept single-even-fsm)
           (fsm/read-next odd-or-even nil 2)))
    (is (= (:accept single-odd-fsm)
           (let [odd-or-even-fn (fsm/fsm-function odd-or-even)]
             (-> #{} (odd-or-even-fn 1) (odd-or-even-fn 2)
                 (odd-or-even-fn 4) (odd-or-even-fn 5)))))))

(deftest alternates-fsm-test
  (testing "alternates-fsm"
    (is (and (contains? odd-or-even :start)
             (contains? odd-or-even :accept)
             (contains? odd-or-even :states)
             (contains? odd-or-even :symbols)
             (contains? odd-or-even :graph)))
    (is (= (:states odd-or-even)
           #{(:start odd-or-even) (:accept odd-or-even)
             (:start single-odd-fsm) (:accept single-odd-fsm)
             (:start single-even-fsm) (:accept single-even-fsm)}))
    (is (= (:symbols odd-or-even) {"o" odd? "e" even?}))
    (is (= 2 (uber/out-degree (:graph odd-or-even) (:start odd-or-even))))
    (is (= 2 (uber/in-degree (:graph odd-or-even) (:accept odd-or-even))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph odd-or-even)
                                          (:start odd-or-even)
                                          (:start single-odd-fsm))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph odd-or-even)
                                          (:start odd-or-even)
                                          (:start single-even-fsm))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph odd-or-even)
                                          (:accept single-odd-fsm)
                                          (:accept odd-or-even))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph odd-or-even)
                                          (:accept single-even-fsm)
                                          (:accept odd-or-even))))))

(deftest optional-fsm-test
  (testing "optional-fsm"
    (is (and (contains? maybe-one-odd :start)
             (contains? maybe-one-odd :accept)
             (contains? maybe-one-odd :states)
             (contains? maybe-one-odd :symbols)
             (contains? maybe-one-odd :graph)))
    (is (= (:states maybe-one-odd) #{(:start maybe-one-odd)
                                     (:accept maybe-one-odd)
                                     (:start single-odd-fsm)
                                     (:accept single-odd-fsm)}))
    (is (= (:symbols maybe-one-odd) {"o" odd?}))
    (is (= {:symbol :epsilon} (uber/attrs (:graph maybe-one-odd)
                                          (:start maybe-one-odd)
                                          (:start single-odd-fsm))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph maybe-one-odd)
                                          (:accept single-odd-fsm)
                                          (:accept maybe-one-odd))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph maybe-one-odd)
                                          (:start maybe-one-odd)
                                          (:accept maybe-one-odd))))))

(deftest zero-or-more-fsm-test
  (testing "zero-or-more-fsm"
    (is (and (contains? zero-or-more-odd :start)
             (contains? zero-or-more-odd :accept)
             (contains? zero-or-more-odd :states)
             (contains? zero-or-more-odd :symbols)
             (contains? zero-or-more-odd :graph)))
    (is (= (:states zero-or-more-odd) #{(:start zero-or-more-odd)
                                        (:accept zero-or-more-odd)
                                        (:start single-odd-fsm)
                                        (:accept single-odd-fsm)}))
    (is (= (:symbols zero-or-more-odd) {"o" odd?}))
    (is (= 2 (uber/in-degree (:graph zero-or-more-odd) (:start single-odd-fsm))))
    (is (= 2 (uber/out-degree (:graph zero-or-more-odd) (:accept single-odd-fsm))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph zero-or-more-odd)
                                          (:start zero-or-more-odd)
                                          (:start single-odd-fsm))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph zero-or-more-odd)
                                          (:accept single-odd-fsm)
                                          (:accept zero-or-more-odd))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph zero-or-more-odd)
                                          (:start zero-or-more-odd)
                                          (:accept zero-or-more-odd))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph zero-or-more-odd)
                                          (:accept single-odd-fsm)
                                          (:start single-odd-fsm))))))

(deftest one-or-more-fsm-test
  (testing "one-or-more-fsm"
    (is (and (contains? one-or-more-odd :start)
             (contains? one-or-more-odd :accept)
             (contains? one-or-more-odd :states)
             (contains? one-or-more-odd :accept)
             (contains? one-or-more-odd :graph)))
    (is (= (:states one-or-more-odd) #{(:start one-or-more-odd)
                                       (:accept one-or-more-odd)
                                       (:start single-odd-fsm)
                                       (:accept single-odd-fsm)}))
    (is (= (:symbols one-or-more-odd) {"o" odd?}))
    (is (= {:symbol :epsilon} (uber/attrs (:graph one-or-more-odd)
                                          (:start one-or-more-odd)
                                          (:start single-odd-fsm))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph one-or-more-odd)
                                          (:accept single-odd-fsm)
                                          (:accept one-or-more-odd))))
    (is (= {:symbol :epsilon} (uber/attrs (:graph one-or-more-odd)
                                          (:accept single-odd-fsm)
                                          (:start single-odd-fsm))))))
