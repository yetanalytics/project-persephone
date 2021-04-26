(ns com.yetanalytics.persephone-gen-test
  (:require [clojure.test :refer [deftest testing is]]
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

(def tc3-validator (per/profile->validator tc3-profile))

(def tc3-dfas (per/profile->fsms tc3-profile))

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
                 :validate-profile? false)]
        (if (some? err)
          (throw (ex-info "Statmenet stream not valid against tc3 Profiles"
                          {:kind      :datasim-template-test-failed
                           :statement next-stmt
                           :error     err}))
          (recur (rest stmts))))
      true)))

(defn run-match-stmt-vs-profile
  "Generate a sequence of n Statemnets and pattern match them
   using match-statement-vs-profile"
  [n]
  (loop [stmts      (take n (sim/sim-seq tc3-inputs))
         state-info {}]
    (if-let [next-stmt (first stmts)]
      (let [registration
            (get-in next-stmt ["context" "registration"] :no-registration)
            state-info'
            (per/match-statement-vs-profile tc3-dfas state-info next-stmt)
            is-rejected?
            (reduce-kv (fn [acc _ pat-si]
                         (and acc (empty? (:states pat-si))))
                       true
                       (get state-info' registration))]
        (if is-rejected?
          (throw (ex-info "Statement stream not matched by tc3 Profile"
                          {:kind       :datasim-pattern-test-failed
                           :statement  next-stmt
                           :patterns   tc3-dfas
                           :state-info state-info'}))
          (recur (rest stmts) state-info')))
      true)))

(deftest validate-stmt-vs-profile-test
  (testing "the validate-statement-vs-profile function using DATASIM"
    (is (run-validate-stmt-vs-profile 100))))

(deftest match-stmt-vs-profile-test
  (testing "the match-statement-vs-profile function using DATASIM"
    (is (run-match-stmt-vs-profile 1000))))
