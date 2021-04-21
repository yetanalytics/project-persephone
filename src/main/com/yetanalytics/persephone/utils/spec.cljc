(ns com.yetanalytics.persephone.utils.spec
  "Profile-specific Statement specs (as opposed to the general specs
   of xapi-schema)."
  (:require [clojure.spec.alpha :as s]
            [clojure.walk       :as w]
            [xapi-schema.spec   :as xs]))

(s/def ::profile ::xs/iri)
(s/def ::subregistration ::xs/uuid)

(def subreg-ext-obj-spec
  (s/keys :req-un [::profile ::subregistration]))

(def subreg-ext-spec*
  (s/coll-of subreg-ext-obj-spec :kind vector? :min-count 1))

(def subreg-ext-spec
  "Same as subreg-ext-spec* but expects string keys."
  (s/and (s/conformer w/keywordize-keys w/stringify-keys)
         subreg-ext-spec*))
