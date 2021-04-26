(ns com.yetanalytics.persephone-test.template-test.predicates-test
  (:require
   [clojure.test :refer [deftest testing is are]]
   [com.yetanalytics.persephone.template.predicates :as p
    #?@(:clj [:refer [wrap-pred and-wrapped or-wrapped add-wrapped]]
        :cljs [:refer-macros [wrap-pred and-wrapped or-wrapped add-wrapped]])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-a? [a x] (= a x))
(defn is-b? [b x] (= b x))

(def is-zero?
  (-> (wrap-pred even?)
      (add-wrapped is-a? 0)
      (add-wrapped is-b? nil)))

(def is-even?
  (-> (wrap-pred even?)
      (add-wrapped is-a? nil)))

(def is-zero-2?
  (and-wrapped (wrap-pred even?) (wrap-pred zero?)))

(def is-even-2?
  (or-wrapped (wrap-pred even?) (wrap-pred zero?)))

(deftest macro-test
  (testing "template validation util macros"
    (are [expected v]
         (= expected (is-zero? v))
      nil 0
      :even? 1
      :is-a? 2)
    (are [expected v]
         (= expected (is-even? v))
      nil 0
      nil 2
      :even? 1)
    (are [expected v]
         (= expected (is-zero-2? v))
      nil 0
      :even? 1
      :zero? 2)
    (are [expected v]
         (= expected (is-even-2? v))
      nil 0
      :even? 1
      nil 2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util function tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest all-matchable?-test
  (testing "all-matchable? function: return true iff every value is matchable."
    (is (p/all-matchable? ["foo" "bar" "stan loona"]))
    (is (p/all-matchable? [])) ;; vacuously true
    (is (not (p/all-matchable? [nil nil nil])))
    (is (not (p/all-matchable? [nil nil "what the pineapple"])))))

(deftest none-matchable?-test
  (testing "none-matchable? function: return true iff no value is matchable."
    (is (p/none-matchable? []))
    (is (p/none-matchable? [nil nil nil]))
    (is (not (p/none-matchable? ["foo" "bar"])))
    (is (not (p/none-matchable? [nil nil "what the pineapple"])))))

(deftest any-matchable?-test
  (testing "any-matchable? function: return true iff some values are matchable."
    (is (p/any-matchable? [nil nil "still good"]))
    (is (p/any-matchable? ["foo" "bar" "all good"]))
    (is (not (p/any-matchable? [])))
    (is (not (p/any-matchable? [nil nil nil])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicate tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; $.actor.member[*].name = ["Andrew Downes" "Toby Nichols" "Ena Hills"]
(def name-values
  ["Andrew Downes" "Toby Nichols" "Ena Hills"])

(deftest some-any-values?-test
  (testing "some-any-values? fn: values MUST include at least one value that is
           given by 'any', ie. the collections need to intersect."
    (is (p/some-any-values? #{"Andrew Downes" "Toby Nichols"} name-values))
    (is (p/some-any-values? #{"Andrew Downes" "Will Hoyt"} name-values))
    (is (not (p/some-any-values? #{"Will Hoyt" "Milt Reder"} name-values)))
    (is (not (p/some-any-values? #{} name-values)))
    ;; any-values is undefined if there are no matchable values
    (is (not (p/some-any-values? #{} [])))
    (is (not (p/some-any-values? #{"Andrew Downes"} [nil])))))

(deftest only-all-values?-test
  (testing "only-all-values? fn: values MUST all be from the values given by
           'all'."
    (is (p/only-all-values? #{"Andrew Downes" "Toby Nichols" "Ena Hills"}
                             name-values))
    ;; Superset is okay
    (is (p/only-all-values? #{"Andrew Downes" "Toby Nichols" "Ena Hills" "Will Hoyt"}
                             name-values))
    (is (not (p/only-all-values? #{"Andrew Downes" "Toby Nichols"} name-values)))
    (is (not (p/only-all-values? #{} name-values)))))

(deftest no-none-values?-test
  (testing "no-none-values fn: values MUST NOT be included in the set given
           by 'none'."
    (is (p/no-none-values? #{"Will Hoyt" "Milt Reder"} name-values))
    (is (not (p/no-none-values? #{"Andrew Downes"} name-values)))
    (is (not (p/no-none-values? #{"Will Hoyt" "Milt Reder" "Ena Hills"}
                                 name-values)))
    (is (p/no-none-values? #{"Will Hoyt" "Milt Reder"} []))
    (is (p/no-none-values? #{"Will Hoyt" "Milt Reder"} [nil]))
    ;; If there is nothing to exclude, we should be okay
    (is (p/no-none-values? #{} name-values))
    (is (p/no-none-values? #{} []))))

(deftest no-unmatch-vals?-test
  (testing "no-unmatch-vals? fn: no unmatchable values allowed."
    (is (p/no-unmatch-vals? #{"Andrew Downes" "Toby Nichols" "Ena Hills"} []))
    (is (not (p/no-unmatch-vals? #{"Andrew Downes"} [nil nil])))
    (is (not (p/no-unmatch-vals? #{} [nil nil])))))

;; Predicates for our next tests
(def included-pred
  (p/create-included-pred {:presence "included" :any ["Andrew Downes"]}))

(def excluded-pred
  (p/create-excluded-pred {:presence "excluded"}))

(def recommended-pred
  (p/create-default-pred {:presence "recommended" :any ["Andrew Downes"]}))

(deftest create-included-pred-test
  (testing "create-included-pred function: create a predicate when presence is
           'included'. Values MUST have at least one matchable value (and no
           unmatchable values) and MUST follow any/all/none reqs."
    (is (nil? (included-pred name-values)))
    (is (= :some-any-values? (included-pred ["Will Hoyt"])))
    (is (= :any-matchable? (included-pred [])))
    (is (= :all-matchable? (included-pred ["Andrew Downes" nil])))))

(deftest create-excluded-pred-test
  (testing "create-excluded-pred function: create a predicate when presence is
           'excluded.' There MUST NOT be any matchable values."
    (is (nil? (excluded-pred [])))
    (is (nil? (excluded-pred [nil nil])))
    (is (= :none-matchable? (excluded-pred name-values)))
    (is (= :none-matchable? (excluded-pred (conj name-values nil))))))

;; The test for when presence is missing is pretty much the same. 
(deftest create-recommended-pred-test
  (testing "create-recommended-pred function: create a predicate when presence
           is 'recommended'. MUST follow any/all/none reqs."
    (is (nil? (recommended-pred [])))
    (is (nil? (recommended-pred name-values)))
    (is (= :some-any-values? (recommended-pred ["Will Hoyt"])))))
