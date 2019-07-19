(ns com.yetanalytics.utils.fsm
  (:require [clojure.set :as cset]
            [ubergraph.core :as uber]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Finite State Machine Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; A finite state machine is mathematically defined as a qunituple of the
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

(defn int-string
  [int-vec]
  (loop [curr-str ""
         int-queue int-vec]
    (if (empty? int-queue)
      curr-str
      (let [next-str (peek int-queue)]
        (print int-queue)
        (recur (str next-str " " curr-str) (pop int-queue))))))

(int-string [3 2 1])

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
        (recur (concat-to-fsm next-fsm curr-fsm) (pop queue)))))

  #_(let [next-fsm (peek fsm-arr)]
      (if (empty? (pop fsm-arr))
        next-fsm ;; Base case: sequence of 1 
        (let [curr-fsm (sequence-fsm (pop fsm-arr))]
          (assoc curr-fsm
                 :accept (:accept next-fsm)
                 :symbols (merge (:symbols curr-fsm) (:symbols next-fsm))
                 :graph
                 (->
                  (:graph curr-fsm)
                  (uber/build-graph (:graph next-fsm))
                  (uber/add-edges*
                   (mapv (fn [as]
                           [as (:start next-fsm) {:symbol :epsilon}])
                         (:accept curr-fsm)))))))))

(defn concat-to-fsm
  "Helper function for sequence-fsm. Attaches a new FSM to the start of the
  main FSM."
  [next-fsm curr-fsm]
  (assoc curr-fsm
         :start (:start next-fsm)
         :symbols (merge (:symbols curr-fsm) (:symbols next-fsm))
         :graph
         (->
          (:graph curr-fsm)
          (uber/build-graph (:graph next-fsm))
          (uber/add-edges*
           (mapv (fn [as]
                   [as (:start curr-fsm) {:symbol :epsilon}])
                 (:accept next-fsm))))))

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
          (recur (union-with-fsm next-fsm curr-fsm) (pop queue))))))

  #_(let [next-fsm (peek fsm-arr)]
      (if (empty? (pop fsm-arr)) ;; Base case: one FSM left
        (let [start (new-node)]
          (assoc next-fsm
                 :start start
                 :graph (-> (:graph next-fsm)
                            (uber/add-nodes start)
                            (uber/add-edges
                             [start (:start next-fsm) {:symbol :epsilon}]))))
        (let [curr-fsm (alternates-fsm (pop fsm-arr))]
          (assoc curr-fsm
                 :accept (cset/union (:accept curr-fsm) (:accept next-fsm))
                 :symbols (merge (:symbols curr-fsm) (:symbols next-fsm))
                 :graph
                 (->
                  (:graph curr-fsm)
                  (uber/build-graph (:graph next-fsm))
                  (uber/add-edges
                   [(:start curr-fsm) (:start next-fsm) {:symbol :epsilon}])))))))

(defn union-with-fsm
  [next-fsm curr-fsm]
  (assoc curr-fsm
         :accept (cset/union (:accept curr-fsm) (:accept next-fsm))
         :symbols (merge (:symbols curr-fsm) (:symbols next-fsm))
         :graph (-> (:graph curr-fsm)
                    (uber/build-graph (:graph next-fsm))
                    (uber/add-edges
                     [(:start curr-fsm) (:start next-fsm) {:symbol :epsilon}]))))

;; Kleene Star:
;;           e
;;        +------+
;;    e   v  A   |
;; + ---> s ---> a
;; s  e
;; + ---> a


(defn zero-or-more-fsm
  "Apply the zeroOrMore pattern, ie. A*, to an FSM; return a new state 
  machine."
  [sub-fsm]
  (let [start (new-node) accept (new-node)]
    (assoc sub-fsm
           :start start
           :accept (cset/union (:accept sub-fsm) #{accept})
           :graph
           (->
            (:graph sub-fsm)
            (uber/add-edges [start (:start sub-fsm) {:symbol :epsilon}])
            (uber/add-edges [start accept {:symbol :epsilon}])
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
                                   {:accept sub-fsm}))))))

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
    (reduce-kv (fn [prev index edge]
                 (let [input (uber/attr graph edge :symbol)
                       dest (uber/dest edge)
                       prev-dests (get input prev nil)]
                   (if (some? prev-dests)
                     (update prev input (conj prev-dests dest))
                     (assoc prev input #{dest}))))
               {} out-edges)))

(defn epsilon-closure
  "Return the epsilon closure of a state, ie. the set of states that can be
  reached by the state using only epsilon transitions (including itself)."
  [fsm state]
  (let [deltas (all-deltas fsm state)
        epsilons (:epsilon deltas)]
    ;; Implictly perform a breadth-first search, where state is the item
    ;; removed from the queue, and epsilons the states that are added.
    (if (nil? epsilons)
      #{state}
      (apply cset/union #{state}
             (mapv (partial epsilon-closure fsm) epsilons)))))

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
    #_(print deltas)
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
  (let [e-closure (apply cset/union
                         (mapv (partial epsilon-closure fsm) curr-state))
        new-state (apply cset/union
                         (mapv #(slurp-symbol fsm % in-symbol) e-closure))]
    new-state))

(defn read-next
  "Let an FSM, given a current state, read a new symbol."
  [fsm curr-state in-symbol]
  (let [curr-state (if (empty? curr-state) #{(:start fsm)} curr-state)
        new-state (read-next* fsm curr-state in-symbol)]
    (if (empty? new-state)
      ;; FSM does not accept symbol; backtrack to previous state
      (do (print "Reading failed!") curr-state)
      ;; FSM accepts symbol; return new state
      new-state)))

(defn fsm-function
  "Create a function out of an FSM that accepts a state and inputs, for our
  convenience (eg. in threading macros)."
  [fsm]
  (partial read-next fsm))
