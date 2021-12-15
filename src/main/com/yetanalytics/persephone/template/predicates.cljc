(ns com.yetanalytics.persephone.template.predicates
  (:require [clojure.set :as cset])
  #?(:cljs (:require-macros
            [com.yetanalytics.persephone.template.predicates
             :refer [wrap-pred and-wrapped or-wrapped add-wrapped]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Predicate macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; We use these macros in order to identify the name of the predicate that
;; returns false for a some series of nested predicates.

#?(:clj
   (defmacro wrap-pred
     "Wrap a predicate function f such that if f returns true,
      return nil, and if f returns false, return the keywordized
      name of f."
     [f]
     (assert (symbol? f))
     (let [pred-name# (keyword f)]
       `(fn [x#] (if (~f x#) nil ~pred-name#)))))

#?(:clj
   (defmacro and-wrapped
     "Given two functions wrapped using wrap-pred, return nil
      if both functions return true and the corresponding
      keywordized fn name for the function that returns false.
      Short circuiting."
     [f1 f2]
     `(fn [x#]
        (if-let [res1# (~f1 x#)]
          res1#
          (when-let [res2# (~f2 x#)]
            res2#)))))

#?(:clj
   (defmacro or-wrapped
     "Given two functions wrapped using wrap-pred, return nil
      if either function returns true and the keywordized fn
      name for the first function if both return false."
     [f1 f2]
     `(fn [x#]
        (when-let [res1# (~f1 x#)]
          (when-let [res2# (~f2 x#)]
            res1#)))))

#?(:clj
   (defmacro add-wrapped
     "Given a function wrapped using wrap-pred or add-wrapped,
      add a 2-ary predicate f only if the values are not nil."
     [wrapped-fns f values]
     `(if (some? ~values)
        ~(let [pred-name# (keyword f)]
           `(fn [x#]
              (if-let [res# (~wrapped-fns x#)]
                res#
                (if (~f ~values x#) nil ~pred-name#))))
        ~wrapped-fns)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic predicates 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; A value is considered matchable if it's not nil (we presume that the JSONPath
;; string will return matchable values as non-nil values). In other words, nil
;; means unmatchable value.

(defn all-matchable?
  "Returns true iff every value in `coll` is matchable."
  [coll]
  (or (empty? coll)
      (every? some? coll)))

(defn none-matchable?
  "Returns true iff no value in `coll` is matchable."
  [coll]
  (->> coll (filter some?) empty?))

(defn any-matchable?
  "Returns true iff at least one matchable value in `coll` exists."
  [coll]
  (->> coll (filter some?) not-empty boolean))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rules predicates.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- presence-pred-fn
  "pulls out the functionality common to the -values? functions

  `pred-fn` should be a fn of 2 arity
  - first arg = `presence` set i.e. any | all | none
  - second arg = `values` collection"
  [presence-set values-coll pred-fn]
  (pred-fn presence-set (-> values-coll set (disj nil))))

;; If 'any' is provided, evaluated values MUST include at least one value
;; that is given by 'any'. In other words, the set of 'any' and the evaluated
;; values MUST interesect.
(defn some-any-values?
  "Return true if there's at least one value from `vals-coll` in
   `any-set`; false otherwise."
  [any-set vals-coll]
  (presence-pred-fn
   any-set
   vals-coll
   (fn [p v] (-> (cset/intersection v p) not-empty boolean))))

;; If 'all' is provided, evaluated values MUST only include values in 'all'.
;; In other words, the set of 'all' MUST be a superset of the evaluated values.
(defn only-all-values?
  "Return true is every value from `vals-coll` is in `all-set`;
   false otherwise."
  [all-set vals-coll]
  (presence-pred-fn
   all-set
   vals-coll
   (fn [p v] (cset/subset? v p))))

;; If 'none' is provided, evaluated values MUST NOT include any values in
;; 'none'. In other words, the set of 'none' and the evaluated values MUST NOT
;; intersect.
(defn no-none-values?
  "Return true if there are no values from `vals-coll` in `none-set`;
   false otherwise."
  [none-set vals-coll]
  (presence-pred-fn
   none-set
   vals-coll
   (fn [p v] (empty? (cset/intersection v p)))))

(defn every-val-present?
  "Return true if every value in `presence-set` can be found in
   `vals-coll`; false otherwise."
  [presence-set vals-coll]
  (presence-pred-fn
   presence-set
   vals-coll
   (fn [p v] (cset/superset? v p))))

;; If 'all' is provided, evaluated values MUST NOT include any unmatchable
;; values.
(defn no-unmatch-vals?
  "Return true if no unmatchable values from `vals-coll` exist;
   false otherwise."
  [_all-set vals-coll]
  (all-matchable? vals-coll))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rule spec creation.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- nset
  "'nilable set'; like `clojure.core/set` but preserves nil args."
  [coll]
  (when (some? coll) (set coll)))

;; MUST apply the 'any', 'all' and 'none' requirements if presence is missing,
;; 'included' or 'recommended' (ie. not 'excluded') and any matchable values
;; are in the statement.
;;
;; If presence is 'included', location and selector MUST include at least one
;; matchable value and MUST NOT include any unmatchable values. 
(defn create-included-pred
  "Returns a wrapped pred for when presence is 'included.'"
  [{:keys [any all none]}]
  (let [any-set  (nset any)
        all-set  (nset all)
        none-set (nset none)]
    (-> (and-wrapped (wrap-pred any-matchable?) (wrap-pred all-matchable?))
        (add-wrapped some-any-values? any-set)
        (add-wrapped only-all-values? all-set) ;; no-unmatch-vals? is redundant
        (add-wrapped no-none-values? none-set))))

;; If presence is 'excluded', location and selector MUST NOT return any values
;; (ie. MUST NOT include any matchable values). 
(defn create-excluded-pred
  "Returns a wrapped pred for when presence is 'excluded.'"
  [_]
  ;; the creation of a spec directly from a function is done using `s/spec`
  (wrap-pred none-matchable?))

;; If presence is 'recommended' or is missing, the evaluated values MUST 
;; conform to the 'any', 'all' and 'none' specs (but there are no additional
;; restrictions, i.e. it is possible not to have any matchable values).
;; 'recommended' allows profile authors to not have any/all/none in a rule.
(defn create-default-pred
  "Returns a wrapped pred for when presence is 'recommended' or is
   missing."
  [{:keys [any all none]}]
  (let [any-set  (nset any)
        all-set  (nset all)
        none-set (nset none)]
    (or-wrapped (-> (wrap-pred any-matchable?)
                    (add-wrapped some-any-values? any-set)
                    (add-wrapped only-all-values? all-set)
                    (add-wrapped no-unmatch-vals? all-set)
                    (add-wrapped no-none-values? none-set))
                (wrap-pred none-matchable?))))

(defn create-det-prop-pred
  "Returns a wrapped pred for use with Determining Properties.
   `prop-vals` contains the values of the given Property."
  [{:keys [match-vals]}]
  (let [match-vals-set (nset match-vals)]
    (-> (wrap-pred any-matchable?)
        (add-wrapped every-val-present? match-vals-set))))

;; Spec to check that the 'presence' keyword is correct.
;; A Statement Template MUST include one or more of presence, any, all or none.
;; NOTE: Should never happen with a validated Statement Template or Profile
(defn- assert-valid-rule
  [rule]
  (when-not (or (contains? rule :det-prop)
                (and (not (contains? rule :presence))
                     (or (contains? rule :any)
                         (contains? rule :all)
                         (contains? rule :none)))
                (#{"included" "excluded" "recommended"} (:presence rule)))
    (throw (ex-info "Invalid rule."
                    {:kind ::invalid-rule-syntax
                     :rule rule}))))

(defn create-rule-pred
  "Given a rule, create a predicate that will validate a Statement
   against it. Returns the name of the atomic predicate that returns
   false, or nil if all return true."
  [{:keys [presence] :as rule}]
  (assert-valid-rule rule)
  (if (contains? rule :det-prop)
    (create-det-prop-pred rule)
    (case presence
      "included"    (create-included-pred rule)
      "excluded"    (create-excluded-pred rule)
      "recommended" (create-default-pred rule)
      nil           (create-default-pred rule))))
