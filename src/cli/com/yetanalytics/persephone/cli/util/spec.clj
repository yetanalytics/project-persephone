(ns com.yetanalytics.persephone.cli.util.spec
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.axioms :as ax]))

(defn not-empty? [coll] (boolean (not-empty coll)))

(defn iri? [x] (s/valid? ::ax/iri x))

(def iri-err-msg "Must be a valid IRI.")

(defn profile? [p] (nil? (pan/validate-profile p)))

(defn profile-err-msg [p] (pan/validate-profile p :result :string))

(defn statement? [s] (s/valid? ::xs/statement s))

(defn statement-err-msg [s] (s/explain-str ::xs/statement s))

(defn statements? [s]
  (s/or :single   (s/valid? ::xs/statement s)
        :multiple (s/valid? (s/coll-of ::xs/statement) s)))

(defn statements-err-msg [s]
  (if (vector? s)
    (s/explain-str (s/coll-of ::xs/statement) s)
    (s/explain-str ::xs/statement s)))
