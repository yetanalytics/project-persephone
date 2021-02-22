(ns com.yetanalytics.persephone.utils.fsm-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.set :as cset]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FSM Property Predicates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Thompson's Construction Predicates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NFAs built using Thompson's Construction have the following properties:
;; - The FSM has exactly one initial state, which is not accessible from any
;;   other state.
;; - The FSM A has exactly one final state, which is not co-accessible from any
;;   other state.
 
(defn- non-access-start?
  "Is the start state of the NFA not accessible from any other state?"
  [{:keys [start transitions] :as _nfa}]
  (every? (fn [[_ trans]]
            (every? (fn [[_ dest]] (not= start dest)) trans))
          transitions))

(defn- non-coaccess-accept?
  "Is there (a) only one accept state and (b) it is not co-accessible
   from any other state, i.e. it cannot access any other state?"
  [{:keys [accepts transitions] :as _nfa}]
  (and (= 1 (count accepts))
       (= {} (get transitions (first accepts)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FSM Specs and Generators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Common specs and generators

#_{:clj-kondo/ignore [:unresolved-var]} ;; Kondo doesn't recognize sgen macros
(defn- pred-gen []
  (sgen/fmap (fn [s] #(contains? s %))
             (sgen/set (sgen/one-of [(sgen/keyword nil)
                                     (sgen/string nil)])
                       {:max-elements 10})))

(s/def ::symbol-id (s/or :keyword keyword? :string string?))
(s/def ::symbol-pred (s/with-gen fn? pred-gen))
(s/def ::symbols (s/every-kv ::symbol-id
                             ::symbol-pred
                             :min-count 1))

(defn- override-fsm-fn
  "Use with fgen to replace the value for :start, :accept, and
   :transition to be conformant with the FSM specs."
  [symbols start accepts src-states dest-states trans-start select-dests-fn fsm]
  (-> fsm
      (assoc :start start)
      (assoc :accepts accepts)
      (assoc :transitions
             (reduce (fn [acc src]
                       (let [symbs (random-sample 0.33 (keys symbols))
                             trans (reduce
                                    (fn [acc sym]
                                      (assoc acc sym (select-dests-fn
                                                      dest-states)))
                                    {}
                                    symbs)]
                         (assoc acc src trans)))
                     trans-start
                     src-states))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA Specs and Generators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_{:clj-kondo/ignore [:unresolved-var]}
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

(defn- nfa-gen []
  (sgen/fmap (fn [{:keys [symbols states] :as nfa}]
               (override-fsm-fn (conj symbols [:epsilon nil])
                                (rand-nth (seq states))
                                (set (random-sample 0.33 states))
                                states
                                states
                                {}
                                (fn [s] (set (random-sample 0.25 s)))
                                nfa))
             (s/gen :nfa/nfa-basics)))

(defn valid-nfa-keys? [nfa] (s/valid? :nfa/nfa nfa))

(s/def ::nfa (s/with-gen (s/and valid-nfa-keys?
                                valid-start-state?
                                valid-accept-states?
                                valid-transition-src-states?
                                valid-transition-dest-states-nfa?
                                valid-transition-symbols-nfa?)
               nfa-gen))

(defn- tnfa-gen []
  (sgen/fmap (fn [{:keys [symbols states] :as nfa}]
               (let [start  (rand-nth (seq states))
                     accept (rand-nth (seq states))]
                 (override-fsm-fn (conj symbols [:epsilon nil])
                                  start
                                  #{accept}
                                  (disj states accept)
                                  (disj states start)
                                  {accept {}}
                                  (fn [s] (set (random-sample 0.25 s)))
                                  nfa)))
             (s/gen :nfa/nfa)))

(s/def ::thompsons-nfa (s/with-gen
                         (s/and ::nfa
                                non-access-start?
                                non-coaccess-accept?)
                         tnfa-gen))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DFA Specs and Generators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_{:clj-kondo/ignore [:unresolved-var]}
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

#_{:clj-kondo/ignore [:unresolved-var]}
(defn- dfa-gen []
  (sgen/fmap (fn [{:keys [symbols states] :as dfa}]
               (override-fsm-fn symbols
                                (rand-nth (seq states))
                                (set (random-sample 0.33 states))
                                states
                                states
                                {}
                                (fn [s] (rand-nth (seq s)))
                                dfa))
             (sgen/one-of [(s/gen :int-dfa/dfa-basics)
                           (s/gen :set-dfa/dfa-basics)])))

(defn valid-dfa-keys? [dfa]
  (s/valid? (s/or :int-dfa :int-dfa/dfa :set-dfa :set-dfa/dfa) dfa))

;; Mut put s/or at end due to tags added to conformed value
(s/def ::dfa (s/with-gen (s/and valid-dfa-keys?
                                valid-start-state?
                                valid-accept-states?
                                valid-transition-src-states?
                                valid-transition-dest-states-dfa?
                                valid-transition-symbols-dfa?)
               dfa-gen))

#_{:clj-kondo/ignore [:unresolved-var]}
(s/def ::accepting-dfa (s/with-gen (s/and ::dfa has-accept-states?)
                                   #(sgen/such-that has-accept-states?
                                                    (s/gen ::dfa))))

(sgen/generate (s/gen ::accepting-dfa))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Putting it all together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::fsm (s/or :nfa ::nfa :dfa ::dfa))

(s/def ::fsm-coll (s/every ::fsm :min-count 1))

