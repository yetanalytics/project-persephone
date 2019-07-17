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
;; - :states is our set of state nodes, which are Ubergraph nodes identified
;; using UUIDs.
;; - :start is our start state.
;; - :accept is our single accept state. While theoretically each FSM has a set
;; of accept states, in our construction we only allow one per FSM.
;; - :graph is an Ubergraph representation of our FSM, which will effectively
;; serve as our transition table (since we can look up our transitions by
;; getting the outgoing edges of any node).
;;
;; The creation of an FSM is based off of Thompson's Algorithm (Thompson, 1968)
;; in which every sub-FSM is treated as a "black box" with which to attach 
;; additional nodes and transitions to. This is NOT a very efficient algorithm,
;; but it is correct and leaves room for optimization.

(defn new-node
  "Wrapper function to generate a random UUID (using Java's UUID class).
  Used to create new node names."
  [] (java.util.UUID/randomUUID))

;; Basic transition:
;;    A
;; x ---> a
(defn transition-fsm
  "Create a finite state machine that contains only a single Statement Template
  transition."
  [fn-symbol fun]
  (let [start (new-node)
        accept (new-node)]
    {:symbols {fn-symbol fun
               :epsilon (constantly true)}
     :start start
     :accept accept
     :states #{start accept}
     :graph (-> (uber/graph)
                (uber/add-nodes start)
                (uber/add-nodes accept)
                (uber/add-edges [start accept {:symbol fn-symbol}]))}))

;; Concatenation
;     A      e      B
;; y ---> z ---> w ---> a
(defn sequence-fsm
  "Apply the sequence pattern, ie. ABC, to a sequence of FSMs; return a new
  state machine."
  [seqn]
  (let [next-machine (peek seqn)]
    (if (empty? (pop seqn))
      next-machine ;; Base case: sequence of 1
      (let [current-machine (sequence-fsm (pop seqn))]
        (assoc current-machine
               :accept (:accept next-machine)
               :states (cset/union (:states current-machine)
                                   (:states next-machine))
               :symbols (merge (:symbols current-machine)
                               (:symbols next-machine))
               :graph (-> (:graph current-machine)
                          (uber/build-graph (:graph next-machine))
                          (uber/add-edges [(:accept current-machine)
                                           (:start next-machine)
                                           {:symbol :epsilon}])))))))

;; Union:
;;    e        A        e
;; +-----> y -----> z -----v
;; x                       a
;; +-----> y -----> z -----^
;;    e        B        e 
(defn alternates-fsm
  "Apply the alternates pattern, ie. A|B|C, to a set of FSMs; return a new
  state machine."
  [alternates]
  (let [next-machine (peek alternates)]
    (if (empty? (pop alternates)) ;; Base case: one FSM left
      (let [start (new-node)
            accept (new-node)]
        (assoc next-machine
               :start start
               :accept accept
               :states (cset/union (:states next-machine) #{start accept})
               :graph (-> (:graph current-machine)
                          (uber/add-nodes start accept)
                          (uber/add-edges [start (:start next-machine)
                                           {:symbol :epsilon}])
                          (uber/add-edges [(:accept next-machine) accept
                                           {:symbol :epsilon}]))))
      (let [current-machine (alternates-fsm (pop alternates))]
        (assoc current-machine
               :states (cset/union (:states current-machine)
                                   (:states next-machine))
               :symbols (merge {:symbols current-machine}
                               {:symbols next-machine})
               :graph (-> (:graph current-machine)
                          (uber/build-graph (:graph next-machine))
                          (uber/add-edges [(:start current-machine)
                                           (:start next-machine)
                                           {:symbol :epsilon}])
                          (uber/add-edges [(:accept next-machine)
                                           (:accept current-machine)
                                           {:symbol :epsilon}])))))))

;; Kleene Star:
;;           e
;; +--------------------+
;; |  e      A      e   v
;; x ---> y ---> z ---> a
;;        ^  e   +
;;        +------+ 
(defn zero-or-more-fsm
  "Apply the zeroOrMore pattern, ie. A*, to an FSM; return a new state 
  machine."
  [zero-or-more]
  (let [start (new-node)
        accept (new-node)]
    (assoc zero-or-more
           :start start
           :accept accept
           :states (cset/union (:states zero-or-more) #{start accept})
           :graph (-> (:graph zero-or-more)
                      (uber/add-edges [start (:start zero-or-more)
                                       {:symbol :epsilon}])
                      (uber/add-edges [(:accept zero-or-more) accept]
                                      {:symbol :epsilon})
                      (uber/add-edges [start accept {:symbol :epsilon}])
                      (uber/add-edges [(:accept zero-or-more)
                                       (:start zero-or-more)
                                       {:symbol :epsilon}])))))

;; Union with epsilon transition
;;    e        A        e
;; +-----> y -----> z -----v
;; x                       a
;; +-----------------------k^
;;             e 
(defn optional-fsm
  "Apply the optional pattern, ie. A?, to an FSM; return a new state machine."
  [optional]
  (let [start (new-node)
        accept (new-node)]
    (assoc optional
           :start start
           :accept accept
           :states (cset/union (:states optional) #{start accept})
           :graph (-> (:graph optional)
                      (uber/add-edges [start (:start optional)
                                       {:symbol :epsilon}])
                      (uber/add-edges [(:accept optional) accept
                                       {:symbol :epsilon}])
                      (uber/add-edges [start accept {:symbol :epsilon}])))))

;; Kleene Plus (ie. Kleen Star w/ concat):
;     e      A      e
;; x ---> y ---> z ---> a
;;        ^  e   +
;;        +------+ 
(defn one-or-more-fsm
  "Apply the oneOrMore pattern, ie. A+, to an FSM; return a new state machine."
  [one-or-more]
  (let [start (new-node)
        accept (new-node)])
  (assoc one-or-more
         :start start
         :accept accept
         :states (cset/union (:states one-or-more) #{start accept})
         :graph (-> (:graph one-or-more)
                    (uber/add-edges [start (:start one-or-more)
                                     {:symbol :epsilon}])
                    (uber/add-edges [(:accept one-or-more) accept
                                     {:symbol :epsilon}])
                    (uber/add-edges [(:accept one-or-more)
                                     (:start one-or-more)
                                     {:symbol :epsilon}]))))

;; TODO Add optimization function

(defn read-next
  "Given a FSM and its current state, read the next symbol given.
  Return the updated state(s) if reading the symbol was successful, nil if
  reading was unsuccessful."
  [fsm curr-state next-symb]
  nil) ;; FIXME
