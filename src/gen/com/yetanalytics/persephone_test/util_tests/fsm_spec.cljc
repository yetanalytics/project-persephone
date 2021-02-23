(ns com.yetanalytics.persephone-test.util-tests.fsm-spec
  (:require #?@(:cljs [[clojure.test.check]
                       [clojure.test.check.generators]
                       [clojure.test.check.properties :include-macros true]])
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.set :as cset]
            [com.yetanalytics.persephone.utils.fsm :as fsm]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FSM Property Predicates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- count-states [fsm-coll]
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
  (sgen/fmap (fn [s] #(contains? s %))
             (sgen/set (sgen/one-of [(sgen/keyword nil)
                                     (sgen/string nil)])
                       {:max-elements 10})))

(s/def ::symbol-id (s/or :keyword keyword? :string string?))
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

(def nfa-spec (s/and valid-nfa-keys?
                     valid-start-state?
                     valid-accept-states?
                     valid-transition-src-states?
                     valid-transition-dest-states-nfa?
                     valid-transition-symbols-nfa?))

(s/def ::nfa
  (s/with-gen
    nfa-spec
    (fn [] (sgen/fmap (partial
                       fsm-overrider
                       (partial sample-to-set 0.33)
                       (partial sample-to-set 0.25))
                      (s/gen :nfa/nfa-basics)))))

(s/def ::thompsons-nfa
  (s/with-gen
    (s/and nfa-spec
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

(def dfa-spec (s/and valid-start-state?
                     valid-accept-states?
                     valid-transition-src-states?
                     valid-transition-dest-states-dfa?
                     valid-transition-symbols-dfa?))

(def dfa-gen-fmap
  (partial fsm-overrider
           (partial sample-to-set 0.33)
           (partial sample-one)))

(s/def ::dfa (s/with-gen
               (s/and valid-dfa-keys?
                      dfa-spec)
               (fn [] (sgen/fmap
                       dfa-gen-fmap
                       (s/gen :int-dfa/dfa-basics)))))

(s/def ::set-dfa (s/with-gen
                   (s/and valid-set-dfa-keys?
                          dfa-spec)
                   (fn [] (sgen/fmap
                           dfa-gen-fmap
                           (s/gen :set-dfa/dfa-basics)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Function specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef fsm/alphatize-states-fsm
  :args (s/cat :fsm (s/or :nfa ::nfa
                          :dfa (s/or :ints ::dfa
                                     :sets ::set-dfa)))
  :ret (s/or :nfa ::nfa :dfa ::dfa)
  :fn (fn [{:keys [args ret]}]
        (= (count (:states args))
           (count (:states ret)))))

(s/fdef fsm/alphatize-states
  :args (s/cat :fsm-coll (s/every (s/or :nfa ::nfa
                                        :dfa (s/or :ints ::dfa
                                                   :sets ::set-dfa))
                                  :min-count 1
                                  :gen-max 10))
  :ret (s/every (s/or :nfa ::nfa :dfa ::dfa)
                :min-count 1)
  :fn (fn [{:keys [args ret]}]
        (= (count-states args)
           (count (:states ret)))))

(s/fdef fsm/transition-nfa
  :args (s/cat :fn-symbol ::symbol-id :fn ::symbol-pred)
  :ret (and ::thompsons-nfa
            (fn [{:keys [states]}] (= 2 (count states)))))

(s/fdef fsm/concat-nfa
  :args (s/cat :nfa-coll (s/every ::thompsons-nfa :min-count 1 :gen-max 5))
  :ret ::thompsons-nfa
  :fn (fn [{:keys [args ret]}]
        (= (count-states (:nfa-coll args)) (count (:states ret)))))

(s/fdef fsm/union-nfa
  :args (s/cat :nfa-coll (s/every ::thompsons-nfa :min-count 1 :gen-max 5))
  :ret ::thompsons-nfa
  :fn (fn [{:keys [args ret]}]
        (= (+ 2 (count-states (:nfa-coll args))) (count (:states ret)))))

(s/fdef fsm/kleene-nfa
  :args (s/cat :nfa ::thompsons-nfa)
  :ret ::thompsons-nfa)

(s/fdef fsm/optional-nfa
  :args (s/cat :nfa ::thompsons-nfa)
  :ret ::thompsons-nfa)

(s/fdef fsm/plus-nfa
  :args (s/cat :nfa ::thompsons-nfa)
  :ret ::thompsons-nfa)

(s/fdef fsm/nfa->dfa
  :args (s/cat :nfa ::nfa)
  :ret ::dfa)

(s/fdef fsm/minimize-dfa
  :args (s/cat :dfa ::dfa)
  :ret ::dfa
  :fn (fn [{:keys [args ret]}]
        (<= (-> ret :states count)
            (-> args :dfa :states count))))
