(ns com.yetanalytics.persephone.utils.fsm
  (:require [clojure.set :as cset]))

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
;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; State creation

(def counter (atom -1))

(defn- reset-counter
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
  (let [old-to-new-state
        (reduce (fn [acc state] (assoc acc state (new-state)))
                {}
                states)
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

(defn alphatize-states-fsm
  "Rename all states in a single FSM."
  [fsm]
  (reset-counter)
  (alphatize-states-fsm* fsm))

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

;; Misc asserts and predicates

(defn- assert-nonempty-fsm-coll
  [fsm-coll fn-name]
  (when (empty? fsm-coll)
    (throw (ex-info (str fn-name " is undefined for empty FSM collection.")
                    {:type :empty-fsm-coll
                     :fn   (keyword fn-name)}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA Construction Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;    i    
;; x ---> a
(defn transition-nfa
  "Create an NFA that accepts a single input."
  [fn-symbol f]
  (reset-counter)
  (let [start (new-state) accept (new-state)]
    {:type        :nfa
     :symbols     {fn-symbol f}
     :states      #{start accept}
     :start       start
     :accepts     #{accept}
     :transitions {start  {fn-symbol #{accept}} accept {}}}))

;; -> q ==> s --> s ==> a
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
(defn kleene-nfa
  "Apply the Kleene star operation on an NFA (the \"*\" regex symbol), which
   means the NFA can be taken zero or more times."
  [{:keys [symbols states start accepts transitions] :as _nfa}]
  (reset-counter (+ 1 (apply max states))) ;; For gentests
  (let [new-start  (new-state)
        new-accept (new-state)
        old-accept (first accepts)]
    {:type     :nfa
     :symbols  symbols
     :states   (cset/union states #{new-start new-accept})
     :start    new-start
     :accepts  #{new-accept}
     :transitions
     (->
      transitions
      (update-in [new-start :epsilon] #(cset/union % #{start new-accept}))
      (update-in [old-accept :epsilon] #(cset/union % #{start new-accept}))
      (update new-accept (constantly {})))}))

;;    +-----------------v
;; -> q --> s ==> s --> f
(defn optional-nfa
  "Apply the optional operation on an NFA (the \"?\" regex symbol), which
   means the NFA may or may not be taken."
  [{:keys [symbols states start accepts transitions] :as _nfa}]
  (reset-counter (+ 1 (apply max states))) ;; For gentests
  (let [new-start  (new-state)
        new-accept (new-state)
        old-accept (first accepts)]
    {:type     :nfa
     :symbols  symbols
     :states   (cset/union states #{new-start new-accept})
     :start    new-start
     :accepts  #{new-accept}
     :transitions
     (->
      transitions
      (update-in [new-start :epsilon] #(cset/union % #{start new-accept}))
      (update-in [old-accept :epsilon] #(cset/union % #{new-accept}))
      (update new-accept (constantly {})))}))

;;          v-----+
;; -> q --> s ==> s --> f
(defn plus-nfa
  "Apply the Kleene plus operation on an NFA (the \"+\" regex symbol), which
   means the NFA can be taken one or more times."
  [{:keys [symbols states start accepts transitions] :as _nfa}]
  (reset-counter (+ 1 (apply max states))) ;; For gentests
  (let [new-start  (new-state)
        new-accept (new-state)
        old-accept (first accepts)]
    {:type     :nfa
     :symbols  symbols
     :states   (cset/union states #{new-start new-accept})
     :start    new-start
     :accepts  #{new-accept}
     :transitions
     (->
      transitions
      (update-in [new-start :epsilon] #(cset/union % #{start}))
      (update-in [old-accept :epsilon] #(cset/union % #{start new-accept}))
      (update new-accept (constantly {})))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA to DFA Conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Optimize this part of the code

(defn- init-queue [init]
  #?(:clj (conj clojure.lang.PersistentQueue/EMPTY init)
     :cljs (conj cljs.core/PersistentQueue.EMPTY init)))

(defn epsilon-closure
  "Given an NFA and a state, returns the epsilon closure for that state."
  [{nfa-transitions :transitions :as _nfa} init-state]
  ;; Perform a BFS and keep track of visited states
  (loop [visited-states (transient #{})
         state-queue    (init-queue init-state)]
    (if-let [state (peek state-queue)]
      (if-not (contains? visited-states state)
        (let [visited-states' (conj! visited-states state)
              next-states     (-> nfa-transitions (get state) :epsilon)
              state-queue'    (reduce (fn [queue s] (conj queue s))
                                      (pop state-queue)
                                      next-states)]
          (recur visited-states' state-queue'))
        (recur visited-states (pop state-queue)))
      (persistent! visited-states))))

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
  [{symbols         :symbols
    nfa-accepts     :accepts
    nfa-transitions :transitions
    :as             nfa}
   dfa-start]
  (letfn [(epsilon-close
           [state]
           (epsilon-closure nfa state))
          (move-nfa-state
           [symb state]
           ;; NOTE: get is slightly faster than get-in (in clj at least).
           ;; This function is THE hotspot for nfa->dfa*, so it's important
           ;; to squeeze as much juice out of this one function.
           (-> nfa-transitions (get state) (get symb)))
          (nfa-accept-states?
           [states]
           (not-empty (cset/intersection states nfa-accepts)))
          (add-state-to-dfa
           [{dfa-states :states :as dfa} next-dfa-state prev-dfa-state symb]
            ; An empty next-dfa-state value means that the symbol cannot be
            ; read at the previous state in the NFA, so we avoid adding it so
            ; it will also fail in the DFA.
           (if (not-empty next-dfa-state)
             (-> dfa
                 (update :states conj next-dfa-state)
                 (update :accepts
                         (fn [accepts]
                           (if (nfa-accept-states? next-dfa-state)
                             (conj accepts next-dfa-state)
                             accepts)))
                 (update :transitions
                         (fn [transitions]
                           (if-not (contains? dfa-states next-dfa-state)
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
    (loop [dfa
           {:type        :dfa
            :symbols     symbols
            :states      #{dfa-start}
            :start       dfa-start
            :accepts     (if (nfa-accept-states? dfa-start) #{dfa-start} #{})
            :transitions {}}
           queue
           (init-queue dfa-start)]
      (if-let [dfa-state (peek queue)]
        (let [[queue'' dfa']
              (reduce
               (fn [[queue dfa] symb]
                 (let [next-dfa-state
                       (-> dfa-state
                           (->> (mapcat (partial move-nfa-state symb)) distinct)
                           (->> (mapcat epsilon-close) set))
                       new-dfa
                       (add-state-to-dfa dfa next-dfa-state dfa-state symb)
                       new-queue
                       (add-state-to-queue queue dfa next-dfa-state)]
                   [new-queue new-dfa]))
               [(pop queue) dfa]
               (keys symbols))]
          (recur dfa' queue''))
        (let [add-missing-srcs
              (fn [transitions states]
                (reduce (fn [trans' s]
                          (if (not (contains? transitions s))
                            (assoc trans' s {})
                            trans'))
                        transitions
                        states))
              {:keys [states]} dfa]
          (-> dfa
              ;; Add source states not present in the transition table
              (update :transitions add-missing-srcs states)
              ;; Alphatize DFA states
              alphatize-states-fsm))))))

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
  (letfn [(update-dests
            [src new-dests]
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

(defn minimize-dfa
  "Minimize the DFA using Brzozowski's Algorithm, which reverses and
   determinizes the DFA twice."
  [dfa]
  (letfn [(construct-reverse-dfa
            [dfa]
            (let [rev-dfa (reverse-dfa dfa)]
              (nfa->dfa* rev-dfa (:start rev-dfa))))]
    (-> dfa
        construct-reverse-dfa
        construct-reverse-dfa)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DFA Input Reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                  after reading the input. If :states is empty,
                  then the input sequence has been rejected.
     :accepted?   True if the FSM as arrived at an accept state
                  after reading the input; false otherwise.
   If the state info is nil, the function starts at the start state.
   If :states is empty, read-next will return state-info without
   calling the FSM, and sets :states to the empty set (as nil is
   always considered rejected)."
  [{start :start :as dfa} state-info input]
  (let [states (if (nil? state-info) #{start} (:states state-info))]
    (reduce (fn [{:keys [states accepted?] :as acc}
                 {m-states :states m-accepted? :accepted?}]
              (-> acc
                  (assoc :states (cset/union states m-states))
                  (assoc :accepted? (or accepted? m-accepted?))))
            {:states #{} :accepted? false}
            (map #(read-next* dfa % input) states))))
