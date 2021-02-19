(ns com.yetanalytics.persephone.utils.fsm
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [clojure.set :as cset]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Finite State Machine Library
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
;; A finite state machine (FSM) is a construction consisting of "states" and
;; "transitions"; the current state changes when the FSM reads an input,
;; depending on the transitions available.
;; 
;; An FSM is mathematically defined as the following quintuple:
;; - Sigma: The input alphabet, a finite and non-empty set of symbols
;; - S:     A finite, non-empty set of states
;; - s_0:   The initial state, s.t. s_0 \in S
;; - delta: The state transition function, where delta : S x Sigma -> S
;; - F:     The set of finite states, where F \subset S
;; 
;; FSMs can also be depicted as graphs, which is why this library has graph
;; visualization algorithms (mostly for debugging).
;; 
;; FSMs can be divided into deterministic finite automata (DFAs) and non-
;; deterministic finite automata (NFAs). In a DFA, there can only be one
;; transition for each state and pair, whereas in an NFA there can be multiple.
;; Furthermore, NFAs may have "epsilon transitions" which can be taken without
;; reading any input.
;; 
;; The creation of an NFA is based off of Thompson's Algorithm (Thompson, 1968)
;; in which every sub-NFA is treated as a "black box" with which to attach 
;; additional nodes and transitions to. We then convert the NFA into a DFA
;; using the powerset construction, which can result exponential blowup in the
;; worst-case scenario, but in practice usually decreases the FSM size.
;; 
;; We encode an NFA as the following data structure:
;; {:symbols     {symbol predicate ...}
;;  :states      #{state ...}
;;  :start       state
;;  :accepts     #{state ...}
;;  :transitions {state {symbol #{state ...} ...} ...}
;; }
;; where "symbol" is some key, "predicate" is a predicate function, and "state"
;; is a number.
;; 
;; We encode a DFA as the following:
;; {:symbols     {symbol predicate ...}
;;  :states      #{state ...}
;;  :start       state
;;  :accepts     #{state ...}
;;  :transitions {state {symbol state ...} ...}
;; }
;; where each source-symbol pair in the transition can correspond to only one
;; destination state.
;; 
;; Note that since mutliple predicates may be valid for an input, we cannot
;; truly eliminate nondeterminism from our FSMs (unless we make each transition
;; a set of logical formulae, which would guarentee exponential blowup), so we
;; use the term "DFA" loosely in this context.
;; 
;; Resources:
;; - Thompson's Algorithm:
;;   https://en.wikipedia.org/wiki/Thompson%27s_construction
;; - Powerset Construction:
;;   https://en.wikipedia.org/wiki/Powerset_construction
;; - DFA minimization:
;;   https://en.wikipedia.org/wiki/DFA_minimization

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs + Spec Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Validation fns

(defn- alphatized-states? [states]
  (and (every? int? states)
       (= states (set (range 0 (count states))))))

(defn- alphatized-fsm? [{states :states}]
  (alphatized-states? states))

(defn- non-access-start?
  "Is the start state of the NFA not accessible from any other state?"
  [{:keys [start transitions] :as _nfa}]
  (every? (fn [[_ trans]]
            (every? (fn [[_ dest]] (not= start dest))
                    trans))
          transitions))

(defn- non-coaccess-accept?
  "Is there (a) only one accept state and (b) it is not co-accessible
   from any other state, i.e. it cannot access any other state?"
  [{:keys [accepts transitions] :as _nfa}]
  (and (= 1 (count accepts))
       (= {} (get transitions (first accepts)))))

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

;; Common specs and generators

(defn- pred-gen []
  (gen/fmap (fn [s] #(contains? s %))
            (gen/set gen/any-equatable {:max-elements 10})))

(s/def :fsm/symbol-id (s/or :keyword keyword? :string string?))
(s/def :fsm/symbol-pred (s/with-gen fn? pred-gen))
(s/def :fsm/symbols (s/every-kv :fsm/symbol-id
                                :fsm/symbol-pred
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

;; NFA specs and generators

(s/def :nfa/type (s/with-gen #(= :nfa %) (fn [] (gen/return :nfa))))
(s/def :nfa/state int?)
(s/def :nfa/states (s/coll-of :nfa/state :kind set? :min-count 1))
(s/def :nfa/start :nfa/state)
(s/def :nfa/accepts (s/coll-of :nfa/state :kind set?))
(s/def :nfa/transitions (s/map-of :nfa/state
                                  (s/every-kv
                                   (s/with-gen
                                     (s/or :symbol :fsm/symbol-id
                                           :epsilon #(= :epsilon %))
                                     (fn [] (s/gen :fsm/symbol-id)))
                                   :nfa/states)))
(s/def :nfa/nfa (s/keys :req-un [:fsm/symbols
                                 :nfa/type
                                 :nfa/states
                                 :nfa/start
                                 :nfa/accepts
                                 :nfa/transitions]))

(defn- nfa-gen []
  (gen/fmap (fn [{:keys [symbols states] :as nfa}]
              (override-fsm-fn (conj symbols [:epsilon nil])
                               (rand-nth (seq states))
                               (set (random-sample 0.33 states))
                               states
                               states
                               {}
                               (fn [s] (set (random-sample 0.25 s)))
                               nfa))
            (s/gen :nfa/nfa)))

(s/def ::nfa (s/with-gen (s/and :nfa/nfa
                                valid-start-state?
                                valid-accept-states?
                                valid-transition-src-states?
                                valid-transition-dest-states-nfa?
                                valid-transition-symbols-nfa?)
               nfa-gen))

(defn- tnfa-gen []
  (gen/fmap (fn [{:keys [symbols states] :as nfa}]
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
                         (s/and :nfa/nfa
                                valid-start-state?
                                valid-accept-states?
                                valid-transition-src-states?
                                valid-transition-dest-states-nfa?
                                valid-transition-symbols-nfa?
                                non-access-start?
                                non-coaccess-accept?)
                         tnfa-gen))

;; DFA specs and generators

(s/def :dfa/type (s/with-gen #(= :dfa %) (fn [] (gen/return :dfa))))

(s/def :int-dfa/state int?)
(s/def :int-dfa/states (s/coll-of :int-dfa/state :kind set? :min-count 1))
(s/def :int-dfa/start :int-dfa/state)
(s/def :int-dfa/accepts (s/coll-of :int-dfa/state :kind set?))
(s/def :int-dfa/transitions (s/map-of :int-dfa/state
                                      (s/map-of :fsm/symbol-id :int-dfa/state)))
(s/def :int-dfa/dfa (s/keys :req-un [:fsm/symbols
                                     :dfa/type
                                     :int-dfa/states
                                     :int-dfa/start
                                     :int-dfa/accepts
                                     :int-dfa/transitions]))

(s/def :set-dfa/state (s/every int? :kind set? :min-count 1))
(s/def :set-dfa/states (s/coll-of :set-dfa/state :kind set? :min-count 1))
(s/def :set-dfa/start :set-dfa/state)
(s/def :set-dfa/accepts (s/coll-of :set-dfa/state :kind set?))
(s/def :set-dfa/transitions (s/map-of :set-dfa/state
                                      (s/map-of :fsm/symbol-id :set-dfa/state)))
(s/def :set-dfa/dfa (s/keys :req-un [:fsm/symbols
                                     :dfa/type
                                     :set-dfa/states
                                     :set-dfa/start
                                     :set-dfa/accepts
                                     :set-dfa/transitions]))

(defn- dfa-gen []
  (gen/fmap (fn [{:keys [symbols states] :as dfa}]
             (override-fsm-fn symbols
                              (rand-nth (seq states))
                              (set (random-sample 0.33 states))
                              states
                              states
                              {}
                              (fn [s] (rand-nth (seq s)))
                              dfa))
            (gen/one-of [(s/gen :int-dfa/dfa) (s/gen :set-dfa/dfa)])))

;; Mut put s/or at end due to tags added to conformed value
(s/def ::dfa (s/with-gen (s/and valid-start-state?
                                valid-accept-states?
                                valid-transition-src-states?
                                valid-transition-dest-states-dfa?
                                valid-transition-symbols-dfa?
                                (s/or :ints :int-dfa/dfa
                                      :sets :set-dfa/dfa))
               dfa-gen))

(s/def ::fsm (s/or :nfa ::nfa :dfa ::dfa))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; State creation

(def counter (atom -1))

(defn reset-counter
  "Reset the counter used to name states; an optional starting value may be
   provided (mainly for debugging). The counter must always be reset before
   constructing a new NFA."
  ([] (swap! counter (constantly -1)))
  ([n] (swap! counter (constantly (- n 1)))))

(defn- new-state []
  (swap! counter (partial + 1)))

;; State alphatization

(defn- alphatize-states-fsm*
  [{type :type states :states :as fsm}]
  (let [states'
        (if (coll? (first states))
          (->> states (map sort) (sort-by first) (map set))
          (->> states sort))
        old-to-new-state
        (reduce (fn [acc state] (assoc acc state (new-state)))
                {}
                states')
        old-to-new-state-set
        (fn [states]
          (->> states (mapv old-to-new-state) (into #{})))
        update-dests
        (fn [transitions]
          (reduce-kv
           (fn [acc src trans]
             (assoc acc src (reduce-kv
                             (fn [acc symb dests]
                               (if (= type :nfa)
                                 (assoc acc symb (old-to-new-state-set dests))
                                 (assoc acc symb (old-to-new-state dests))))
                             {}
                             trans)))
           {}
           transitions))]
    (-> fsm
        (update :states old-to-new-state-set)
        (update :start old-to-new-state)
        (update :accepts old-to-new-state-set)
        (update :transitions #(cset/rename-keys % old-to-new-state))
        (update :transitions update-dests))))

(s/fdef alphatize-states-fsm
  :args (s/cat :fsm ::fsm)
  :ret (s/and alphatized-fsm? ::fsm)
  :fn (fn [{:keys [args ret]}]
        (= (count (:states args))
           (count (:states ret)))))

(defn alphatize-states-fsm
  "Rename all states in a single FSM."
  [fsm]
  (reset-counter)
  (alphatize-states-fsm* fsm))

(s/fdef alphatize-states
  :args (s/cat :fsm-coll (s/every ::fsm))
  :ret (s/and (s/every ::fsm)
              (fn [nfa-coll]
                (let [states (apply cset/union (map :states nfa-coll))]
                  (alphatized-states? states))))
        :fn (fn [{:keys [args ret]}]
              (= (reduce (fn [cnt {:keys [states]}] (+ cnt (count states)))
                         0
                         args)
                 (count (:states ret)))))

(defn alphatize-states
  "Rename all states in a collection of FSMs such that no two states share the
   same name."
  [fsm-coll]
  (reset-counter)
  (loop [new-fsm-queue []
         fsm-queue fsm-coll]
    (if-let [fsm (first fsm-queue)]
      (recur (->> fsm alphatize-states-fsm* (conj new-fsm-queue))
             (rest fsm-queue))
      new-fsm-queue)))

;; Epsilon transition appending

(defn- add-epsilon-transitions
  "Update the transition table to add new epsilon transitions from a collection
   of sources to a new destination."
  [transitions sources new-dest]
  (reduce (fn [trans src]
            (update-in trans
                       [src :epsilon]
                       (fn [dests] (cset/union dests #{new-dest}))))
          transitions
          sources))

(defn- assert-nonempty-fsm-coll
  [fsm-coll fn-name]
  (when (empty? fsm-coll)
    (throw (ex-info (str fn-name " is undefined for empty FSM collection.")
                    {:error :empty-fsm-coll
                     :fn    (keyword fn-name)}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA Construction Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;    i    
;; x ---> a

(s/fdef transition-nfa
  :args (s/cat :fn-symbol :fsm/symbol-id
               :f :fsm/symbol-pred)
  :ret (s/and ::thompsons-nfa))

(defn transition-nfa
  "Create an NFA that accepts a single input."
  [fn-symbol f]
  (let [start (new-state) accept (new-state)]
    {:type        :nfa
     :symbols     {fn-symbol f}
     :states      #{start accept}
     :start       start
     :accepts     #{accept}
     :transitions {start  {fn-symbol #{accept}} accept {}}}))

;; -> q ==> s --> s ==> a

(s/fdef concat-nfa
  :args (s/cat :nfa-coll (s/every ::thompsons-nfa :min-count 1))
  :ret (s/and ::thompsons-nfa alphatized-fsm?))

(defn concat-nfa
  "Concat a collection of NFAs in sequential order. The function throws an
   exception on empty collections."
  [nfa-coll]
  (assert-nonempty-fsm-coll nfa-coll "concat-nfa")
  (let [nfa-coll (alphatize-states nfa-coll)]
    (loop [fsm      (first nfa-coll)
           fsm-list (rest nfa-coll)]
      (if-let [next-fsm (first fsm-list)]
        (let [{symbols     :symbols
               states      :states
               start       :start
               accepts     :accepts
               transitions :transitions}
              fsm
              {next-symbols     :symbols
               next-states      :states
               next-start       :start
               next-accepts     :accepts
               next-transitions :transitions}
              next-fsm
              new-fsm
              {:type     :nfa
               :symbols  (merge symbols next-symbols)
               :states   (cset/union states next-states)
               :start    start
               :accepts  next-accepts
               :transitions
               (-> (merge transitions next-transitions)
                   (update-in
                    [(first accepts) :epsilon]
                    (fn [nexts] (set (conj nexts next-start)))))}]
          (recur new-fsm (rest fsm-list)))
        fsm))))

;;    + --> s ==> s --v
;; -> q               f
;;    + --> s ==> s --^

(s/fdef union-nfa
  :args (s/cat :nfa-coll (s/every ::thompsons-nfa :min-count 1))
  :ret (s/and ::thompsons-nfa alphatized-fsm?))

(defn union-nfa
  "Construct a union of NFAs (corresponding to the \"|\" regex symbol.)"
  [nfa-coll]
  (assert-nonempty-fsm-coll nfa-coll "union-nfa")
  (let [nfa-coll    (alphatize-states nfa-coll)
        new-start   (new-state)
        new-accept  (new-state)
        old-starts  (set (mapv :start nfa-coll))
        old-accepts (mapv #(-> % :accepts first) nfa-coll)]
    {:type     :nfa
     :symbols  (into {} (mapcat :symbols nfa-coll))
     :start    new-start
     :accepts  #{new-accept}
     :states
     (cset/union
      (reduce (fn [acc fsm] (->> fsm :states (cset/union acc))) #{} nfa-coll)
      #{new-start new-accept})
     :transitions
     (->
      (reduce (fn [acc fsm] (->> fsm :transitions (merge acc))) {} nfa-coll)
      (add-epsilon-transitions old-accepts new-accept)
      (update-in [new-start :epsilon] (constantly old-starts))
      (update new-accept (constantly {})))}))

;;          v-----+
;; -> q --> s ==> s --> f
;;    +-----------------^

(s/fdef kleene-nfa
  :args (s/cat :nfa ::thompsons-nfa)
  :ret ::thompsons-nfa)

(defn kleene-nfa
  "Apply the Kleene star operation on an NFA (the \"*\" regex symbol), which
   means the NFA can be taken zero or more times."
  [{:keys [symbols states start accepts transitions] :as _nfa}]
  (let [new-start  (new-state)
        new-accept (new-state)]
    {:type     :nfa
     :symbols  symbols
     :states   (cset/union states #{new-start new-accept})
     :start    new-start
     :accepts  #{new-accept}
     :transitions
     (->
      transitions
      (update-in [new-start :epsilon] #(cset/union % #{start new-accept}))
      (update-in [(first accepts) :epsilon] #(cset/union % #{start new-accept}))
      (update new-accept (constantly {})))}))

;;    +-----------------v
;; -> q --> s ==> s --> f

(s/fdef optional-nfa
  :args (s/cat :nfa ::thompsons-nfa)
  :ret ::thompsons-nfa)

(defn optional-nfa
  "Apply the optional operation on an NFA (the \"?\" regex symbol), which
   means the NFA may or may not be taken."
  [{:keys [symbols states start accepts transitions] :as _nfa}]
  (let [new-start  (new-state)
        new-accept (new-state)]
    {:type     :nfa
     :symbols  symbols
     :states   (cset/union states #{new-start new-accept})
     :start    new-start
     :accepts  #{new-accept}
     :transitions
     (->
      transitions
      (update-in [new-start :epsilon] #(cset/union % #{start new-accept}))
      (update-in [(first accepts) :epsilon] #(cset/union % #{new-accept}))
      (update new-accept (constantly {})))}))

;;          v-----+
;; -> q --> s ==> s --> f

(s/fdef plus-nfa
  :args (s/cat :nfa ::thompsons-nfa)
  :ret ::thompsons-nfa)

(defn plus-nfa
  "Apply the Kleene plus operation on an NFA (the \"+\" regex symbol), which
   means the NFA can be taken one or more times."
  [{:keys [symbols states start accepts transitions] :as _nfa}]
  (let [new-start  (new-state)
        new-accept (new-state)]
    {:type     :nfa
     :symbols  symbols
     :states   (cset/union states #{new-start new-accept})
     :start    new-start
     :accepts  #{new-accept}
     :transitions
     (->
      transitions
      (update-in [new-start :epsilon] #(cset/union % #{start}))
      (update-in [(first accepts) :epsilon] #(cset/union % #{start new-accept}))
      (update new-accept (constantly {})))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA to DFA Conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- init-queue [init]
  #?(:clj (conj clojure.lang.PersistentQueue/EMPTY init)
     :cljs (conj cljs.core/PersistentQueue.EMPTY init)))

(defn epsilon-closure
  "Given an NFA and a state, returns the epsilon closure for that state."
  [nfa init-state]
  ;; Perform a BFS and keep track of visited states
  (loop [visited-states #{}
         state-queue    (init-queue init-state)]
    (if-let [state (peek state-queue)]
      (if-not (contains? visited-states state)
        (let [visited-states' (conj visited-states state)
              next-states     (-> nfa :transitions (get state) :epsilon seq)
              state-queue'    (reduce
                               (fn [queue s] (conj queue s))
                               (pop state-queue)
                               next-states)]
          (recur visited-states' state-queue'))
        (recur visited-states (pop state-queue)))
      visited-states)))

(defn nfa-move
  "Given an NFA, a symbolic input (NOT an argument to predicates), and a state,
   return a vector of states arrived after the transition. Returns nil if no
   transitions are available."
  [nfa symb-input state]
  (if-let [trans (-> nfa :transitions (get state))]
    (->> trans
         (filterv (fn [[symb _]] (= symb-input symb)))
         (mapcat (fn [[_ dests]] dests)))
    nil))

(defn- nfa-accept-states? [nfa nfa-states]
  (not-empty (cset/intersection nfa-states (:accepts nfa))))

(defn- nfa->dfa*
  "Performs the following powerset construction algorithm from:
   http://www.cs.nuim.ie/~jpower/Courses/Previous/parsing/node9.html
   1. Create the start state of the DFA by taking the epsilon closure of the
      start state of the NFA.
   2. Perform the following for the new DFA state:
      For each possible input symbol:
      a) Apply move to the newly-created state and the input symbol; this
         will return a set of states.
      b) Apply the epsilon closure to this set of states, possibly resulting
         in a new set.
      This set of NFA states will be a single state in the DFA.
   3. Each time we generate a new DFA state, we must apply step 2 to it. The
      process is complete when applying step 2 does not yield any new states.
   4. The accept states of the DFA are those which contain any of the accept
      states of the NFA.
   Internally, this performs a tree search that guarentees no unreachable
   states."
  [nfa dfa-start]
  (letfn [(add-state-to-dfa
            [dfa next-dfa-state prev-dfa-state symb]
            ; An empty next-dfa-state value means that the symbol cannot be
            ; read at the previous state in the NFA, so we avoid adding it so
            ; it will also fail in the DFA.
            (if (not-empty next-dfa-state)
              (-> dfa
                  (update :states conj next-dfa-state)
                  (update :accepts
                          (fn [accepts]
                            (if (nfa-accept-states? nfa next-dfa-state)
                              (conj accepts next-dfa-state)
                              accepts)))
                  (update :transitions
                          (fn [transitions]
                            (if-not (contains? (:states dfa) next-dfa-state)
                              (assoc transitions next-dfa-state {})
                              transitions)))
                  (update-in
                   [:transitions prev-dfa-state] merge {symb next-dfa-state}))
              dfa))
          (add-state-to-queue
            [queue dfa next-dfa-state]
            ; Don't add to queue if next-dfa-state is empty (signifying failure)
            ; or if it's already in the DFA (i.e. it's already visited).
            (if (and (not-empty next-dfa-state)
                     (not (contains? (:states dfa) next-dfa-state)))
              (conj queue next-dfa-state)
              queue))]
    (loop [dfa {:type        :dfa
                :symbols     (:symbols nfa)
                :states      #{dfa-start}
                :start       dfa-start
                :accepts     (if (nfa-accept-states? nfa dfa-start)
                               #{dfa-start}
                               #{})
                :transitions {}}
           queue (init-queue dfa-start)]
      (if-let [dfa-state (peek queue)]
        (let [[queue'' dfa']
              (reduce
               (fn [[queue dfa] symb]
                 (let [next-dfa-state (->>
                                       dfa-state
                                       (mapcat (partial nfa-move nfa symb))
                                       (mapcat (partial epsilon-closure nfa))
                                       set)
                       new-dfa         (add-state-to-dfa
                                        dfa
                                        next-dfa-state
                                        dfa-state
                                        symb)
                       new-queue       (add-state-to-queue
                                        queue
                                        dfa
                                        next-dfa-state)]
                   [new-queue new-dfa]))
               [(pop queue) dfa]
               (-> nfa :symbols keys))]
          (recur dfa' queue''))
        ;; Add src states not present in the transition table
        (let [{:keys [states transitions]} dfa]
          (assoc dfa
                 :transitions
                 (reduce (fn [trans' s]
                           (if (not (contains? transitions s))
                             (assoc trans' s {})
                             trans'))
                         transitions
                         states)))))))

(s/fdef nfa->dfa
  :args (s/cat :nfa ::nfa)
  :ret ::dfa)

(defn nfa->dfa
  "Given an NFA with epsilon transitions, perform the powerset construction in
   order to (semi)-determinize it and remove epsilon transitions."
  [{start :start :as nfa}]
  (nfa->dfa* nfa (epsilon-closure nfa start)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DFA Minimization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- reverse-transitions
  "Create a new transition table that reverses the source and destination states
   for each symbol. Since multiple source-symbol pairs may lead to the same
   destination state in the original DFA, this produces a NFA, where the new
   source-symbol pair leads to multiple new destinations."
  [transitions]
  (letfn [(update-dests [src new-dests]
            (if (nil? new-dests) #{src} (conj new-dests src)))]
    (reduce-kv
     (fn [acc src trans]
       (merge-with (partial merge-with cset/union)
                   acc
                   (reduce-kv
                    (fn [acc symb dest]
                      (update-in acc [dest symb] (partial update-dests src)))
                    {}
                    trans)))
     {}
     transitions)))

(defn- reverse-dfa
  "Create a reverse NFA out of a DFA, where
   1. The original start state is an accept state
   2. The original accept states form a start state (so we represent the start
      state with the set of accept states, rather than a single state)
   3. The transitions are reversed."
  [{:keys [symbols states start accepts transitions] :as _dfa}]
  {:type        :nfa
   :symbols     symbols
   :states      states
   :start       accepts
   :accepts     #{start}
   :transitions (reverse-transitions transitions)})

;; For more information about Brzozowski's Algorithm, see:
;; https://cs.stackexchange.com/questions/1872/brzozowskis-algorithm-for-dfa-minimization
;; https://cs.stackexchange.com/questions/105574/proof-of-brzozowskis-algorithm-for-dfa-minimization

(s/fdef minimize-dfa
  :args (s/cat :dfa (s/with-gen ::dfa
                                (fn [] (gen/such-that
                                        #(< 0 (count (:accepts %)))
                                        (s/gen ::dfa)))))
  :ret ::dfa
  :fn (fn [{:keys [args ret]}]
        (<= (-> ret :states count)
            (-> args :states count))))

(defn minimize-dfa
  "Minimize the DFA using Brzozowski's Algorithm, which reverses and
   determinizes the DFA twice."
  [dfa]
  (assert (< 0 (count (:accepts dfa))))
  (letfn [(construct-reverse-dfa
            [dfa]
            (let [rev-dfa (-> dfa alphatize-states-fsm reverse-dfa)]
              (nfa->dfa* rev-dfa (:start rev-dfa))))]
    (-> dfa construct-reverse-dfa construct-reverse-dfa)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DFA Input Reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Handle situations when number of states returned is greater than 1.

(defn- read-next*
  "Like read-next, except it takes in the state directly, rather than
   state info. Returns a set of destination states.
   The read-next function is more useful for threading."
  [{:keys [symbols accepts transitions] :as _dfa} state input]
  (if-let [trans (-> transitions (get state))]
    (let [dests
          (->> trans
               (filterv (fn [[symb _]] (let [pred? (get symbols symb)]
                                         (pred? input))))
               (mapv (fn [[_ dest]] dest)))]
      {:states    (set dests)
       :accepted? (-> (filterv (partial contains? accepts) dests)
                      not-empty
                      boolean)})
    (let [err-msg "State not found in the finite state machine"]
      (throw #?(:clj (Exception. err-msg)
                :cljs (js/Error. err-msg))))))

(defn read-next
  "Given a compiled FSM, the current state info, and an input, let
   the FSM read that input; this function returns update state info.
   The state info has the following fields:
     :states      The set of next states arrived at in the FSM
                  after reading the input.
     :accepted?   True if the FSM as arrived at an accept state
                  after reading the input; false otherwise.
     :rejected?   True if the FSM was not able to read any more
                  states, false otherwise. If :states is
                  empty, then :rejected? is true.
   If the state info is nil, the function starts at the start state.
   If :states is empty or if input is nil, return state-info without
   calling the FSM, and set :rejected? to true."
  [{start :start :as dfa} {accepted? :accepted? :as state-info} input]
  (let [states      (if (nil? state-info) #{start} (:states state-info))]
    (if-not (or (empty? states) (nil? input))
      (reduce (fn [{:keys [states accepted? rejected?] :as acc}
                   {m-states :states m-accepted? :accepted?}]
                (-> acc
                    (assoc :states (cset/union states m-states))
                    (assoc :accepted? (or accepted? m-accepted?))
                    (assoc :rejected? (and rejected? (empty? m-states)))))
              {:states #{} :accepted? false :rejected? true}
              (map #(read-next* dfa % input) states))
      {:states states :accepted? accepted? :rejected? true})))
