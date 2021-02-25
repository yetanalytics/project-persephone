(ns com.yetanalytics.persephone-test
  (:require [clojure.test :refer [deftest testing is]]
            [criterium.core :as criterium]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.datasim.sim :as sim]
            [com.yetanalytics.datasim.input :as sim-input]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATASIM tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; These are clj-only tests since DATASIM does not support cljs

(def tc3-inputs
  (-> (sim-input/from-location :input :json "test-resources/tc3_inputs.json")
      (assoc-in [:parameters :seed] (rand-int 1000000000))))

;; NOTE: from-location fills in nil "template" and "pattern" fields with nil,
;; which will cause profile validation to fail.

;; First profile contains all the Statement Templates and Patterns
(def tc3-profile (get-in tc3-inputs [:profiles 0]))

(def tc3-dfas (per/compile-profile tc3-profile))

(defn run-validate-stmt-vs-profile
  "Generate a sequence of n Statements and validate them all
   using validate-statement-vs-profile."
  [n]
  (loop [stmts (take n (sim/sim-seq tc3-inputs))]
    (if-let [next-stmt (first stmts)]
      (let [err (per/validate-statement-vs-profile
                 tc3-profile
                 next-stmt
                 :fn-type :result ;; Return error on invalid stmt
                 ;; TODO: Fix Pan s.t. all profiles pass
                 :validate-profile? false)]
        (if (some? err)
          (throw (ex-info "Statmenet stream not valid against tc3 Profiles"
                          {:type      :datasim-template-test-failed
                           :statement next-stmt
                           :error     err}))
          (recur (rest stmts))))
      true)))

(defn run-match-next-statement
  "Generate a sequence of n Statemnets and pattern match them
   using match-next-statement."
  [n]
  (loop [stmts      (take n (sim/sim-seq tc3-inputs))
         state-info {}]
    (if-let [next-stmt (first stmts)]
      (let [registration
            (get-in next-stmt ["context" "registration"] :no-registration)
            state-info'
            (per/match-next-statement tc3-dfas state-info next-stmt)
            is-rejected?
            (reduce-kv (fn [acc _ pat-si]
                         (and acc (empty? (:states pat-si))))
                       true
                       (get state-info' registration))]
        (if is-rejected?
          (throw (ex-info "Statement stream not matched by tc3 Profile"
                          {:type       :datasim-pattern-test-failed
                           :statement  next-stmt
                           :patterns   tc3-dfas
                           :state-info state-info'}))
          (recur (rest stmts) state-info')))
      true)))

(deftest validate-stmt-vs-profile-test
  (testing "the validate-statement-vs-profile function using DATASIM"
    (is (run-validate-stmt-vs-profile 10))))

(deftest match-next-statement-test
  (testing "the match-next-statement function using DATASIM"
    (is (run-match-next-statement 100))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Benchmarking (temporary)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ===== Criterium quick bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 6 in 6 samples of 1 calls.
;;              Execution time mean : 10.839387 sec
;;     Execution time std-deviation : 253.562308 ms
;;    Execution time lower quantile : 10.483715 sec ( 2.5%)
;;    Execution time upper quantile : 11.103982 sec (97.5%)
;;                    Overhead used : 1.698049 ns

;; ===== Criterium quick bench output for (run-match-next-statement 10) =========
;; Evaluation count : 6 in 6 samples of 1 calls.
;;              Execution time mean : 1.057962 sec
;;     Execution time std-deviation : 71.280125 ms
;;    Execution time lower quantile : 1.011374 sec ( 2.5%)
;;    Execution time upper quantile : 1.178259 sec (97.5%)
;;                    Overhead used : 1.698049 ns
;;
;; Found 1 outliers in 6 samples (16.6667 %)
;; 	low-severe	 1 (16.6667 %)
;;  Variance from outliers : 15.0731 % Variance is moderately inflated by outliers

(comment
  (criterium/with-progress-reporting
    (criterium/quick-bench (run-validate-stmt-vs-profile 10)))

  (criterium/with-progress-reporting
    (criterium/quick-bench (run-match-next-statement 10))))
