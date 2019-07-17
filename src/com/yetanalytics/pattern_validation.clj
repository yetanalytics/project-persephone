(ns com.yetanalytics.pattern-validation
  (:require [clojure.set :as cset]
            [ubergraph.core :as uber]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Finite State Machine Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; A finite state machine is mathematically defined as a qunituple of the
;; following:
;; - Sigma: The input alphabet, a finite and non-empty set of symbols
;; - S: A finite, non-empty set of states
;; - s_0: The initial state, s.t. s_0 \in S
;; - delta: The state transition function, where delta : S x Sigma -> S
;; - F: The set of finite states, where F \subset S
;; In our FSM, our Sigma is implictly the set of Template predicates and sub-
;; Patterns that define our transitions (so we don't need to explictly define
;; it). Our S and delta are represented by a multigraph where each edge is 
;; labeled by a Template predicate or a sub-Pattern. (Technically we can use
;; a transition table without a graph, but using a graph makes for very easy
;; visualization.)
(defn init-fsm
  "Initialize a finite state machine with 0 nodes and 0 transitions."
  [id index]
  (let [start (statify index)]
    {;; Our primary Pattern id
     :id id
     ;; Our symbol table is actually a heterogeneous map between:
     ;; 1. Pattern IDs and their respective FSMs
     ;; 2. Template IDs and their respective predicate functions
     :symbols {}
     ;; Our transition table is represented by an ubergraph, where each state is
     ;; a node and each edge is marked by a Template ID symbol.
     ;; We don't actually need a graph to define the table, but it's useful for 
     ;; visualization purposes.
     :graph (uber/graph [start {:pattern id}])
     ;; Our start state is always s0
     :start start
     ;; There will always be only one accept state.
     :accept nil}))

(defn new-node
  "Wrapper function to generate a random UUID (using Java's UUID class).
  Used to create new node names."
  [] (java.util.UUID/randomUUID))

(defn transition-fsm
  "Create a finite state machine that contains only a single Statement Template
  transition."
  [fn-symbol fun]
  (let [start (new-node)
        accept (new-node)]
    {:symbols {fn-symbol fun}
     :start start
     :accept accept
     :states #{start accept}
     :graph (-> (uber/graph)
                (uber/add-nodes start)
                (uber/add-nodes accept)
                (uber/add-edges [start accept {:symbol fn-symbol}]))}))

(defn sequence-fsm
  "Recursively create a finite state machine out of a sequence of smaller
  FSMs. On each recursion, add an epsilon transition from the end of the
  previous machine to the start of the next machine."
  [seqn]
  (let [next-machine (peek seqn)]
    (if (empty? (pop seqn)) ;; Base case: sequence of 1
      next-machine
      (let [{:keys [start accept symbols states graph]} next-machine
            current-machine (sequence-fsm (pop seqn))
            old-start-edges (uber/out-edges graph start)
            new-start-edges
            (mapv #(update % 0 (fn [_] (:accept current-machine)))
                  old-start-edges)]
        {:start (:start current-machine)
         :accept accept
         :states (cset/union (:states current-machine)
                             (cset/difference states #{start}))
         :symbols (merge (:symbols current-machine) symbols)
         :graph (uber/build-graph
                 (:graph current-machine)
                 (-> graph
                     (uber/remove-edges* old-in-edges)
                     (uber/remove-nodes start)
                     (uber/add-edges* new-in-edges)))}))))

(defn alternates-fsm
  "Create a finite state machine out of alternating choices of smaller state
  machines."
  [alternates]
  (let [next-machine (peek alternates)]
    (if (empty? (pop alternates)) ;; Base case: one FSM left
      next-machine
      (let [{:keys [start accept symbols states graph]} next-machine
            current-machine (alternates-fsm (pop seqn))
            old-start-edges (uber/out-edges graph next-start)
            old-accept-edges (uber/in-edges graph next-accept)
            new-edges
            (mapv (fn [edge]
                    (-> edge
                        (update 0 (fn [src] (if (= start)
                                              (:start current-machine)
                                              src)))
                        (update 1 (fn [dest] (if (= accept)
                                               (:accept current-machine)
                                               dest)))))
                  (uber/edges (:graph next-machine)))]
        (assoc current-machine
               :states (cset/union (:states current-machine)
                                   (cset/difference next #{start accept}))
               :symbols (merge (:symbols current-machine) symbols)
               :graph (uber/build-graph
                       (:graph current-machine)
                       (-> graph
                           (uber/remove-edges* old-start-edges)
                           (uber/remove-edges* old-accept-edges)
                           (uber/remove-nodes start accept)
                           (uber/add-edges new-edges))))))))

(defn optional-fsm
  "Create a new finite state machine by making the given FSM optional (ie. 
  either one or zero of the FSM is accepted)."
  [optional]
  (assoc optional
         :graph (-> (:graph optional)
                    (uber/add-edges [(:start optional)
                                     (:accept optional)]))))

(defn zero-or-more-fsm
  "Create a new finite state machine by making it be accepted zero or more
  times (ie. the Kleene star operation)."
  [zero-or-more]
  (let [node (new-node)]
    (assoc zero-or-more
           :start node
           :accept (:start zero-or-more)
           :states (cset/union (:states zero-or-more) #{node})
           :graph (-> (:graph zero-or-more)
                      (uber/add-edges [node (:start zero-or-more)])
                      (uber/add-edges [(:accept zero-or-more)
                                       (:start zero-or-more)])))))

(defn one-or-more-fsm
  "Create a new finite state machine by making it be accepted one or more
  times (ie. the plus operation)."
  [one-or-more]
  (assoc one-or-more
         :graph (-> (:graph one-or-more)
                    (uber/add-edges [(:accept one-or-more)
                                     (:start one-or-more)]))))

(defn read-next
  "Given a FSM and its current state, read the next symbol given.
  Return the updated state(s) if reading the symbol was successful, nil if
  reading was unsuccessful."
  [fsm curr-state next-symb]
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Foo Bar 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO Patterns from external profiles 
(defn mapify-patterns
  "Given a profile, turn its Pattern vector into a map between the Pattern IDs
  and the patterns themselves."
  [{:keys [patterns]}]
  (zipmap (mapv :id patterns) patterns))

;; TODO Templates from external profiles
(defn mapify-templates
  "Given a profile, turn its Templates vector into a map between the Template
  IDs and the templates themselves."
  [{:keys [templates]}]
  (zipmap (mapv :id templates) templates))

(defn specify-templates
  "Given a ID to Template map, turn the Templates into validator functions."
  [template-map]
  (let [ids (keys template-map)
        validators (mapv (-> % (partial get template-map)
                             (validate-statement-2)) ids)]
    (zipmap ids validators)))

(defn pattern-dispatch [object & others]
  (case (:type object)
    "StatementTemplate" :template
    "Pattern" (first (keys
                      (dissoc object :id :type :prefLabel :definition
                              :primary :inScheme :deprecated)))
    :else (throw
           (ex-info "Exception: Not a Pattern nor StatementTemplate" object))))

(defn mechanize-by-id [object-map id]
  (-> id (partial get object-map) (mechanize-pattern object-map)))

(defmulti mechanize-pattern pattern-dispatch)

(defmethod mechanize-pattern :sequence [pattern object-map]
  (let [seqn (:sequence pattern)
        fsms (mapv (partial mechanize-by-id object-map) seqn)]
    (sequence-fsm fsms)))

(defmethod mechanize-pattern :alternates [{:keys [alternates]} object-map]
  (let [fsms (mapv (partial mechanize-by-id object-map) alternates)]
    (alternates-fsm fsms)))

(defmethod mechanize-pattern :optional [{:keys [optional]} object-map]
  (let [fsm (partial mechanize-by-id object-map) optional]
    (optional-fsm fsm)))

(defmethod mechanize-pattern :oneOrMore [{:keys [oneOrMore]} object-map]
  (let [fsm (partial mechanize-by-id object-map) oneOrMore]
    (one-or-more-fsm fsm)))

(defmethod mechanize-pattern :zeroOrMore [{:keys [zeroOrMore]} object-map]
  (let [fsm (partial mechanize-by-id object-map) zeroOrMore]
    (zero-or-more-fsm fsm)))

(defmethod mechanize-pattern :template [template object-map]
  (transition-fsm (:id template)
                  (partial tv/validate-statement-2 template)))
