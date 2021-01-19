(ns com.yetanalytics.persephone.utils.fsm
  (:require [clojure.set :as cset]
            [clojure.string :as string]
            [dorothy.core :as dorothy]
            [dorothy.jvm :as djvm]))

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
;;  :accepts     state
;;  :transitions {state {symbol #{state ...} ...} ...}
;; }
;; where "symbol" is a string or keyword, "predicate" is a predicate function,
;; and "state" is a number.
;; 
;; We encode a DFA as the following:
;; {:symbols     {symbol predicate ...}
;;  :states      #{state ...}
;;  :start       state
;;  :accepts     #{state ...}
;;  :transitions {state {symbol state ...} ...}
;; }
;; 
;; The main differences are that a DFA can have multiple accept states and that
;; eachs symbol in the transition map corresponds to only one state. NFAs having
;; only one accept state is NOT a general property of NFAs, but it is an
;; invariant under Thompson's Algorithm.
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; State creation utilities

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

(defn- alphatize-states-nfa
  [nfa]
  (let [old-to-new-states
        (reduce (fn [acc state] (assoc acc state (new-state)))
                {}
                (sort (:states nfa))) ;; FIXME: sort is for debugging only
        update-dests
        (fn [transitions]
          (reduce-kv
           (fn [acc src trans]
             (assoc acc src (reduce-kv
                             (fn [acc symb dests]
                               (assoc acc
                                      symb
                                      (into
                                       #{}
                                       (mapv old-to-new-states dests))))
                             {}
                             trans)))
           {}
           transitions))]
    (-> nfa
        (update :states #(into #{} (mapv old-to-new-states %)))
        (update :start old-to-new-states)
        (update :accept old-to-new-states)
        (update :transitions update-dests)
        (update :transitions #(cset/rename-keys % old-to-new-states)))))

(defn alphatize-states
  "Rename all states in a collection of NFAs such that no two states share the
   same name."
  [nfa-coll]
  (reset-counter)
  (loop [new-fsm-queue []
         fsm-queue nfa-coll]
    (if-let [fsm (first fsm-queue)]
      (let [fsm' (alphatize-states-nfa fsm)]
        (recur (conj new-fsm-queue fsm') (rest fsm-queue)))
      new-fsm-queue)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA Construction Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;    i    
;; x ---> a
(defn transition-nfa
  "Create an NFA that accepts a single input."
  [fn-symbol f]
  (let [start (new-state) accept (new-state)]
    {:type    :nfa
     :symbols {fn-symbol f}
     :states  #{start accept}
     :start   start
     :accept  accept
     :transitions {start {fn-symbol #{accept}} accept {}}}))

;; -> q ==> s --> s ==> a
(defn concat-nfa
  "Concat a collection of NFAs in sequential order. The function throws an
   exception on empty collections."
  [nfa-coll]
  (if-not (empty? nfa-coll)
    (let [nfa-coll (alphatize-states nfa-coll)]
      (loop [fsm      (first nfa-coll)
             fsm-list (rest nfa-coll)]
        (if-let [next-fsm (first fsm-list)]
          (let [new-fsm
                {:type    :nfa
                 :symbols (merge (:symbols fsm) (:symbols next-fsm))
                 :states  (cset/union (:states fsm) (:states next-fsm))
                 :start   (:start fsm)
                 :accept  (:accept next-fsm)
                 :transitions
                 (-> (merge (:transitions fsm) (:transitions next-fsm))
                     (update-in
                      [(:accept fsm) :epsilon]
                      (fn [nexts] (set (conj nexts (:start next-fsm))))))}]
            (recur new-fsm (rest fsm-list)))
          fsm)))
    (throw (Exception. "Undefined for empty collection of FSMs"))))

;;    + --> s ==> s --v
;; -> q               f
;;    + --> s ==> s --^
(defn union-nfa
  "Construct a union of NFAs (corresponding to the \"|\" regex symbol.)"
  [nfa-coll]
  (let [nfa-coll    (alphatize-states nfa-coll)
        new-start   (new-state)
        new-accept  (new-state)
        old-starts  (set (mapv :start nfa-coll))
        old-accepts (mapv :accept nfa-coll)
        reduce-eps-trans
        (partial reduce
                 (fn [acc state]
                   (update-in acc
                              [state :epsilon]
                              (fn [d] (cset/union d #{new-accept})))))]
    {:type    :nfa
     :symbols (into {} (mapcat :symbols nfa-coll))
     :states  (cset/union
               (reduce
                (fn [acc fsm] (->> fsm :states (cset/union acc))) #{} nfa-coll)
               #{new-start new-accept})
     :start   new-start
     :accept  new-accept
     :transitions
     (->
      (reduce (fn [accum fsm] (->> fsm :transitions (merge accum))) {} nfa-coll)
      (reduce-eps-trans old-accepts)
      (update-in [new-start :epsilon] (constantly old-starts))
      (update new-accept (constantly {})))}))

;;          v-----+
;; -> q --> s ==> s --> f
;;    +-----------------^
(defn kleene-nfa
  "Apply the Kleene star operation on an NFA (the \"*\" regex symbol), which
   means the NFA can be taken zero or more times."
  [nfa]
  (let [new-start  (new-state)
        new-accept (new-state)
        old-start  (:start nfa)
        old-accept (:accept nfa)]
    {:type    :nfa
     :symbols (:symbols nfa)
     :states  (cset/union (:states nfa) #{new-start new-accept})
     :start   new-start
     :accept  new-accept
     :transitions
     (->
      (:transitions nfa)
      (update-in [new-start :epsilon] #(cset/union % #{old-start new-accept}))
      (update-in [old-accept :epsilon] #(cset/union % #{old-start new-accept}))
      (update new-accept (constantly {})))}))

;;    +-----------------v
;; -> q --> s ==> s --> f
(defn optional-nfa
  "Apply the optional operation on an NFA (the \"?\" regex symbol), which
   means the NFA may or may not be taken."
  [nfa]
  (let [new-start  (new-state)
        new-accept (new-state)
        old-start  (:start nfa)
        old-accept (:accept nfa)]
    {:type    :nfa
     :symbols (:symbols nfa)
     :states  (cset/union (:states nfa) #{new-start new-accept})
     :start   new-start
     :accept  new-accept
     :transitions
     (->
      (:transitions nfa)
      (update-in [new-start :epsilon] #(cset/union % #{old-start new-accept}))
      (update-in [old-accept :epsilon] #(cset/union % #{new-accept}))
      (update new-accept (constantly {})))}))

;;          v-----+
;; -> q --> s ==> s --> f
(defn plus-nfa
  "Apply the Kleene plus operation on an NFA (the \"+\" regex symbol), which
   means the NFA can be taken one or more times."
  [nfa]
  (let [new-start  (new-state)
        new-accept (new-state)
        old-start  (:start nfa)
        old-accept (:accept nfa)]
    {:type    :nfa
     :symbols (:symbols nfa)
     :states  (cset/union (:states nfa) #{new-start new-accept})
     :start   new-start
     :accept  new-accept
     :transitions
     (->
      (:transitions nfa)
      (update-in [new-start :epsilon] #(cset/union % #{old-start}))
      (update-in [old-accept :epsilon] #(cset/union % #{old-start new-accept}))
      (update new-accept (constantly {})))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA to DFA Conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- init-queue [init]
  (conj clojure.lang.PersistentQueue/EMPTY init))

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

(defn- nfa-states->dfa-state [nfa-states]
  (if (not-empty nfa-states)
    (->> nfa-states vec sort (map str) (string/join "-"))
    nil))

(defn- nfa-accept-states? [nfa nfa-states]
  (contains? nfa-states (:accept nfa)))

(defn nfa->dfa
  "Given an NFA with epsilon transitions, perform the powerset construction in
   order to (semi)-determinize it and remove epsilon transitions."
  [nfa]
  ;; Pseudocode from:
  ;;    http://www.cs.nuim.ie/~jpower/Courses/Previous/parsing/node9.html
  ;; 1. Create the start state of the DFA by taking the epsilon closure of the
  ;;    start state of the NFA.
  ;; 2. Perform the following for the new DFA state:
  ;;    For each possible input symbol:
  ;;     a) Apply move to the newly-created state and the input symbol; this
  ;;        will return a set of states.
  ;;     b) Apply the epsilon closure to this set of states, possibly resulting
  ;;        in a new set.
  ;;    This set of NFA states will be a single state in the DFA.
  ;; 4. Each time we generate a new DFA state, we must apply step 2 to it. The
  ;;    process is complete when applying step 2 does not yield any new states.
  ;; 5. The finish states of the DFA are those which contain any of the finish
  ;;    states of the NFA.
  (letfn [(add-state-to-dfa
            [dfa next-nfa-states next-dfa-state prev-dfa-state symb]
            (if (some? next-dfa-state)
              (-> dfa
                  (update :states conj next-dfa-state)
                  (update :accepts #(if (nfa-accept-states? nfa next-nfa-states)
                                      (conj % next-dfa-state)
                                      %))
                  (update-in
                   [:transitions prev-dfa-state] merge {symb next-dfa-state})
                  (update :transitions #(if-not (contains?
                                                 (:states dfa)
                                                 next-dfa-state)
                                          (assoc % next-dfa-state {})
                                          %)))
              dfa))
          (add-state-to-queue
            [queue dfa next-nfa-states next-dfa-state]
            (if (and (some? next-dfa-state)
                     (not (contains? (:states dfa) next-dfa-state)))
              (conj queue next-nfa-states)
              queue))]
    (let [nfa-start-eps-close (epsilon-closure nfa (:start nfa))
          dfa-start           (nfa-states->dfa-state nfa-start-eps-close)
          is-start-accepting  (nfa-accept-states? nfa nfa-start-eps-close)]
      (loop [dfa {:type        :dfa
                  :symbols     (:symbols nfa)
                  :states      #{dfa-start}
                  :start       dfa-start
                  :accepts     (if is-start-accepting #{dfa-start} #{})
                  :transitions {}}
             queue (init-queue nfa-start-eps-close)]
        (if-let [nfa-states (peek queue)]
          (let [queue'    (pop queue)
                dfa-state (nfa-states->dfa-state nfa-states)
                [queue'' dfa']
                (reduce
                 (fn [[queue dfa] symb]
                   (let [next-nfa-states (->>
                                          nfa-states
                                          (mapcat (partial nfa-move nfa symb))
                                          (mapcat (partial epsilon-closure nfa))
                                          set)
                         next-dfa-state  (nfa-states->dfa-state next-nfa-states)
                         new-dfa         (add-state-to-dfa dfa
                                                           next-nfa-states
                                                           next-dfa-state
                                                           dfa-state
                                                           symb)
                         new-queue       (add-state-to-queue queue
                                                             dfa
                                                             next-nfa-states
                                                             next-dfa-state)]
                     [new-queue new-dfa]))
                 [queue' dfa]
                 (-> nfa :symbols keys))]
            (recur dfa' queue''))
          dfa)))))

;; TODO: Handle situations when number of states returned is greater than 1.

(defn- read-next*
  "Like read-next, except it takes in the state directly, rather than state
   info. The read-next function is more useful for threading."
  [dfa state input]
  (let [{symbols :symbols accepts :accepts} dfa]
    (if-let [trans (-> dfa :transitions (get state))]
      (let [dests
            (->> trans
                 (filterv (fn [[symb _]]
                            (let [pred? (get symbols symb)] (pred? input))))
                 (mapv (fn [[_ dest]] dest)))]
        {:state     (first dests)
         :accepted? (boolean
                     (seq (filterv (partial contains? accepts) dests)))})
      (throw (Exception. "State not found in the finite state machine")))))

(defn read-next
  "Given a compiled FSM, the current state info, and an input, let the FSM read
   that input; this function returns update state info. State info has the
   following fields:
     :next-state - The next state arrived at in the FSM after reading the input.
     If the FSM cannot read the input, then next-state is nil.
     :accepted? - True if the FSM as arrived at an accept state after reading
     the input; false otherwise.
   If state-info is nil, the function starts at the start state.
   If the state value is nil, or if input is nil, return state-info without
   calling the FSM."
  [dfa state-info input]
  (let [state       (if (nil? state-info) (:start dfa) (:state state-info))
        is-accepted (contains? (:accepts dfa) state)]
    (if-not (or (nil? state) (nil? input))
      (read-next* dfa state input)
      {:state state :accepted? is-accepted})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FSM Graph Printing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: More testing, especially on DFAs

(defn fsm->graphviz
  "Convert a FSM to be readable by the Dorothy graphviz library."
  [fsm]
  (let
   [nodes (reduce
           (fn [accum state]
             (conj accum
                   [state
                    {:shape       :circle
                     :label       state
                     :peripheries (if (contains? (:accepts fsm) state) 2 1)}]))
           []
           (:states fsm))
    edges (reduce-kv
           (fn [accum src trans]
             (concat accum
                     (reduce-kv
                      (fn [accum symb dests]
                        (if (= (:type fsm) :nfa)
                          (concat accum
                                  (reduce
                                   (fn [accum dest]
                                     (conj accum [src dest {:label symb}]))
                                   []
                                   dests))
                          (conj accum [src dests {:label symb}])))
                      []
                      trans)))
           []
           (:transitions fsm))]
    (dorothy/digraph (concat edges nodes))))

(defn print-fsm
  "Show a FSM as an image."
  [fsm]
  (-> fsm fsm->graphviz dorothy/dot djvm/show!))