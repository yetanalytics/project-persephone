(ns com.yetanalytics.persephone.pattern
  (:require [clojure.walk :as w]
            [clojure.zip  :as zip]
            [com.yetanalytics.persephone.utils.maps  :as m]
            [com.yetanalytics.persephone.pattern.fsm :as fsm]
            [com.yetanalytics.persephone.template    :as t]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Asserts + Exceptions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- throw-invalid-pattern
  [pattern]
  (throw (ex-info "Pattern is missing required fields."
                  {:kind ::invalid-pattern :pattern pattern})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Object maps and seqs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO Templates and Patterns from external profiles 
(defn mapify-all
  "Put all Templates and Patterns of a profile into a unified map."
  [{:keys [templates patterns] :as _profile}]
  (merge (m/mapify-coll templates) (m/mapify-coll patterns)))

(defn primary-patterns
  "Get a sequence of all of the primary Patterns in a Profile."
  [profile]
  (->> profile :patterns (filter #(-> % :primary true?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Object maps -> tree data structure
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Public for testing - also in case anyone needs a pattern zipper
(defn create-zipper
  "Create a zipper out of the root `pattern`, where each node is a
   Pattern and its sub-Patterns/Templates are its children."
  [pattern]
  (letfn [(branch? [node]
            (= "Pattern" (:type node)))
          (children [branch]
            (cond
              (contains? branch :sequence)
              (-> branch :sequence seq)
              (contains? branch :alternates)
              (-> branch :alternates seq)
              (contains? branch :optional)
              (-> branch :optional list)
              (contains? branch :oneOrMore)
              (-> branch :oneOrMore list)
              (contains? branch :zeroOrMore)
              (-> branch :zeroOrMore list)
              :else
              (throw-invalid-pattern branch)))
          (make-node [node children-seq]
            (let [children-vec (vec children-seq)]
              (cond
                (contains? node :sequence)
                (assoc node :sequence children-vec)
                (contains? node :alternates)
                (assoc node :alternates children-vec)
                (contains? node :optional)
                (assoc node :optional (first children-vec))
                (contains? node :oneOrMore)
                (assoc node :oneOrMore (first children-vec))
                (contains? node :zeroOrMore)
                (assoc node :zeroOrMore (first children-vec))
                :else
                (throw-invalid-pattern node))))]
    (zip/zipper branch? children make-node pattern)))

;; Private because this is just a helper for `grow-pattern-tree`
(defn- expand-children
  "Given a location in a pattern zipper and a table of objects
   (Templates and Patterns), replace the identifiers with their
   respective objects (either a Template map, a Pattern, or an
   array of Patterns)."
  [pattern-loc objects-map]
  (if (zip/branch? pattern-loc)
    (let [loc-children  (zip/children pattern-loc)
          loc-children' (map (partial get objects-map) loc-children)
          loc-node      (zip/node pattern-loc)
          loc-node'     (zip/make-node pattern-loc loc-node loc-children')]
      (zip/replace pattern-loc loc-node'))
    ;; Can't change children if they dont' exist  
    pattern-loc))

(defn grow-pattern-tree
  "Build a tree data structure out of a Pattern using zippers. Each
   internal node is a Pattern and each leaf node is a Template."
  [pattern objects-map]
  (loop [pattern-loc (create-zipper pattern)]
    (if (zip/end? pattern-loc)
      (zip/node pattern-loc) ; Return the root
      (let [new-loc (expand-children pattern-loc objects-map)]
        (recur (zip/next new-loc))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern tree -> DFA
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- pattern->fsm
  "Compose an FSM to read xAPI Statements in a bottom-up manner."
  ([node]
   (pattern->fsm node nil))
  ([{:keys [type id] :as node} stmt-ref-opts]
   (cond
     (= "Pattern" type)
     (let [{?seq-nfas :sequence
            ?alt-nfas :alternates
            ?opt-nfa  :optional
            ?zom-nfa  :zeroOrMore
            ?oom-nfa  :oneOrMore} node]
       (cond
         ?seq-nfas (fsm/concat-nfa ?seq-nfas)
         ?alt-nfas (fsm/union-nfa ?alt-nfas)
         ?opt-nfa  (fsm/optional-nfa ?opt-nfa)
         ?zom-nfa  (fsm/kleene-nfa ?zom-nfa)
         ?oom-nfa  (fsm/plus-nfa ?oom-nfa)
         :else     (throw-invalid-pattern node)))
     (= "StatementTemplate" type)
     (fsm/transition-nfa id (t/create-template-predicate node stmt-ref-opts))
     :else
     node)))

(defn pattern-tree->dfa
  "Given `pattern-tree` (returned by `grow-pattern-tree`), construct a
   minimized DFA to read xAPI statements."
  ([pattern-tree]
   (pattern-tree->dfa pattern-tree nil))
  ([pattern-tree stmt-ref-opts]
   (->> pattern-tree
        (w/postwalk (fn [node] (pattern->fsm node stmt-ref-opts)))
        fsm/nfa->dfa
        fsm/minimize-dfa)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pattern tree -> NFA
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- pattern->fsm*
  "Compose an FSM with metadata to read Statement Template IDs"
  [{:keys [type id] :as node}]
  (cond
    (= "Pattern" type)
    (let [{?seq-nfas :sequence
           ?alt-nfas :alternates
           ?opt-nfa :optional
           ?zom-nfa :zeroOrMore
           ?oom-nfa :oneOrMore} node
          new-nfa (cond
                    ?seq-nfas (fsm/concat-nfa ?seq-nfas true)
                    ?alt-nfas (fsm/union-nfa ?alt-nfas true)
                    ?opt-nfa  (fsm/optional-nfa ?opt-nfa true)
                    ?zom-nfa  (fsm/kleene-nfa ?zom-nfa true)
                    ?oom-nfa  (fsm/plus-nfa ?oom-nfa true))
          meta-fn (fn [states-m]
                    (reduce-kv (fn [m k v]
                                 (assoc m k (update v :path conj id)))
                               {}
                               states-m))]
      (vary-meta new-nfa update :states meta-fn))
    (= "StatementTemplate" type)
    (let [pred   (fn [input] (= id input))
          nfa    (fsm/transition-nfa id pred true)
          states (:states nfa)]
      (with-meta nfa {:states (reduce (fn [acc s] (assoc acc s {:path [id]}))
                                      {}
                                      states)}))
    :else
    node))

(defn pattern-tree->nfa
  "Given `pattern-tree` (returned by `grow-pattern-tree`), return an NFA
   with metadata associating each state with the corresponding
   template-pattern path that they are derived from. Unlike the FSM
   returned by `pattern->fsm`, the predicates take in ID strings, not xAPI
   Statements, as input."
  [pattern-tree]
  (w/postwalk pattern->fsm* pattern-tree))

(defn read-visited-templates
  "Given `nfa` returned by `pattern-tree->nfa`, read in the `template-ids`
   sequence and return the path of Patterns and Templates that was taken
   during the original matching process. If `template-ids` is empty or
   represents an invalid input sequence, return an empty seq."
  [nfa template-ids]
  ;; This function takes advantage of the fact that due to the way our
  ;; NFAs are constructed, the final epsilon closure will contain the
  ;; state with all the relevant pattern path info (e.g. in a union
  ;; of transitions, the pentultimate states will include the template ID).
  (let [nfa-metadata (meta nfa)]
    (if (not-empty template-ids)
      (loop [tokens  template-ids
             sinfo   nil]
        (let [[fst & rst] tokens]
          (cond
            ;; Invalid token sequence - abort
            (= #{} sinfo)
            '()
            
            ;; Last token - return
            (empty? rst)
            (let [sinfo* (fsm/read-next nfa sinfo fst)]
              (->> sinfo*
                   (map :state)
                   (map (fn [s] (get-in nfa-metadata [:states s :path])))
                   concat
                   (filter (fn [path] (= fst (first path))))
                   distinct
                   (map vec)))
            
            ;; More tokens- continue
            :else
            (recur rst
                   (fsm/read-next nfa sinfo fst)))))
      ;; Empty `template-ids` - no patterns were matched against
      '())))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Putting it all together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn profile->fsms
  "Given a Profile, returns a map between primary Pattern IDs and
   their respective FSMs that can perform Statement validation.
   Assumes a valid Profile."
  ([profile]
   (profile->fsms profile nil))
  ([profile statement-ref-fns]
   (let [temp-pat-map (mapify-all profile)
         pattern-seq  (primary-patterns profile)]
     (reduce (fn [acc {pat-id :id :as pattern}]
               (let [pat-tree (grow-pattern-tree pattern temp-pat-map)
                     pat-dfa  (pattern-tree->dfa pat-tree statement-ref-fns)
                     pat-nfa  (pattern-tree->nfa pat-tree)]
                 (assoc acc pat-id {:id  pat-id
                                    :dfa pat-dfa
                                    :nfa pat-nfa})))
             {}
             pattern-seq))))
