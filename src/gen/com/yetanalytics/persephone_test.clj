(ns com.yetanalytics.persephone-test
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

(def tc3-profiles (get tc3-inputs :profiles))

(def tc3-profile (first tc3-profiles))

(def tc3-dfas (per/compile-profile tc3-profile))

(defn run-validate-stmt-vs-profile
  "Generate a sequence of n Statements and validate them all
   using validate-statement-vs-profile."
  [n]
  (loop [stmts (take n (sim/sim-seq tc3-inputs))]
    (if-let [next-stmt (first stmts)]
      (let [errs (filter (fn [prof]
                           (per/validate-statement-vs-profile
                            prof
                            next-stmt
                            :fn-type :result ;; Return error on invalid stmt
                                  ;; TODO: Fix Pan s.t. all profiles pass
                            :validate-profile? false))
                         tc3-profiles)]
        (if (empty? errs)
          (recur (rest stmts))
          (throw (ex-info "Statmenet stream not valid against tc3 Profiles"
                          {:type      :datasim-template-test-failed
                           :statement next-stmt
                           :error     (first errs)}))))
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
