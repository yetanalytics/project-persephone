(ns com.yetanalytics.persephone.pattern.failure
  "Util namespace to construct the failure map for pattern matching, with
   included specs."
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan.objects.template  :as pan-template]
            [com.yetanalytics.pan.objects.pattern   :as pan-pattern]
            [com.yetanalytics.persephone.pattern    :as p]
            [com.yetanalytics.persephone.utils.spec :refer [lazy-seq?]]))

;; Specs

(s/def ::statement :statement/id)
(s/def ::pattern ::pan-pattern/id)

;; The templates that were visited during matching
(s/def ::templates
  (s/coll-of ::pan-template/id :kind vector?))

;; The different paths of patterns that were taken during matching
(s/def ::patterns
  (s/coll-of (s/coll-of ::pan-pattern/id :kind vector?) :kind lazy-seq?))

;; Trace info - all templates visited + all pattern paths taken
(s/def ::traces
  (s/coll-of (s/keys :req-un [::templates ::patterns])
             :kind lazy-seq?))

;; Basic info (statement + pattern IDs) and optional trace info
(s/def ::failure
  (s/keys :req-un [::statement ::pattern]
          :opt-un [::traces]))

;; Functions

(defn construct-failure-info
  "Construct the failure map given `fsms`, `state-info`, and `statement`,
   where the failure map is has `:statement`, `:pattern`, and optional
   `:traces` keys."
  [{pat-id    :id
    ?pat-nfa  :nfa
    ?nfa-meta :nfa-meta
    :as       _fsms}
   state-info
   {stmt-id "id" :as _statement}]
  (if ?pat-nfa
    ;; Use the pattern NFA to build the traces data structure
    (let [read-template-ids
          (if ?nfa-meta
            (partial p/read-visited-templates ?pat-nfa ?nfa-meta)
            (partial p/read-visited-templates ?pat-nfa))
          build-trace
          (fn [temp-ids]
            {:templates temp-ids
             :patterns  (read-template-ids temp-ids)})
          fail-traces
          (->> state-info
               (map :visited)
               (map build-trace))]
      {:statement stmt-id
       :pattern   pat-id
       :traces    fail-traces})
    ;; No pattern NFA available - just return basic info
    {:statement stmt-id
     :pattern   pat-id}))
