(ns com.yetanalytics.persephone.pattern.fsm
  (:require [clojure.spec.alpha :as s]
            [clojure.set        :as cset]
            [com.yetanalytics.persephone.pattern.fsm-spec :as fs]))

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

;; State alphatization

(defn- alphatize-states-fsm*
  [{type :type states :states :as fsm} start-count]
  (let [[states-m ncount]   (reduce (fn [[m counter] state]
                                      [(assoc m state counter) (inc counter)])
                                    [{} start-count]
                                    states)
        old-states->new-set (fn [states]
                              (->> states (mapv states-m) set))
        update-state-keys   (fn [m]
                              (cset/rename-keys m states-m))
        update-dests*       (fn [acc symb dests]
                              (let [new-dests (if (= :nfa type)
                                                (old-states->new-set dests)
                                                (states-m dests))]
                                (assoc acc symb new-dests)))
        update-dests        (fn [transitions]
                              (reduce-kv
                               (fn [acc src trans]
                                 (->> trans
                                      (reduce-kv update-dests* {})
                                      (assoc acc src)))
                               {}
                               transitions))]
    [(-> fsm
         (update :states old-states->new-set)
         (update :start states-m)
         (update :accepts old-states->new-set)
         (update :transitions update-state-keys)
         (update :transitions update-dests)
         (vary-meta update :states update-state-keys))
     ncount]))

(s/fdef alphatize-states-fsm
  :args (s/cat :fsm (s/or :nfa fs/nfa-spec
                          :dfa (s/or :ints fs/dfa-spec
                                     :sets fs/set-dfa-spec)))
  :ret (s/or :nfa fs/nfa-spec :dfa fs/dfa-spec)
  :fn (fn [{:keys [args ret]}]
        (= (count (:states args))
           (count (:states ret)))))

(defn alphatize-states-fsm
  "Rename all states in a single FSM. Renames all map keys for the `:states`
   metadata value if provided."
  [fsm]
  (first (alphatize-states-fsm* fsm 0)))

(s/fdef alphatize-states
  :args (s/cat :fsm-coll (s/every (s/or :nfa fs/nfa-spec
                                        :dfa (s/or :ints fs/dfa-spec
                                                   :sets fs/set-dfa-spec))
                                  :min-count 1
                                  :gen-max 10))
  :ret (s/every (s/or :nfa fs/nfa-spec :dfa fs/dfa-spec)
                :min-count 1)
  :fn (fn [{:keys [args ret]}]
        (= (fs/count-states args)
           (count (:states ret)))))

(defn alphatize-states
  "Rename all states in a collection of FSMs such that no two states
   share the same name. Renames all map keys for the `:states` metadata
   value if provided."
  [fsm-coll]
  (loop [new-fsm-queue []
         fsm-queue     fsm-coll
         counter       0]
    (if-let [fsm (first fsm-queue)]
      (let [[new-fsm new-counter] (alphatize-states-fsm* fsm counter)]
        (recur (conj new-fsm-queue new-fsm)
               (rest fsm-queue)
               new-counter))
      new-fsm-queue)))

;; Epsilon transition appending

(defn- add-epsilon-transitions
  "Update the transition table to add new epsilon transitions from
   a collection of sources to a new destination."
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

;; NOTE: We could optimize construction by tracking state as an int instead
;; of an int set, and making transitions a vector instead of an int-keyed
;; map, but this would compromise generality across fns in this namespace.

;;    i    
;; x ---> a

(s/fdef transition-nfa
  :args (s/cat :fn-symbol ::fs/symbol-id
               :fn ::fs/symbol-pred
               :meta? (s/? boolean?))
  :ret (and fs/thompsons-nfa-spec
            (fn [{:keys [states]}] (= 2 (count states)))))

(defn transition-nfa
  "Create an NFA that accepts a single input. If `meta?` is `true`, then
   associate `:states` metadata to the NFA."
  ([fn-symbol f]
   (transition-nfa fn-symbol f false))
  ([fn-symbol f meta?]
   (let [start   0
         accept  1
         new-nfa {:type        :nfa
                  :symbols     {fn-symbol f}
                  :states      #{start accept}
                  :start       start
                  :accepts     #{accept}
                  :transitions {start {fn-symbol #{accept}} accept {}}}]
     (if meta?
       (with-meta new-nfa {:states {start {} accept {}}})
       new-nfa))))

;; -> q ==> s --> s ==> a

(s/fdef concat-nfa
  :args (s/cat :nfa-coll (s/every fs/thompsons-nfa-spec
                                  :min-count 1
                                  :gen-max 5)
               :meta? (s/? boolean?))
  :ret fs/thompsons-nfa-spec
  :fn (fn [{:keys [args ret]}]
        (= (fs/count-states (:nfa-coll args)) (count (:states ret)))))

(defn- concat-nfa*
  [nfa-coll]
  (loop [fsm      (first nfa-coll)
         fsm-list (rest nfa-coll)]
    (if-let [next-fsm (first fsm-list)]
      (let [;; Destructure
            {symbols     :symbols
             states      :states
             start       :start
             accepts     :accepts
             transitions :transitions}      fsm
            {next-symbols     :symbols
             next-states      :states
             next-start       :start
             next-accepts     :accepts
             next-transitions :transitions} next-fsm
            ;; Create
            new-trans (-> (merge transitions next-transitions)
                          (update-in
                           [(first accepts) :epsilon]
                           (fn [nexts] (set (conj nexts next-start)))))
            new-fsm   {:type        :nfa
                       :symbols     (merge symbols next-symbols)
                       :states      (cset/union states next-states)
                       :start       start
                       :accepts     next-accepts
                       :transitions new-trans}]
        (recur new-fsm (rest fsm-list)))
      fsm)))

(defn concat-nfa
  "Concat a collection of NFAs in sequential order. The function throws
   an exception on empty collections. If `meta?` is `true`, then assoc the
   alphatized `:states` metadata to the NFA."
  ([nfa-coll]
   (concat-nfa nfa-coll false))
  ([nfa-coll meta?]
   (assert-nonempty-fsm-coll nfa-coll "concat-nfa")
   (let [nfa-coll (alphatize-states nfa-coll)
         new-nfa  (concat-nfa* nfa-coll)]
     (if meta?
       (with-meta new-nfa {:states (->> nfa-coll
                                        (map meta)
                                        (map :states)
                                        (apply merge))})
       new-nfa))))

;;    + --> s ==> s --v
;; -> q               f
;;    + --> s ==> s --^

(s/fdef union-nfa
  :args (s/cat :nfa-coll (s/every fs/thompsons-nfa-spec
                                  :min-count 1
                                  :gen-max 5)
               :meta? (s/? boolean?))
  :ret fs/thompsons-nfa-spec
  :fn (fn [{:keys [args ret]}]
        (= (+ 2 (fs/count-states (:nfa-coll args))) (count (:states ret)))))

(defn union-nfa
  "Construct a union of NFAs (corresponding to the \"|\" regex symbol.)
   If `meta?` is `true`, then assoc the alphatized `:states` metadata to
   the NFA."
  ([nfa-coll]
   (union-nfa nfa-coll false))
  ([nfa-coll meta?]
   (assert-nonempty-fsm-coll nfa-coll "union-nfa")
   (let [nfa-coll    (alphatize-states nfa-coll)
         old-states  (reduce (fn [acc fsm] (->> fsm :states (cset/union acc)))
                             #{}
                             nfa-coll)
         old-starts  (set (mapv :start nfa-coll))
         old-accepts (mapv #(-> % :accepts first) nfa-coll)
         max-state   (dec (count old-states)) ; optimization b/c alphatization
         new-start   (+ max-state 1)
         new-accept  (+ max-state 2)
         new-states  (cset/union old-states #{new-start new-accept})
         new-trans*  (reduce (fn [acc fsm] (->> fsm :transitions (merge acc)))
                             {}
                             nfa-coll)
         new-trans   (-> new-trans*
                         (add-epsilon-transitions old-accepts new-accept)
                         (update-in [new-start :epsilon] (constantly old-starts))
                         (update new-accept (constantly {})))
         new-nfa     {:type        :nfa
                      :symbols     (into {} (mapcat :symbols nfa-coll))
                      :start       new-start
                      :accepts     #{new-accept}
                      :states      new-states
                      :transitions new-trans}]
     (if meta?
       (let [meta-states
             (merge {new-start {} new-accept {}}
                    (->> nfa-coll (map meta) (map :states) (apply merge)))]
         (with-meta new-nfa {:states meta-states}))
       new-nfa))))

;;          v-----+
;; -> q --> s ==> s --> f
;;    +-----------------^

(s/fdef kleene-nfa
  :args (s/cat :nfa fs/thompsons-nfa-spec
               :meta? (s/? boolean?))
  :ret fs/thompsons-nfa-spec)

(defn kleene-nfa
  "Apply the Kleene star operation on an NFA (the \"*\" regex symbol),
   which means the NFA can be taken zero or more times. If `meta` is `true`,
   copy the original `:states` metadata."
  ([nfa]
   (kleene-nfa nfa false))
  ([{:keys [symbols states start accepts transitions] :as nfa} meta?]
   (let [max-state    (apply max states)
         new-start    (+ max-state 1)
         new-accept   (+ max-state 2)
         old-accept   (first accepts)
         union-accept (fn [s] (cset/union s #{start new-accept}))
         new-trans    (-> transitions
                          (update-in [new-start :epsilon] union-accept)
                          (update-in [old-accept :epsilon] union-accept)
                          (update new-accept (constantly {})))
         new-nfa      {:type        :nfa
                       :symbols     symbols
                       :states      (cset/union states #{new-start new-accept})
                       :start       new-start
                       :accepts     #{new-accept}
                       :transitions new-trans}]
     (if meta?
       (with-meta new-nfa (-> (meta nfa)
                              (assoc-in [:states new-start] {})
                              (assoc-in [:states new-accept] {})))
       new-nfa))))

;;    +-----------------v
;; -> q --> s ==> s --> f

(s/fdef optional-nfa
  :args (s/cat :nfa fs/thompsons-nfa-spec
               :meta? (s/? boolean?))
  :ret fs/thompsons-nfa-spec)

(defn optional-nfa
  "Apply the optional operation on an NFA (the \"?\" regex symbol), which
   means the NFA may or may not be taken. If `meta` is `true`, copy the
   original `:states` metadata."
  ([nfa]
   (optional-nfa nfa false))
  ([{:keys [symbols states start accepts transitions] :as nfa} meta?]
   (let [max-state    (apply max states)
         new-start    (+ max-state 1)
         new-accept   (+ max-state 2)
         old-accept   (first accepts)
         union-start  (fn [s] (cset/union s #{start new-accept}))
         union-accept (fn [s] (cset/union s #{new-accept}))
         new-trans    (-> transitions
                          (update-in [new-start :epsilon] union-start)
                          (update-in [old-accept :epsilon] union-accept)
                          (update new-accept (constantly {})))
         new-nfa      {:type        :nfa
                       :symbols     symbols
                       :states      (cset/union states #{new-start new-accept})
                       :start       new-start
                       :accepts     #{new-accept}
                       :transitions new-trans}]
     (if meta?
       (with-meta new-nfa (-> (meta nfa)
                              (assoc-in [:states new-start] {})
                              (assoc-in [:states new-accept] {})))
       new-nfa))))

;;          v-----+
;; -> q --> s ==> s --> f

(s/fdef plus-nfa
  :args (s/cat :nfa fs/thompsons-nfa-spec
               :meta? (s/? boolean?))
  :ret fs/thompsons-nfa-spec)

(defn plus-nfa
  "Apply the Kleene plus operation on an NFA (the \"+\" regex symbol),
   which means the NFA can be taken one or more times. If `meta` is `true`,
   copy the original `:states` metadata."
  ([nfa]
   (plus-nfa nfa false))
  ([{:keys [symbols states start accepts transitions] :as nfa} meta?]
   (let [max-state    (apply max states)
         new-start    (+ max-state 1)
         new-accept   (+ max-state 2)
         old-accept   (first accepts)
         union-start  (fn [s] (cset/union s #{start}))
         union-accept (fn [s] (cset/union s #{start new-accept}))
         new-trans    (-> transitions
                          (update-in [new-start :epsilon] union-start)
                          (update-in [old-accept :epsilon] union-accept)
                          (update new-accept (constantly {})))
         new-nfa      {:type        :nfa
                       :symbols     symbols
                       :states      (cset/union states #{new-start new-accept})
                       :start       new-start
                       :accepts     #{new-accept}
                       :transitions new-trans}]
     (if meta?
       (with-meta new-nfa (-> (meta nfa)
                              (assoc-in [:states new-start] {})
                              (assoc-in [:states new-accept] {})))
       new-nfa))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFA to DFA Conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- init-queue [init]
  #?(:clj (conj clojure.lang.PersistentQueue/EMPTY init)
     :cljs (conj cljs.core/PersistentQueue.EMPTY init)))

;; `epsilon-closure` is public for testing purposes, and because it's a
;; well-defined mathematical operation on NFAs.

;; We only care about the transitions, hence the spec.
;; Since `epsilon-closure` is called by internal fns like `nfa->dfa*`,
;; spec-ing the whole NFA may cause a spec error.
(s/fdef epsilon-closure
  :args (s/cat :nfa (s/keys :req-un [:nfa/transitions])
               :init-state :nfa/state)
  :ret :nfa/states)

(defn epsilon-closure
  "Given an NFA with transitions and an `init-state`, returns the
   epsilon closure for that state."
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
   1. Create the start state of the DFA by taking the epsilon closure
      of the start state of the NFA.
   2. Perform the following for the new DFA state:
      For each possible input symbol:
      a) Apply move to the newly-created state and the input symbol;
         this will return a set of states.
      b) Apply the epsilon closure to this set of states, possibly
         resulting in a new set.
      This set of NFA states will be a single state in the DFA.
   3. Each time we generate a new DFA state, we must apply step 2 to
      it. The process is complete when applying step 2 does not yield
      any new states.
   4. The accept states of the DFA are those which contain any of the
      accept states of the NFA.
   Internally, this performs a tree search that guarentees no
   unreachable states."
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
           ;; An empty next-dfa-state value means that the symbol cannot be
           ;; read at the previous state in the NFA, so we avoid adding it so
           ;; it will also fail in the DFA.
           (let [next-state (not-empty next-dfa-state)]
             (cond-> dfa
               ;; Add new DFA state
               next-state
               (update :states conj next-state)
               ;; Add new DFA state as a transition destination
               next-state
               (update-in [:transitions prev-dfa-state] merge {symb next-state})
               ;; Add new DFA state as a transition source, if needed
               (and next-state (not (contains? dfa-states next-state)))
               (update :transitions assoc next-state {})
               ;; Add new DFA state as an accept state, if needed
               (and next-state (nfa-accept-states? next-state))
               (update :accepts conj next-state))))
          (add-state-to-queue
           [queue {dfa-states :states :as _dfa} next-dfa-state]
           ;; Don't add to queue if next-dfa-state is empty (signifying failure)
           ;; or if it's already in the DFA (i.e. it's already visited).
           (cond-> queue
             (and (not-empty next-dfa-state)
                  (not (contains? dfa-states next-dfa-state)))
             (conj next-dfa-state)))
          (add-missing-srcs
           [transitions states]
           (reduce (fn [trans' s]
                     (if (not (contains? transitions s))
                       (assoc trans' s {})
                       trans'))
                   transitions
                   states))]
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
        (-> dfa
            ;; Add source states not present in the transition table
            (update :transitions add-missing-srcs (:states dfa))
            ;; Alphatize DFA states
            alphatize-states-fsm)))))

(s/fdef nfa->dfa
  :args (s/cat :nfa fs/nfa-spec)
  :ret fs/dfa-spec)

(defn nfa->dfa
  "Given an NFA with epsilon transitions, perform the powerset
   construction in order to (semi)-determinize it and remove
   epsilon transitions."
  [{start :start :as nfa}]
  (nfa->dfa* nfa (epsilon-closure nfa start)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DFA Minimization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- reverse-transitions
  "Create a new transition table that reverses the source and dest
   states for each symbol. Since multiple source-symbol pairs may lead
   to the same destination state in the original DFA, this produces a
   NFA, where the new source-symbol pair leads to multiple new dests."
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
   2. The original accept states form a start state (so we represent
      the start state with the set of accept states, rather than a
      single state)
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
  :args (s/cat :dfa fs/dfa-spec)
  :ret fs/dfa-spec
  :fn (fn [{:keys [args ret]}]
        (<= (-> ret :states count)
            (-> args :dfa :states count))))

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
;; Input Reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- read-next-nfa
  "Like `read-next` for NFAs, except it takes in the state directly.
   Runs the epsilon closure first before reading the input; `:epsilon`
   is therefore not recorded as a visited transition."
  [{:keys [symbols accepts transitions] :as nfa} input state visited]
  (if-let [trans (get transitions state)]
    (for [symb  (keys trans)
          dest  (get trans symb)
          :let  [eps-closure (epsilon-closure nfa dest)
                 visited     (when visited (conj visited symb))]
          :when (and (not= :epsilon symb)
                     ((get symbols symb) input))
          state eps-closure
          :let  [state-info (cond-> {:state     state
                                     :accepted? (contains? accepts state)}
                              visited
                              (assoc :visited visited))]]
      state-info)
    (let [err-msg (str "State not found in the finite state machine: "
                       (pr-str state))]
      (throw #?(:clj (Exception. err-msg)
                :cljs (js/Error. err-msg))))))

(defn- read-next-dfa
  "Like `read-next` for DFAs, except it takes in the state directly, rather
   than state info. Returns a set of state info maps."
  [{:keys [symbols accepts transitions] :as _dfa} input state visited]
  (if-let [trans (get transitions state)]
    (reduce-kv (fn [acc symb dest]
                 (if (and (not= :epsilon symb)
                          ((get symbols symb) input))
                   (conj acc (cond-> {:state     dest
                                      :accepted? (contains? accepts dest)}
                               visited
                               (assoc :visited (conj visited symb))))
                   acc))
               #{}
               trans)
    (let [err-msg (str "State not found in the finite state machine: "
                       (pr-str state))]
      (throw #?(:clj (Exception. err-msg)
                :cljs (js/Error. err-msg))))))

(s/def ::record-visits? boolean?)
(s/def ::start-opts (s/keys :opt-un [::record-visits?]))

(s/fdef read-next
  :args (s/cat :fsm (s/or :nfa fs/nfa-spec :dfa fs/dfa-spec)
               :start-opts (s/? ::start-opts)
               :state-info (s/nilable fs/state-info-spec)
               :input any?)
  :ret fs/state-info-spec)

(defmulti read-next
  "Given a compiled `fsm`, the current `state-info`, and `input`, let
   the FSM read that input; this function returns updated state info.
   The state info is a set of maps with the following fields:
   
   | Key | Description
   | --- | ---
   | `:state`     | The states arrived at in the FSM after reading the input.
   | `:accepted?` | `true` if the FSM as arrived at an accept state after reading the input; `false` otherwise.

   In addition to the required args, the optional `start-opts` map
   can be passed. Valid options include:

   | Key               | Description
   | ---               | ---
   | `:record-visits?` | If `true`, each state info map will contain an extra `:visited` value that is a list of visited transition IDs.
   
   If `state-info` is `nil`, the function starts at the start state,
   with `start-opts` applied as needed. As indicated by its name,
   `start-opts` not applied when `state-info` is not `nil`.

   If `state-info` is `#{}`, i.e. is empty, it is returned as-is, since an
   empty set indicates that no more states can be matched."
  {:arglists '([fsm state-info input]
               [fsm start-opts state-info input])}
  (fn [fsm & _] (:type fsm)))

(defmethod read-next :nfa
  ([nfa state-info input]
   (read-next nfa {} state-info input))
  ([nfa start-opts state-info input]
   (let [;; Destructuring
         {:keys [record-visits?]} start-opts
         {:keys [start accepts]}  nfa
         ;; State Info
         state-info (if (nil? state-info)
                      (map (fn [s]
                             (cond-> {:state     s
                                      :accepted? (contains? accepts s)}
                               record-visits?
                               (assoc :visited [])))
                           (epsilon-closure nfa start))
                      state-info)]
     (->> state-info
          (map (fn [{s :state v :visited}] (read-next-nfa nfa input s v)))
          (map set)
          (apply cset/union)))))

(defmethod read-next :dfa
  ([dfa state-info input]
   (read-next dfa {} state-info input))
  ([dfa start-opts state-info input]
   (let [;; Destructuring
         {:keys [record-visits?]} start-opts
         {:keys [start accepts]} dfa
         ;; State Info
         state-info (if (nil? state-info)
                      #{(cond-> {:state     start
                                 :accepted? (contains? accepts start)}
                          record-visits?
                          (assoc :visited []))}
                      state-info)]
     (->> state-info
          (map (fn [{s :state v :visited}] (read-next-dfa dfa input s v)))
          (apply cset/union)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Input Reading Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef accepted?
  :args (s/cat :state-info fs/state-info-spec)
  :ret boolean?)

(defn accepted?
  [state-info]
  (boolean (some :accepted? state-info)))

(s/fdef rejected?
  :args (s/cat :state-info fs/state-info-spec)
  :ret boolean?)

(defn rejected?
  [state-info]
  (= #{} state-info))
