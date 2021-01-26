(ns com.yetanalytics.persephone.pattern-validation
  (:require [clojure.core.match :as m]
            [clojure.walk :as w]
            [clojure.zip :as zip]
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
              #_(if (map? (:optional branch))
                (-> branch :optional :id list)
                (-> branch :optional list))
              (contains? branch :oneOrMore)
              (-> branch :oneOrMore list)
              #_(if (map? (:oneOrMore branch))
                (-> branch :oneOrMore :id list)
                (-> branch :oneOrMore list))
              (contains? branch :zeroOrMore)
              (-> branch :zeroOrMore list)
              #_(if (map? (:zeroOrMore branch))
                (-> branch :zeroOrMore :id list)
                (-> branch :zeroOrMore list))))
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
  [node]
  (m/match [node]
    [{:type "Pattern" :sequence _}]
    (-> node :sequence fsm/concat-nfa)
    [{:type "Pattern" :alternates _}]
    (-> node :alternates fsm/union-nfa)
    [{:type "Pattern" :optional _}]
    (-> node :optional fsm/optional-nfa)
    [{:type "Pattern" :zeroOrMore _}]
    (-> node :zeroOrMore fsm/kleene-nfa)
    [{:type "Pattern" :oneOrMore _}]
    (-> node :oneOrMore fsm/plus-nfa)
    [{:type "StatementTemplate"}]
    (fsm/transition-nfa (:id node) (partial tv/validate-statement node))
    ;; Node is not a map
    :else node))

(defn pattern-tree->fsm
  "Turn a Pattern tree data structure into an FSM using a post-order DFS tree
   traversal."
  [pattern-tree]
  (fsm/reset-counter)
  (->> pattern-tree (w/postwalk pattern->fsm) fsm/nfa->dfa fsm/minimize-dfa))

(defn profile->fsms
  "Pipeline function that turns a Profile into a vectors of FSMs that can
   perform Statement validation. Each entry corresponds to a primary Pattern.
   Note: Assumes syntactically valid Patterns from a valid Profile."
  [profile]
  (let [temp-pat-map (mapify-all profile)
        pattern-seq (primary-patterns profile)]
    (fsm/alphatize-states
     (mapv (fn [pattern] (-> pattern
                             (grow-pattern-tree temp-pat-map)
                             pattern-tree->fsm))
           pattern-seq))))