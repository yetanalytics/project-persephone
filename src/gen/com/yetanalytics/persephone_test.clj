(ns com.yetanalytics.persephone-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.datasim.sim :as sim]
            [com.yetanalytics.datasim.input :as sim-input]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATASIM tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def tc3-inputs
  (-> (sim-input/from-location :input :json "test-resources/tc3_inputs.json")
      (assoc-in [:parameters :seed] (rand-int 1000))))

(def tc3-profiles (get tc3-inputs :profiles))

(def tc3-profile (first tc3-profiles))

(def tc3-stmt-seq (take 10 (sim/sim-seq tc3-inputs)))

(def tc3-dfas (per/compile-profile tc3-profile))

(deftest validate-stmt-vs-profile-test
  (testing "the validate-statement-vs-profile function using DATASIM"
    (is (loop [stmts tc3-stmt-seq]
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
            true)))))

(deftest match-next-statement-test
  (testing "the match-next-statement function using DATASIM"
    (is (loop [stmts      tc3-stmt-seq
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
            true)))))
