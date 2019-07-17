(ns com.yetanalytics.util-tests.fsm-tests
  (:require [clojure.test :refer :all]
            [ubergraph.core :as uber]
            [com.yetanalytics.utils.fsm :as fsm]))

(defn vowel? [c] (contains? #{\a \e \i \o \u} c))
(defn consonant? [c] (contains? #{\b \c \d \f \g \h \j \k \l \m \n \p \q \r \s
                                  \t \v \w \x \y \z} c))

(deftest transition-fsm-test
  (testing "transition-fsm function"
    (is (= 2 (count (uber/nodes (:graph (fsm/transition-fsm "v" vowel?))))))
    (is (= 1 (count (uber/edges (:graph (fsm/transition-fsm "v" vowel?))))))))
