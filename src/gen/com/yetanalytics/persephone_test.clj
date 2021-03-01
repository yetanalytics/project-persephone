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

(def tc3-validator (per/profile->statement-validator tc3-profile))

(def tc3-dfas (per/compile-profile tc3-profile))

(defn run-validate-stmt-vs-profile
  "Generate a sequence of n Statements and validate them all
   using validate-statement-vs-profile."
  [n]
  (loop [stmts (take n (sim/sim-seq tc3-inputs))]
    (if-let [next-stmt (first stmts)]
      (let [err (per/validate-statement-vs-profile
                 tc3-validator
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

;; **** No optimization ****
;; 
;; ===== Criterium quick bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 6 in 6 samples of 1 calls.
;;              Execution time mean : 10.839387 sec
;;     Execution time std-deviation : 253.562308 ms
;;    Execution time lower quantile : 10.483715 sec ( 2.5%)
;;    Execution time upper quantile : 11.103982 sec (97.5%)
;;                    Overhead used : 1.698049 ns
;;
;; ===== Criterium quick bench output for (run-match-next-statement 10) =========
;; Evaluation count : 6 in 6 samples of 1 calls.
;;              Execution time mean : 1.057962 sec
;;     Execution time std-deviation : 71.280125 ms
;;    Execution time lower quantile : 1.011374 sec ( 2.5%)
;;    Execution time upper quantile : 1.178259 sec (97.5%)
;;                    Overhead used : 1.698049 ns
;;
;; **** After commit e127223859984e8510492279cdd9bf6951c417cd ****
;; 
;; ===== Criterium full bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 300 in 60 samples of 5 calls.
;;              Execution time mean : 224.369674 ms
;;     Execution time std-deviation : 23.271289 ms
;;    Execution time lower quantile : 198.941093 ms ( 2.5%)
;;    Execution time upper quantile : 272.140514 ms (97.5%)
;;                    Overhead used : 1.641107 ns
;; 
;; Approx. 48-fold speedup compared to previous/first benchmark
;; 
;; **** After commit 0b066377bc0e4748e56d9afa7b54786e63b46d8b ****
;;
;; ===== Criterium full bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 420 in 60 samples of 7 calls.
;;              Execution time mean : 169.893496 ms
;;     Execution time std-deviation : 11.165940 ms
;;    Execution time lower quantile : 159.130748 ms ( 2.5%)
;;    Execution time upper quantile : 194.406254 ms (97.5%)
;;                    Overhead used : 1.641107 ns
;; Approx. 25% speedup compared to previous benchmark
;; Approx. 64-fold speedup comapred to first benchmark
;; 
;; ===== Criterium full bench output for (run-match-next-statement 10) =========
;; Evaluation count : 2280 in 60 samples of 38 calls.
;;              Execution time mean : 26.781508 ms
;;     Execution time std-deviation : 305.005347 µs
;;    Execution time lower quantile : 26.472251 ms ( 2.5%)
;;    Execution time upper quantile : 27.622609 ms (97.5%)
;;                    Overhead used : 1.641107 ns
;;
;; Approx. 40-fold speedup compared to first benchmark

(comment
  (criterium/with-progress-reporting
    (criterium/quick-bench (per/profile->statement-validator tc3-profile)))

  (criterium/with-progress-reporting
    (criterium/bench (run-validate-stmt-vs-profile 100)))

  (criterium/with-progress-reporting
    (criterium/bench (run-match-next-statement 10)))
  
  (defn regular-loop [limit]
    (loop [n limit ds []]
      (if (zero? n)
        ds
        (recur (dec n) (conj ds n)))))

  (defn transient-loop [limit]
    (loop [n limit ds (transient [])]
      (if (zero? n)
        (persistent! ds)
        (recur (dec n) (conj! ds n)))))
  
  (def fives (repeatedly 1000000 (constantly 5)))

  ;; 109.34 ms
  (criterium/quick-bench (set fives))

  ;; 7.85 ns
  (criterium/quick-bench (distinct fives))
  )

;; **** No optimization ****
;;
;; == Criterium full bench output for (profile->statement-validator tc3-profile) ==
;; Evaluation count : 60 in 60 samples of 1 calls.
;;              Execution time mean : 1.069253 sec
;;     Execution time std-deviation : 72.672616 ms
;;    Execution time lower quantile : 980.281379 ms ( 2.5%)
;;    Execution time upper quantile : 1.212416 sec (97.5%)
;;                    Overhead used : 1.730549 ns
;;
;; ======= Criterium full bench output for (compile-profile tc3-profile) =======
;; Evaluation count : 60 in 60 samples of 1 calls.
;;              Execution time mean : 19.975453 sec
;;     Execution time std-deviation : 888.041085 ms
;;    Execution time lower quantile : 19.118869 sec ( 2.5%)
;;    Execution time upper quantile : 21.831310 sec (97.5%)
;;                    Overhead used : 1.730549 ns
;;                    
;; **** After commit 0175b0e2c1c31a3d32a373dcf768f4e177d1afa6 ****
;;
;; == Criterium full bench output for (profile->statement-validator tc3-profile) ==
;; Evaluation count : 3000 in 60 samples of 50 calls.
;;              Execution time mean : 20.318170 ms
;;     Execution time std-deviation : 283.137264 µs
;;    Execution time lower quantile : 20.051612 ms ( 2.5%)
;;    Execution time upper quantile : 20.770245 ms (97.5%)
;;                    Overhead used : 1.730549 ns
;;                    
;; Approx. 53-fold speedup compared to original
;; 
;; ======= Criterium full bench output for (compile-profile tc3-profile) =======
;;
;; Evaluation count : 60 in 60 samples of 1 calls.
;;              Execution time mean : 4.093291 sec
;;     Execution time std-deviation : 26.803267 ms
;;    Execution time lower quantile : 4.064227 sec ( 2.5%)
;;    Execution time upper quantile : 4.150354 sec (97.5%)
;;                    Overhead used : 1.730549 ns
;;
;; Approx. 5-fold speedup compared to original


(comment
  (criterium/with-progress-reporting
    (criterium/bench (per/profile->statement-validator tc3-profile)))
  
  (criterium/with-progress-reporting
   (criterium/bench (per/compile-profile tc3-profile))))
