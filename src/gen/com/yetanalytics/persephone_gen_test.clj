(ns com.yetanalytics.persephone-gen-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.datasim.sim :as sim]
            [com.yetanalytics.datasim.input :as sim-input]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATASIM tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Use macros to allow use w/ cljs?

(def tc3-inputs
  (-> (sim-input/from-location :input :json "test-resources/tc3_inputs.json")
      (update-in [:parameters :seed] (fn [_] (rand-int 1000)))))

(def tc3-profile (get-in tc3-inputs [:profiles 0]))

(def tc3-stmt-seq (take 100 (sim/sim-seq tc3-inputs)))

(def tc3-dfas (per/compile-profile tc3-profile))

(deftest match-next-statement-datasim-test
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
                (throw (ex-info "Statement stream rejected by tc3 Profile"
                                {:type       :datasim-test-failed
                                 :statement  next-stmt
                                 :patterns   tc3-dfas
                                 :state-info state-info'}))
                (recur (rest stmts) state-info')))
            true)))))
