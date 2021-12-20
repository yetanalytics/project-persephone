(ns com.yetanalytics.persephone.template.statement-ref
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.pan.objects.template     :as pan-template]
            [com.yetanalytics.persephone.utils.asserts :as assert]
            [com.yetanalytics.persephone.utils.maps    :as maps]))

;; TODO: Move some sref-related functions from `template` ns to here?

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ::pan-template/id doesn't have a generator
(s/def ::get-template-fn
  (s/fspec :args (s/cat :template-id ::xs/iri)
           :ret (s/nilable ::pan-template/template)))

(s/def ::get-statement-fn
  (s/fspec :args (s/cat :statement-id :statement/id)
           :ret (s/nilable ::xs/statement)))

(s/def ::statement-ref-fns
  (s/keys :req-un [::get-template-fn
                   ::get-statement-fn]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Convenience Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn profile->id-template-map
  "Takes `profile` and returns a map between Statement Template IDs
   and the Templates themselves. Used for Statement Ref Template
   resolution.
   
   :validate-profile? is default true. If true, `profile->validator`
   checks that `profile` conforms to the xAPI Profile spec."
  [profile & {:keys [validate-profile?] :or {validate-profile? true}}]
  (when validate-profile? (assert/assert-profile profile))
  (maps/mapify-coll (:templates profile)))

;; Doesn't exactly conform to stmt batch requirements since in theory,
;; a statement ref ID can refer to a FUTURE statement in the batch.
;; However, this shouldn't really matter in practice.
(defn statement-batch->id-statement-map
  "Takes the coll `statement-batch` and returns a map between
   Statement IDs and the Statement themselves. Used for Statement
   Ref resolution, particularly in statement batch matching."
  [statement-batch]
  (maps/mapify-coll statement-batch :string? true))
