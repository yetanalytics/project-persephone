(ns com.yetanalytics.persephone.utils.fsm-viz
  (:require [com.yetanalytics.persephone.utils.fsm :refer :all]
            [dorothy.core :as dorothy]
            [dorothy.jvm :as djvm]))

(defn fsm->graphviz
  "Convert a FSM to a format readable by the Dorothy graphviz library."
  [fsm]
  (let
   [nodes (reduce
           (fn [accum state]
             (conj accum
                   [(str state)
                    {:shape       :circle
                     :label       (str state)
                     :peripheries (if (contains? (:accepts fsm) state) 2 1)}]))
           []
           (:states fsm))
    edges (reduce-kv
           (fn [accum src trans]
             (concat accum
                     (reduce-kv
                      (fn [accum symb dests]
                        (case
                         (:type fsm)
                          :nfa
                          (concat accum
                                  (reduce
                                   (fn [accum dest]
                                     (conj accum [(str src)
                                                  (str dest)
                                                  {:label symb}]))
                                   []
                                   dests))
                          :dfa
                          (conj accum [(str src) (str dests) {:label symb}])))
                      []
                      trans)))
           []
           (:transitions fsm))]
    (dorothy/digraph (concat edges nodes))))

(defn show-fsm
  "Show a FSM as an image."
  [fsm]
  (-> fsm fsm->graphviz dorothy/dot djvm/show!))

(defn save-fsm
  "Save a FSM to an output file with the specified format."
  [fsm output-str format]
  (-> fsm fsm->graphviz dorothy/dot (djvm/save! output-str {:format format})))
