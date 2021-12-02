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
;; Object maps -> tree data structure -> FSM
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn update-children
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
      (let [new-loc (update-children pattern-loc objects-map)]
        (recur (zip/next new-loc))))))

(defn pattern->fsm
  "Given a Pattern (e.g. as a node in a Pattern tree), return the
   corresponding FSM. The FSM built at this node is a composition of
   FSMs built from the child nodes."
  ([node]
   (pattern->fsm node nil))
  ([{:keys [type id] :as node} stmt-ref-opts]
   (cond
     (= "Pattern" type)
     (let [{:keys [sequence alternates optional zeroOrMore oneOrMore]} node]
       (cond (some? sequence)
             (fsm/concat-nfa sequence)
             (some? alternates)
             (fsm/union-nfa alternates)
             (some? optional)
             (fsm/optional-nfa optional)
             (some? zeroOrMore)
             (fsm/kleene-nfa zeroOrMore)
             (some? oneOrMore)
             (fsm/plus-nfa oneOrMore)
             :else
             (throw-invalid-pattern node)))
     (= "StatementTemplate" type)
     (fsm/transition-nfa id (t/create-template-predicate node stmt-ref-opts))
     :else
     node)))

(defn pattern-tree->fsm
  "Turn a Pattern tree data structure into an FSM using a post-order
   DFS tree traversal."
  ([pattern-tree]
   (pattern-tree->fsm pattern-tree nil))
  ([pattern-tree stmt-ref-opts]
   (->> pattern-tree
        (w/postwalk (fn [node] (pattern->fsm node stmt-ref-opts)))
        fsm/nfa->dfa
        fsm/minimize-dfa)))

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
               (let [pat-fsm (-> pattern
                                 (grow-pattern-tree temp-pat-map)
                                 (pattern-tree->fsm statement-ref-fns))]
                 (assoc acc pat-id pat-fsm)))
             {}
             pattern-seq))))

(defn pattern-accepts?
  "Given `state-info` `#{{:state 0 :accepted? true} ...}`, return `true` if
   at least one state counts as an accept state."
  [state-info]
  (->> state-info
       (map :accepted?)
       (filter true?)
       not-empty
       boolean))
