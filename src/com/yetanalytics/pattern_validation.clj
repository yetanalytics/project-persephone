(ns com.yetanalytics.pattern-validation
  (:require [clojure.set :as cset]
            [ubergraph.core :as uber]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Lorem Ipsum
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
