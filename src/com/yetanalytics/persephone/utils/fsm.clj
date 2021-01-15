(ns com.yetanalytics.persephone.utils.fsm
  (:require [clojure.set :as cset]
            [clojure.string :as string]
            [dorothy.core :as dorothy]
            [dorothy.jvm :as djvm]))

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

;;
;; {:alphabet {:kw predicate ...}
;;  :states #{states ...}
;;  :start state
;;  :accepts #{states ...}
;;  :transitions {state {:kw #{states} (if nfa) / state (if dfa)}} ...}
;; }
;;

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

(def counter (atom 0))

(defn new-node-2 []
  (swap! counter (partial + 1)))

(defn fsm->graphviz
  [fsm]
  (let
   [nodes (reduce
           (fn [accum state]
             (conj accum
                   [state
                    {:shape       :circle
                     :peripheries (if (contains? (:accepts fsm) state) 2 1)
                     :label       state}]))
           []
           (:states fsm))
    edges (reduce-kv
           (fn [accum src trans]
             (concat accum
                     (reduce-kv
                      (fn [accum symb dests]
                        (if (coll? dests)
                          ; NFA: destinations are sets of states
                          (concat accum
                                  (reduce (fn [accum dest]
                                            (conj accum [src dest {:label symb}]))
                                          []
                                          dests))
                          ; DFA: destinations are single states
                          (conj accum [src dests {:label symb}])))
                      []
                      trans)))
           []
           (:transitions fsm))]
    (dorothy/digraph (concat edges nodes))))

(defn print-fsm [fsm]
  (-> fsm fsm->graphviz dorothy/dot djvm/show!))

;;    i    
;; x ---> a
(defn transition-fsm-2
  [fn-symbol f]
  (let [start (new-node-2) accept (new-node-2)]
    {:symbols {fn-symbol f}
     :states #{start accept}
     :start start
     :accepts #{accept}
     :transitions {start {fn-symbol #{accept}} accept {}}}))

;; -> q ==> s --> s ==> a
(defn concat-fsm-2 [fsm-coll]
  (if-not (empty? fsm-coll)
    (loop [fsm (peek fsm-coll)
           fsm-queue (pop fsm-coll)]
      (if (empty? fsm-queue)
        fsm
        (let [next-fsm (peek fsm-queue)
              remain-fsm (pop fsm-queue)
              new-fsm
              {:symbols (merge (:symbols fsm) (:symbols next-fsm))
               :states (cset/union (:states fsm) (:states next-fsm))
               :start (:start fsm)
               :accepts (:accepts next-fsm)
               :transitions
               (->
                (merge (:transitions fsm) (:transitions next-fsm))
                (update-in
                 [(-> fsm :accepts first) :epsilon]
                 (fn [nexts] (set (conj nexts (:start next-fsm))))))}]
          (recur new-fsm remain-fsm))))
    (throw (Exception. "Undefined for empty collection of FSMs"))))

;;    + --> s ==> s --v
;; -> q               f
;;    + --> s ==> s --^
(defn union-fsm-2 [fsm-coll]
  (let [new-start (new-node-2)
        new-accept (new-node-2)
        old-starts (set (mapv :start fsm-coll))
        old-accepts (mapv #(-> % :accepts first) fsm-coll)
        reduce-eps-trans
        (partial reduce
                 (fn [acc state]
                   (update-in acc
                              [state :epsilon]
                              (fn [d] (cset/union d #{new-accept})))))]
    {:symbols (into {} (mapcat :symbols fsm-coll))
     :states (cset/union
              (reduce
               (fn [acc fsm] (->> fsm :states (cset/union acc))) #{} fsm-coll)
              #{new-start new-accept})
     :start new-start
     :accepts #{new-accept}
     :transitions
     (->
      (reduce (fn [accum fsm] (->> fsm :transitions (merge accum))) {} fsm-coll)
      (reduce-eps-trans old-accepts)
      (update-in [new-start :epsilon] (fn [_] old-starts)))}))

;;          v-----+
;; -> q --> s ==> s --> f
;;    +-----------------^
(defn kleene-fsm-2 [fsm]
  (let [new-start (new-node-2)
        new-accept (new-node-2)
        old-start (:start fsm)
        old-accept (-> fsm :accepts first)]
    {:symbols (:symbols fsm)
     :states (cset/union (:states fsm) #{new-start new-accept})
     :start new-start
     :accepts #{new-accept}
     :transitions
     (->
      (:transitions fsm)
      (update-in
       [new-start :epsilon] #(cset/union % #{old-start new-accept}))
      (update-in
       [old-accept :epsilon] #(cset/union % #{old-start new-accept})))}))

;;    +-----------------v
;; -> q --> s ==> s --> f
(defn optional-fsm-2 [fsm]
  (let [new-start (new-node-2)
        new-accept (new-node-2)
        old-start (:start fsm)
        old-accept (-> fsm :accepts first)]
    {:symbols (:symbols fsm)
     :states (cset/union (:states fsm) #{new-start new-accept})
     :start new-start
     :accepts #{new-accept}
     :transitions
     (->
      (:transitions fsm)
      (update-in
       [new-start :epsilon] #(cset/union % #{old-start new-accept}))
      (update-in
       [old-accept :epsilon] #(cset/union % #{new-accept})))}))

;;          v-----+
;; -> q --> s ==> s --> f
(defn plus-fsm-2 [fsm]
  (let [new-start (new-node-2)
        new-accept (new-node-2)
        old-start (:start fsm)
        old-accept (-> fsm :accepts first)]
    {:symbols (:symbols fsm)
     :states (cset/union (:states fsm) #{new-start new-accept})
     :start new-start
     :accepts #{new-accept}
     :transitions
     (->
      (:transitions fsm)
      (update-in
       [new-start :epsilon] #(cset/union % #{old-start}))
      (update-in
       [old-accept :epsilon] #(cset/union % #{old-start new-accept})))}))

(defn init-queue [init] (conj clojure.lang.PersistentQueue/EMPTY init))

(defn epsilon-closure-2
  "Returns the epsilon closure (performs a BFS internally)"
  [fsm init-state]
  (loop [visited-states #{}
         state-queue (init-queue init-state)]
    (if-let [state (peek state-queue)]
      (if-not (contains? visited-states state)
        (let [visited-states'
              (conj visited-states state)
              next-states
              (-> fsm :transitions (get state) :epsilon seq)
              state-queue'
              (reduce
               (fn [queue s] (conj queue s))
               (pop state-queue)
               next-states)]
          (recur visited-states' state-queue'))
        visited-states)
      visited-states)))

(defn move
  [nfa symb-input state]
  (if-let [trans (-> nfa :transitions (get state))]
    (->> trans
         (filterv (fn [[symb _]] (= symb-input symb)))
         (mapcat (fn [[_ dests]] dests)))
    nil))

#_(defn move-all
    "Given an NFA and a state, return the set of states after all possible non-
  epsilon transitions."
    [nfa state]
    (reduce-kv
     (fn [accum symb dests] (if-not (= symb :epsilon) (conj accum dests) accum))
     []
     (-> nfa :transitions state)))

(defn nfa-states->dfa-state [nfa-states]
  (if (not-empty nfa-states)
    (->> nfa-states vec sort (map str) (string/join "-"))
    "FAIL"))

(defn construct-powerset
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
  (let [{symbols :symbols
         nfa-start :start
         nfa-accepts :accepts} nfa
        nfa-start-eps-close (epsilon-closure-2 nfa nfa-start)
        dfa-start (nfa-states->dfa-state nfa-start-eps-close)]
    (loop [dfa
           {:symbols symbols
            :states #{dfa-start}
            :start dfa-start
            :accepts #{}
            :transitions {}}
           queue (init-queue nfa-start-eps-close)]
      (if-let [nfa-states (peek queue)]
        (let [queue'
              (pop queue)
              dfa-state
              (nfa-states->dfa-state nfa-states)
              [queue'' dfa']
              (reduce
               (fn [[queue dfa] symb]
                 (let [next-nfa-states
                       (->>
                        nfa-states
                        (mapcat (partial move nfa symb))
                        (mapcat (partial epsilon-closure-2 nfa))
                        set)
                       next-dfa-state
                       (nfa-states->dfa-state next-nfa-states)
                       new-dfa
                       (->
                        dfa
                        (update :states conj next-dfa-state)
                        (update :accepts
                                (if (not-empty (cset/intersection
                                                nfa-accepts
                                                next-nfa-states))
                                  #(conj % next-dfa-state)
                                  (partial identity)))
                        (update-in
                         [:transitions dfa-state]
                         merge
                         {symb next-dfa-state}))
                       new-queue
                       (if (and (not (contains? (:states dfa) next-dfa-state))
                                (not-empty next-nfa-states))
                         (conj queue next-nfa-states)
                         queue)]
                   [new-queue new-dfa]))
               [queue' dfa]
               (keys symbols))]
          (recur dfa' queue''))
        (if (contains? (:states dfa) "FAIL")
          (update-in dfa
                     [:transitions "FAIL"]
                     (fn [trans]
                       (reduce
                        (fn [acc symb] (assoc acc symb "FAIL"))
                        trans
                        (keys symbols))))
          dfa)))))

(defn read-next-state-2*
  "Given an FSM, a queue of states, and an input, advance the current state
   from the topmost state in the queue."
  [fsm input state-queue state]
  (loop [states state-queue
         transitions (-> fsm :transitions state)]
    (if-let [[symbol next-state] (peek transitions)]
      (let [predicate (-> fsm :symbols symbol)]
        (if (and (some? predicate) (predicate input))
          (recur (conj states next-state) (pop transitions))
          (recur states (pop transitions))))
      transitions)))

#_((defn read-next-state-2
     [fsm input state-queue]
     (if-let [state (peek state-queue)]
       (let [state-queue' (read-next-state-2* fsm input state-queue state)
             state-queue'' (conj (epsilon-closure-2 fsm state))])
       (->> state (fsm input (pop state-queue)) (conj (epsilon-closure fsm)))
       state-queue)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn slurp-transition
  "Let an FSM read an incoming symbol against a single edge transition.
  Return the set of nodes that is the result of the transition function.
  If the empty set is returned, that means the input is not accepted against
  this transition."
  [fsm input state-set in-symbol]
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
