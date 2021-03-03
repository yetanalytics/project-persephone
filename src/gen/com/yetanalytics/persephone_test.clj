(ns com.yetanalytics.persephone-test
  (:require [clojure.test :refer [deftest testing is]]
            [criterium.core :as criterium]
            [taoensso.tufte :as tufte :refer [profile]]
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

;; **** No optimization (commit 6593ce37b91dc1a2b187d24a79a4a1a857af7f1a) ****
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
;; ===== Criterium full bench output for (run-match-next-statement 10) =========
;;
;; Evaluation count : 2220 in 60 samples of 37 calls.
;;              Execution time mean : 27.411854 ms
;;     Execution time std-deviation : 234.350360 µs
;;    Execution time lower quantile : 27.123511 ms ( 2.5%)
;;    Execution time upper quantile : 28.002287 ms (97.5%)
;;                    Overhead used : 1.730549 ns
;; 
;; Approx. 39-fold speedup compared to previous/first benchmark
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
;; 
;; **** After commit aed921db1ded7a5cf97d052d0bba9b15edfe592c ****
;; 
;; ===== Criterium full bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 420 in 60 samples of 7 calls.
;;              Execution time mean : 146.948045 ms
;;     Execution time std-deviation : 1.603593 ms
;;    Execution time lower quantile : 145.184251 ms ( 2.5%)
;;    Execution time upper quantile : 149.791996 ms (97.5%)
;;                    Overhead used : 1.652530 ns
;; Approx. 14% speedup compared to previous benchmark
;; Approx. 74-fold speedup compared to first benchmark
;;
;; ===== Criterium full bench output for (run-match-next-statement 10) =========
;; Evaluation count : 2280 in 60 samples of 38 calls.
;;              Execution time mean : 28.560289 ms
;;     Execution time std-deviation : 1.789700 ms
;;    Execution time lower quantile : 26.493428 ms ( 2.5%)
;;    Execution time upper quantile : 32.885898 ms (97.5%)
;;                    Overhead used : 1.652530 ns
;; No improvement compared to previous benchmark
;;
;; **** After commit 949270343a6730909d2e8133e3010a5e9b5cd8dd ****
;; 
;; ===== Criterium full bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 480 in 60 samples of 8 calls.
;;              Execution time mean : 127.616119 ms
;;     Execution time std-deviation : 1.321394 ms
;;    Execution time lower quantile : 126.452544 ms ( 2.5%)
;;    Execution time upper quantile : 130.317877 ms (97.5%)
;;                    Overhead used : 1.761076 ns
;; Approx. 13% speedup compared to previous benchmark
;; Approx. 85-fold speedup compared to original benchmark
;; 
;; ===== Criterium full bench output for (run-match-next-statement 10) =========
;; Evaluation count : 2400 in 60 samples of 40 calls.
;;              Execution time mean : 25.163064 ms
;;     Execution time std-deviation : 1.469391 ms
;;    Execution time lower quantile : 22.984650 ms ( 2.5%)
;;    Execution time upper quantile : 28.459845 ms (97.5%)
;;                    Overhead used : 1.761076 ns
;; Approx. 42-fold speedup compared to original benchmark
;;
;; **** After commit eecad6562edd68c2f593f0285d8addcd6f96f2e2 ****
;; 
;; ===== Criterium full bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 540 in 60 samples of 9 calls.
;;              Execution time mean : 112.928374 ms
;;     Execution time std-deviation : 2.032832 ms
;;    Execution time lower quantile : 111.440055 ms ( 2.5%)
;;    Execution time upper quantile : 118.086482 ms (97.5%)
;;                    Overhead used : 1.662059 ns
;; Approx. 12% speedup compared to previous benchmark
;; Approx. 96-fold speedup compared to original benchmark
;; 
;; **** After commit 1fe46ea0e4b33e69ff5ea732743b9da4a0843a30 ****
;; ===== Criterium full bench output for (run-validate-stmt-vs-profile 10) =====
;; Evaluation count : 540 in 60 samples of 9 calls.
;;              Execution time mean : 118.011175 ms
;;     Execution time std-deviation : 8.587749 ms
;;    Execution time lower quantile : 107.272283 ms ( 2.5%)
;;    Execution time upper quantile : 137.483773 ms (97.5%)
;;                    Overhead used : 1.646923 ns
;; No speedup compared to previous benchmark

(comment
  (criterium/with-progress-reporting
    (criterium/bench (run-validate-stmt-vs-profile 10)))

  (criterium/with-progress-reporting
    (criterium/bench (run-match-next-statement 10)))
  
  (tufte/add-basic-println-handler! {})
  (profile {} (run-validate-stmt-vs-profile 1000)))

;; **** No optimization (commit 9743089f527940e4359e1d26b7e4a3f20e6cc816) ****
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
;; Evaluation count : 2700 in 60 samples of 45 calls.
;;              Execution time mean : 22.428714 ms
;;     Execution time std-deviation : 1.534218 ms
;;    Execution time lower quantile : 21.009265 ms ( 2.5%)
;;    Execution time upper quantile : 27.627957 ms (97.5%)
;;                    Overhead used : 1.659292 ns
;;
;; Approx. 48-fold speedup compared to original
;; NOTE: Tested with REPL completely reset (not the case for FSM benchmarks)
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
;; 
;; **** After commit ea74850f588f3d5b8995b7f541a58aecb3e701be ****
;; 
;; ======= Criterium full bench output for (compile-profile tc3-profile) =======
;;
;; Evaluation count : 60 in 60 samples of 1 calls.
;;              Execution time mean : 2.109351 sec
;;     Execution time std-deviation : 15.668083 ms
;;    Execution time lower quantile : 2.087955 sec ( 2.5%)
;;    Execution time upper quantile : 2.139637 sec (97.5%)
;;                    Overhead used : 1.662933 ns
;; 
;; Approx. 2-fold speedup compared to previous commit
;; Approx. 10-fold speedup compared to original commit

(comment
  (criterium/with-progress-reporting
    (criterium/bench (per/profile->statement-validator tc3-profile)))

  (criterium/with-progress-reporting
    (criterium/bench (per/compile-profile tc3-profile))))
