(ns com.yetanalytics.pattern-validation
  (:require [clojure.core.match :as m]
            [clojure.walk :as w]
            [clojure.zip :as zip]
            [com.yetanalytics.utils.fsm :as fsm]
            [com.yetanalytics.template-validation :as tv]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Object maps
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
  [{:keys [patterns templates]}]
  (merge (mapify-patterns patterns) (mapify-templates templates)))

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
                      (:sequence branch)
                      [(_ :guard #(contains? % :alternates))]
                      (:alternates branch)
                      [(_ :guard #(contains? % :optional))]
                      (:optional branch)
                      [(_ :guard #(contains? % :oneOrMore))]
                      (:oneOrMore branch)
                      [(_ :guard #(contains? % :zeroOrMore))]
                      (:zeroOrMore branch)))
          (make-node [_ _] (constantly nil))]
    (zip/zipper branch? children make-node pattern)))

(defn update-children
  "Given a location in a pattern zipper and a table of objects (Templates and 
  Patterns), replace the identifiers with their respective objects (either
  a Template map, a Pattern, or an array of Patterns)."
  [pattern-loc objects-map]
  (letfn [(extract-vec [node k] (mapv (partial get objects-map) (get node k)))
          (extract-map [node k] (->> node k :id (get objects-map)))]
    (if (zip/branch? pattern-loc)
      (zip/edit pattern-loc
                (fn [node]
                  (m/match [node]
                    [(_ :guard #(contains? % :sequence))]
                    (assoc node :sequence (extract-vec node :sequence))
                    [(_ :guard #(contains? % :alternates))]
                    (assoc node :alternates (extract-vec node :alternates))
                    [(_ :guard #(contains? % :optional))]
                    (assoc node :optional (extract-map node :optional))
                    [(_ :guard #(contains? % :oneOrMore))]
                    (assoc node :oneOrMore (extract-map node :oneOrMore))
                    [(_ :guard #(contains? % :zeroOrMore))]
                    (assoc node :zeroOrMore (extract-map node :zerOrMore))
                    :else node)))
      ;; Can't change children if they dont' exist  
      pattern-loc)))

(defn grow-pattern-tree
  "Build a tree data structure out of a Pattern using zippers. Each internal
  node is a Pattern and each leaf node is a Statement Template."
  [pattern objects-map]
  (loop [pattern-loc (zip-pattern pattern)]
    (if (zip/end? pattern-loc)
      (zip/node pattern-loc) ;; Return the root
      (let [new-loc (map-children pattern-loc objects-map)]
        (recur (zip/next new-loc))))))

(defn build-node-fsm
  "Given a node in the Pattern tree, return the corresponding FSM.
  The FSM built at this node is a composition of FSMs built from the child
  nodes."
  [node]
  (m/match [node]
    [{:type "Pattern" :sequence _}] (fsm/sequence-fsm (:sequence node))
    [{:type "Pattern" :alternates _}] (fsm/alternates-fsm (:alternates node))
    [{:type "Pattern" :optional _}] (fsm/optional-fsm  (:optional node))
    [{:type "Pattern" :zeroOrMore _}] (fsm/zero-or-more-fsm (:zeroOrMore node))
    [{:type "Pattern" :oneOrMore _}] (fsm/one-or-more-fsm (:oneOrMore node))
    [{:type "StatementTemplate"}]
    (fsm/transition-fsm (:id node) (partial tv/validate-statement-2 node))
    ;; Node is not a map
    :else node))

(defn mechanize-pattern
  "Turn a Pattern tree data structure into an FSM using a post-order DFS tree
  traversal."
  [pattern-tree]
  (w/postwalk build-node-fsm pattern-tree))
