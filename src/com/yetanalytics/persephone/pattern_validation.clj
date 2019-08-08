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
  (letfn [(branch? [node] (contains? #{"Pattern"} (:type node)))
          (children [branch]
                    (m/match [branch]
                      [(_ :guard #(contains? % :sequence))]
                      (-> branch :sequence seq)
                      [(_ :guard #(contains? % :alternates))]
                      (-> branch :alternates seq)
                      [(_ :guard #(contains? % :optional))]
                      (if (map? (:optional branch))
                        (-> branch :optional :id list)
                        (-> branch :optional seq))
                      [(_ :guard #(contains? % :oneOrMore))]
                      (if (map? (:oneOrMore branch))
                        (-> branch :oneOrMore :id list)
                        (-> branch :oneOrMore seq))
                      [(_ :guard #(contains? % :zeroOrMore))]
                      (if (map? (:zeroOrMore branch))
                        (-> branch :zeroOrMore :id list)
                        (-> branch :zeroOrMore seq))))
          (make-node [node children-seq]
                     (let [children-vec (vec children-seq)]
                       (m/match [node]
                         [(_ :guard #(contains? % :sequence))]
                         (assoc node :sequence children-vec)
                         [(_ :guard #(contains? % :alternates))]
                         (assoc node :alternates children-vec)
                         [(_ :guard #(contains? % :optional))]
                         (assoc node :optional children-vec)
                         [(_ :guard #(contains? % :oneOrMore))]
                         (assoc node :oneOrMore children-vec)
                         [(_ :guard #(contains? % :zeroOrMore))]
                         (assoc node :zeroOrMore children-vec))))]
    (zip/zipper branch? children make-node pattern)))

(defn update-children
  "Given a location in a pattern zipper and a table of objects (Templates and 
  Patterns), replace the identifiers with their respective objects (either
  a Template map, a Pattern, or an array of Patterns)."
  [pattern-loc objects-map]
  #_(println (zip/branch? pattern-loc))
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

(defn build-node-fsm
  "Given a node in the Pattern tree, return the corresponding FSM.
  The FSM built at this node is a composition of FSMs built from the child
  nodes."
  [node]
  (m/match [node]
    [{:type "Pattern" :sequence _}]
    (-> node :sequence fsm/sequence-fsm)
    [{:type "Pattern" :alternates _}]
    (-> node :alternates fsm/alternates-fsm)
    [{:type "Pattern" :optional _}]
    (-> node :optional first fsm/optional-fsm)
    [{:type "Pattern" :zeroOrMore _}]
    (-> node :zeroOrMore first fsm/zero-or-more-fsm)
    [{:type "Pattern" :oneOrMore _}]
    (-> node :oneOrMore first fsm/one-or-more-fsm)
    [{:type "StatementTemplate"}]
    (fsm/transition-fsm (:id node) (partial tv/validate-statement node))
    ;; Node is not a map
    :else node))

(defn mechanize-pattern
  "Turn a Pattern tree data structure into an FSM using a post-order DFS tree
  traversal."
  [pattern-tree]
  (w/postwalk build-node-fsm pattern-tree))

(defn profile-to-fsm
  "Pipeline function that turns a Profile into a FSM that can perform
  Statement validation."
  [profile]
  (let [o-map (mapify-all profile)
        p-seq (primary-patterns profile)]
    (not-empty
     (map #(-> % (grow-pattern-tree o-map) mechanize-pattern) p-seq))))
