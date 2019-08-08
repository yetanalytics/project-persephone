(ns com.yetanalytics.persephone.utils.fsm
  (:require [clojure.set :as cset]
            [ubergraph.core :as uber]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Finite State Machine Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; A finite state machine is mathematically defined as a quintuple of the
;; following:
;; - Sigma: The input alphabet, a finite and non-empty set of symbols
;; - S: A finite, non-empty set of states
;; - s_0: The initial state, s.t. s_0 \in S
;; - delta: The state transition function, where delta : S x Sigma -> S
;; - F: The set of finite states, where F \subset S
;;
;; Each FSM is encoded as a map of the five mathematical objects, as such:
;; - :symbols is our alphabet. In practice it is actually a map between an ID
;; and a one-place predicate function, which evaluates the next symbol read.
;; - :start is our start state.
;; - :accept is our set of accept states.
;; - :graph is an Ubergraph representation of our FSM, which will effectively
;; serve as our transition table (since we can look up our transitions by
;; getting the outgoing edges of any node) and our state set (by calling 
;; uber/nodes).
;;
;; The creation of an FSM is based off of Thompson's Algorithm (Thompson, 1968)
;; in which every sub-FSM is treated as a "black box" with which to attach 
;; additional nodes and transitions to. This is NOT a very efficient algorithm,
;; but it is correct and leaves room for optimization.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constructing Finite State Machines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn states
  "Return all states in an FSM."
  [fsm]
  (-> fsm :graph uber/nodes set))

(defn new-node
  "Wrapper function to generate a random UUID (using Java's UUID class).
  Used to create new node names."
  [] (java.util.UUID/randomUUID))

;; Basic transition:
;;    A
;; x ---> a
(defn transition-fsm
  "Create a finite state machine that contains only a single transition edge."
  [fn-symbol fun]
  (let [start (new-node)
        accept (new-node)]
    {:symbols {fn-symbol fun} ;; Epsilon is not part of the alphabet
     :start start
     :accept #{accept}
     :graph (-> (uber/multidigraph)
                (uber/add-nodes start accept)
                (uber/add-edges [start accept {:symbol fn-symbol}]))}))

;; Concatenation helper
(defn concat-to-fsm
  "Helper function for sequence-fsm. Attaches a new FSM to the start of the
  main FSM."
  [next-fsm curr-fsm]
  (assoc curr-fsm
         :start (:start next-fsm)
         :symbols (merge (:symbols curr-fsm) (:symbols next-fsm))
         :graph (->
                 (:graph curr-fsm)
                 (uber/build-graph (:graph next-fsm))
                 (uber/add-edges*
                  (mapv (fn [as]
                          [as (:start curr-fsm) {:symbol :epsilon}])
                        (:accept next-fsm))))))
;; Concatenation
;     A      e      B
;; s ---> s ---> s ---> a
(defn sequence-fsm
  "Apply the sequence pattern, ie. ABC, to a sequence of FSMs; return a new
  state machine."
  [fsm-arr]
  (loop [curr-fsm (peek fsm-arr)
         queue (pop fsm-arr)]
    (if (empty? queue)
      curr-fsm
      (let [next-fsm (peek queue)]
        (recur (concat-to-fsm next-fsm curr-fsm) (pop queue))))))

;; Union helper
(defn union-with-fsm
  [next-fsm curr-fsm]
  (assoc curr-fsm
         :accept (cset/union (:accept curr-fsm) (:accept next-fsm))
         :symbols (merge (:symbols curr-fsm) (:symbols next-fsm))
         :graph (->
                 (:graph curr-fsm)
                 (uber/build-graph (:graph next-fsm))
                 (uber/add-edges
                  [(:start curr-fsm) (:start next-fsm) {:symbol :epsilon}]))))
;; Union:
;;    e      A
;; + ---> s ---> a
;; s
;; + ---> s ---> a
;;    e      B
(defn alternates-fsm
  "Apply the fsm-arr pattern, ie. A|B|C, to a set of FSMs; return a new
  state fsm."
  [fsm-arr]
  (let [start (new-node)]
    (loop [curr-fsm (assoc {}
                           :start start :accept #{} :symbols {}
                           :graph (-> (uber/multidigraph)
                                      (uber/add-nodes start)))
           queue fsm-arr]
      (if (empty? queue)
        curr-fsm
        (let [next-fsm (peek queue)]
          (recur (union-with-fsm next-fsm curr-fsm) (pop queue)))))))

;; Kleene Star:
;;           e
;;        +------+
;;    e   v  A   |
;; a ---> s ---> a
(defn zero-or-more-fsm
  "Apply the zeroOrMore pattern, ie. A*, to an FSM; return a new state 
  machine."
  [sub-fsm]
  (let [node (new-node)]
    (assoc sub-fsm
           :start node
           :accept (cset/union (:accept sub-fsm) #{node})
           :graph
           (->
            (:graph sub-fsm)
            (uber/add-edges [node (:start sub-fsm) {:symbol :epsilon}])
            (uber/add-edges* (mapv (fn [as]
                                     [as (:start sub-fsm) {:symbol :epsilon}])
                                   (:accept sub-fsm)))))))

;; Union with epsilon transition
;;    e      A
;; + ---> s ---> a
;; s
;; + ---> a
;;    e
(defn optional-fsm
  "Apply the sub-fsm pattern, ie. A?, to an FSM; return a new state machine."
  [sub-fsm]
  (let [start (new-node) accept (new-node)]
    (assoc sub-fsm
           :start start
           :accept (cset/union (:accept sub-fsm) #{accept})
           :graph
           (->
            (:graph sub-fsm)
            (uber/add-edges [start (:start sub-fsm) {:symbol :epsilon}])
            (uber/add-edges [start accept {:symbol :epsilon}])))))

;; Kleene Plus (ie. Kleen Star w/ concat):
;;           e
;;        + ---- +
;;    e   v  A   |
;; s ---> s ---> a
(defn one-or-more-fsm
  "Apply the oneOrMore pattern, ie. A+, to an FSM; return a new state machine."
  [sub-fsm]
  (let [start (new-node)]
    (assoc sub-fsm
           :start start
           :graph
           (->
            (:graph sub-fsm)
            (uber/add-edges [start (:start sub-fsm) {:symbol :epsilon}])
            (uber/add-edges* (mapv (fn [as]
                                     [as (:start sub-fsm) {:symbol :epsilon}])
                                   (:accept sub-fsm)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Using Finite State Machines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all-deltas
  "For a given FSM and an input state, return a table relating inputs and the
  set of result states. Equivalent to returning a row in a transition table
  (with transitions that output the empty set removed)."
  [fsm state]
  (let [graph (:graph fsm)
        out-edges (-> graph (uber/out-edges state) vec)]
    (reduce (fn [accum edge]
              (let [input (uber/attr graph edge :symbol)
                    dest (uber/dest edge)]
                (update accum input #(cset/union % #{dest})))) {} out-edges)))

(defn epsilon-closure
  "Return the epsilon closure of a state, ie. the set of states that can be
  reached by the state using only epsilon transitions (including itself)."
  [fsm states]
  ;; Perform BFS using a queue, while also using a set as a state accumulator.
  ;; Important to not consider states already in the set, or else we would get
  ;; stuck in infinite cycles.
  (loop [state-set states
         state-queue states]
    (let [deltas (apply merge (mapv (partial all-deltas fsm) state-queue))
          epsilons (cset/difference (:epsilon deltas) state-set)
          new-states (cset/union epsilons state-set)]
      (if (nil? epsilons)
        new-states
        (recur new-states epsilons)))))

(defn slurp-transition [fsm input state-set in-symbol]
  "Let an FSM read an incoming symbol against a single edge transition.
  Return the set of nodes that is the result of the transition function.
  If the empty set is returned, that means the input is not accepted against
  this transition."
  (if-let [pred-fn (-> fsm :symbols (get input))]
    (if (pred-fn in-symbol)
      state-set ;; If transition accepts, return new states; else kill path
      #{})
    ;; Epsilon transitions don't count (since we've already read them) 
    #{}))

(defn slurp-symbol
  "Given an FSM, a single state and an incoming symbol, find the set of states
  given by all accepting transitions (excluding epsilon transitions)."
  [fsm state in-symbol]
  (let [deltas (all-deltas fsm state)
        inputs (keys deltas) next-states (vals deltas)]
    (apply cset/union
           (mapv (fn [input state-set]
                   (slurp-transition fsm input state-set in-symbol))
                 inputs next-states))))

(defn read-next*
  "Let an FSM, given a current state, read a new symbol.
  Unlike the non-star version, this function returns the empty map on failure
  instead of resetting the state, making it unsutiable for composition (but
  useful for debug purposes)."
  [fsm curr-state in-symbol]
  (let [e-closure (epsilon-closure fsm curr-state)]
    (apply cset/union (mapv #(slurp-symbol fsm % in-symbol) e-closure))))

(defn read-next
  "Let an FSM, given a current state, read a new symbol.
  If the given state is nil or empty, start at the FSM's start state.
  Properties of curr-state map:
  :states-set - The current set of state IDs.
  :rejected-last - Whether the FSM has rejected the last input read.
  :accept-states - The set of accept states the FSM has reached, or nil if it
  has not reached any."
  [fsm in-symbol & [curr-state]]
  (let [{:keys [start accept]} fsm
        curr-state (if (empty? curr-state)
                     {:states-set #{start}
                      :rejected-last false
                      :accept-states (get accept start)}
                     curr-state)
        {:keys [states-set rejected-last accept-states]} curr-state
        new-states (read-next* fsm states-set in-symbol)]
    (if (empty? new-states)
      ;; FSM does not accept symbol; backtrack to previous state
      {:states-set states-set :rejected-last true :accept-states accept-states}
      ;; FSM accepts symbol; return new state
      {:states-set new-states :rejected-last false
       :accept-states (not-empty (cset/intersection accept new-states))})))

(defn fsm-function
  "Create a function out of an FSM that accepts a state and inputs, for our
  convenience (eg. in threading macros)."
  [fsm]
  (partial read-next fsm))
