(ns com.yetanalytics.persephone.pattern.fsm-spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.set :as cset]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FSM Property Predicates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn count-states [fsm-coll]
  (reduce (fn [cnt {:keys [states]}] (+ cnt (count states)))
          0
          fsm-coll))

(defn has-accept-states?
  "Does the FSM have at least one accept state?"
  [{:keys [accepts] :as _fsm}]
  (< 0 (count accepts)))

(defn valid-start-state?
  "Is the start state in the state set?"
  [{:keys [states start] :as _fsm}]
  (contains? states start))

(defn valid-accept-states?
  "Are all the accept states in the state set?"
  [{:keys [states accepts] :as _fsm}]
  (cset/superset? states accepts))

(defn valid-transition-src-states?
  "Are all source states in the transition table in the state set?"
  [{:keys [states transitions] :as _fsm}]
  (let [trans-srcs (reduce-kv (fn [acc src _] (conj acc src))
                              #{}
                              transitions)]
    (= states trans-srcs)))

(defn- valid-transition-dest-states?
  [collect-dest-fn {:keys [states transitions] :as _fsm}]
  (letfn [(is-valid-trans-dests?
            [trans]
            (cset/superset?
             states
             (reduce-kv (fn [acc _ dests] (collect-dest-fn acc dests))
                        #{}
                        trans)))]
    (nil? (some (complement is-valid-trans-dests?) (vals transitions)))))

(defn valid-transition-dest-states-nfa?
  "Are all dest states in the transition table in the NFA's state set?"
  [nfa]
  (valid-transition-dest-states? (fn [acc dests] (cset/union acc dests)) nfa))

(defn valid-transition-dest-states-dfa?
  "Are all dest states in the transition table in the DFA's state set?"
  [dfa]
  (valid-transition-dest-states? (fn [acc dest] (conj acc dest)) dfa))

(defn- valid-transition-symbols?
  "Are all the symbols in the transition table in the alphabet?"
  [conj-epsilon? {:keys [symbols transitions] :as _fsm}]
  (let [symbols
        (if conj-epsilon?
          (conj symbols [:epsilon nil])
          symbols)
        trans-symbols
        (reduce-kv (fn [acc _ trans]
                     (cset/union acc
                                 (reduce-kv (fn [acc sym _] (conj acc sym))
                                            #{}
                                            trans)))
                   #{}
                   transitions)]
    (cset/superset? symbols trans-symbols)))

(defn valid-transition-symbols-nfa?
  [nfa]
  (valid-transition-symbols? true nfa))

(defn valid-transition-symbols-dfa?
  [dfa]
  (valid-transition-symbols? false dfa))

;; For NFAs produced via Thompson's Construction
;; There are several constraints such NFAs have to follow (which are listed on
;; the Wikipedia page), but this is the main structural spec.
(defn one-accept-state?
  [{:keys [accepts] :as _nfa}]
  (= 1 (count accepts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Common specs and generators

#_{:clj-kondo/ignore [:unresolved-var]}
(defn- pred-gen []
  (sgen/fmap (fn [x] (fn [y] (= x y)))
             (sgen/one-of [(sgen/int) (sgen/char) (sgen/boolean)])))

;; TODO: Add back keyword symbols?
;; (Remember that :epsilon was a keyword)
(s/def ::symbol-id string?)
(s/def ::symbol-pred (s/with-gen fn? pred-gen))
(s/def ::symbols (s/every-kv ::symbol-id
                             ::symbol-pred
                             :min-count 1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common generators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- sample-one [coll]
  (-> coll seq rand-nth))

(defn- sample-one-to-set [coll]
  #{(sample-one coll)})

(defn- sample-to-set [prob coll]
  (set (random-sample prob coll)))

(defn- fsm-overrider
  [accept-fn trans-fn {:keys [symbols states] :as fsm}]
  (-> fsm
      (assoc :start
             (sample-one states))
      (assoc :accepts
             (accept-fn states))
      (assoc :transitions
             (reduce
              (fn [acc src]
                (let [symbs (random-sample 0.33 (keys symbols))
                      trans (reduce
                             (fn [acc sym]
                               (assoc acc sym (trans-fn states)))
                             {}
                             symbs)]
                  (assoc acc src trans)))
              {}
              states))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA Specs and Generators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :nfa/type (s/with-gen #(= :nfa %) #(sgen/return :nfa)))
(s/def :nfa/state nat-int?)
(s/def :nfa/states (s/coll-of :nfa/state :kind set? :min-count 1 :gen-max 10))
(s/def :nfa/start :nfa/state)
(s/def :nfa/accepts (s/coll-of :nfa/state :kind set?))
(s/def :nfa/transitions (s/map-of :nfa/state
                                  (s/every-kv
                                   (s/with-gen
                                     (s/or :symbol ::symbol-id
                                           :epsilon #(= :epsilon %))
                                     #(s/gen ::symbol-id))
                                   :nfa/states)))

(s/def :nfa/nfa-basics (s/keys :req-un [::symbols
                                        :nfa/type
                                        :nfa/states]))

(s/def :nfa/nfa (s/keys :req-un [::symbols
                                 :nfa/type
                                 :nfa/states
                                 :nfa/start
                                 :nfa/accepts
                                 :nfa/transitions]))

(defn valid-nfa-keys? [nfa] (s/valid? :nfa/nfa nfa))

(def nfa-common-spec (s/and valid-nfa-keys?
                            valid-start-state?
                            valid-accept-states?
                            valid-transition-src-states?
                            valid-transition-dest-states-nfa?
                            valid-transition-symbols-nfa?))

(def nfa-spec
  (s/with-gen
    nfa-common-spec
    (fn [] (sgen/fmap (partial
                       fsm-overrider
                       (partial sample-to-set 0.33)
                       (partial sample-to-set 0.25))
                      (s/gen :nfa/nfa-basics)))))

(def thompsons-nfa-spec
  (s/with-gen
    (s/and nfa-common-spec
           one-accept-state?)
    (fn [] (sgen/fmap (partial
                       fsm-overrider
                       (partial sample-one-to-set)
                       (partial sample-to-set 0.25))
                      (s/gen :nfa/nfa-basics)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DFA Specs and Generators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :dfa/type (s/with-gen #(= :dfa %) #(sgen/return :dfa)))

(s/def :int-dfa/state nat-int?)
(s/def :int-dfa/states (s/coll-of :int-dfa/state
                                  :kind set?
                                  :min-count 1
                                  :gen-max 10))

(s/def :int-dfa/start :int-dfa/state)
(s/def :int-dfa/accepts (s/coll-of :int-dfa/state :kind set?))
(s/def :int-dfa/transitions (s/map-of :int-dfa/state
                                      (s/map-of ::symbol-id :int-dfa/state)))

(s/def :int-dfa/dfa-basics
  (s/keys :req-un [::symbols
                   :dfa/type
                   :int-dfa/states]))
(s/def :int-dfa/dfa
  (s/keys :req-un [::symbols
                   :dfa/type
                   :int-dfa/states
                   :int-dfa/start
                   :int-dfa/accepts
                   :int-dfa/transitions]))

(s/def :set-dfa/state (s/every nat-int?
                               :kind set?
                               :gen-max 5))
(s/def :set-dfa/states (s/coll-of :set-dfa/state
                                  :kind set?
                                  :min-count 1
                                  :gen-max 10))

(s/def :set-dfa/start :set-dfa/state)
(s/def :set-dfa/accepts (s/coll-of :set-dfa/state :kind set?))
(s/def :set-dfa/transitions (s/map-of :set-dfa/state
                                      (s/map-of ::symbol-id :set-dfa/state)))

(s/def :set-dfa/dfa-basics
  (s/keys :req-un [::symbols
                   :dfa/type
                   :set-dfa/states]))
(s/def :set-dfa/dfa
  (s/keys :req-un [::symbols
                   :dfa/type
                   :set-dfa/states
                   :set-dfa/start
                   :set-dfa/accepts
                   :set-dfa/transitions]))

(defn valid-dfa-keys? [dfa]
  (s/valid? :int-dfa/dfa dfa))

(defn valid-set-dfa-keys? [dfa]
  (s/valid? :set-dfa/dfa dfa))

(def dfa-common-spec (s/and valid-start-state?
                            valid-accept-states?
                            valid-transition-src-states?
                            valid-transition-dest-states-dfa?
                            valid-transition-symbols-dfa?))

(def dfa-gen-fmap
  (partial fsm-overrider
           (partial sample-to-set 0.33)
           (partial sample-one)))

(def dfa-spec
  (s/with-gen
    (s/and valid-dfa-keys?
           dfa-common-spec)
    (fn [] (sgen/fmap
            dfa-gen-fmap
            (s/gen :int-dfa/dfa-basics)))))

(def set-dfa-spec
  (s/with-gen
    (s/and valid-set-dfa-keys?
           dfa-common-spec)
    (fn [] (sgen/fmap
            dfa-gen-fmap
            (s/gen :set-dfa/dfa-basics)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State Info Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::accepted? boolean?)
(s/def ::visited (s/coll-of ::symbol-id))

(def state-info-spec
  (s/every (s/keys :req-un [:int-dfa/state
                            ::accepted?
                            ::visited])
           :kind set?))
