(ns com.yetanalytics.persephone.pattern-validation
  (:require [clojure.walk :as w]
            [clojure.zip  :as zip]
            [com.yetanalytics.persephone.utils.fsm :as fsm]
            [com.yetanalytics.persephone.template-validation :as tv]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Object maps and seqs
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

(defn mapify-all
  "Put all Templates and Patterns of a profile into a unified map."
  [profile]
  (merge (mapify-patterns profile) (mapify-templates profile)))

(defn primary-patterns
  "Get a sequence of all of the primary Patterns in a Profile."
  [profile]
  (->> profile :patterns (filter #(-> % :primary true?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Object maps -> tree data structure -> FSM
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-zipper
  "Create a zipper out of a pattern."
  [pattern]
  (letfn [(branch? [node]
            (contains? #{"Pattern"} (:type node)))
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
              (-> branch :zeroOrMore list)))
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
                (assoc node :zeroOrMore (first children-vec)))))]
    (zip/zipper branch? children make-node pattern)))

(defn update-children
  "Given a location in a pattern zipper and a table of objects (Templates and 
   Patterns), replace the identifiers with their respective objects (either
   a Template map, a Pattern, or an array of Patterns)."
  [pattern-loc objects-map]
  (if (zip/branch? pattern-loc)
    (zip/replace pattern-loc
                 (zip/make-node pattern-loc
                                (zip/node pattern-loc)
                                (map (partial get objects-map)
                                     (zip/children pattern-loc))))
    ;; Can't change children if they dont' exist  
    pattern-loc))

(defn grow-pattern-tree
  "Build a tree data structure out of a Pattern using zippers. Each internal
   node is a Pattern and each leaf node is a Statement Template."
  [pattern objects-map]
  (loop [pattern-loc (create-zipper pattern)]
    (if (zip/end? pattern-loc)
      (zip/node pattern-loc) ;; Return the root
      (let [new-loc (update-children pattern-loc objects-map)]
        (recur (zip/next new-loc))))))

#_{:clj-kondo/ignore [:unresolved-symbol]} ;; Kondo doesn't recognize core.match
(defn pattern->fsm
  "Given a Pattern (e.g. as a node in a Pattern tree), return the corresponding
   FSM. The FSM built at this node is a composition of FSMs built from the child
   nodes."
  [{:keys [type id] :as node}]
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
            (fsm/plus-nfa oneOrMore)))
    (= "StatementTemplate" type)
    (fsm/transition-nfa id (partial tv/valid-statement? node))
    :else
    node))

(defn pattern-tree->fsm
  "Turn a Pattern tree data structure into an FSM using a post-order
   DFS tree traversal."
  [pattern-tree]
  (->> pattern-tree (w/postwalk pattern->fsm) fsm/nfa->dfa fsm/minimize-dfa))

(defn profile->fsms
  "Given a Profile, returns a map between primary Pattern IDs and
   their respective FSMs that can perform Statement validation.
   Assumes a valid Profile."
  [profile]
  (let [temp-pat-map (mapify-all profile)
        pattern-seq (primary-patterns profile)]
    (reduce (fn [acc {pat-id :id :as pattern}]
              (let [pat-fsm (-> pattern
                                (grow-pattern-tree temp-pat-map)
                                pattern-tree->fsm)]
                (assoc acc pat-id pat-fsm)))
            {}
            pattern-seq)))
