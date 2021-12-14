(ns com.yetanalytics.persephone.utils.statement
  (:require [clojure.spec.alpha :as s]
            [clojure.set        :as cset]
            [clojure.walk       :as w]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.pan.objects.profile :as pan-prof]
            [com.yetanalytics.persephone.utils.time :as time]))

(def subreg-iri
  "https://w3id.org/xapi/profiles/extensions/subregistration")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef compare-statements-by-timestamp
  :args (s/cat :s1 ::xs/statement :s2 ::xs/statement)
  :ret int?)

(defn compare-statements-by-timestamp
  "Compare Statements `s1` and `s2` by their timestamp values."
  [s1 s2]
  (let [t1 (get s1 "timestamp")
        t2 (get s2 "timestamp")]
    (time/compare-timestamps t1 t2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Profile IDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef get-statement-profile-ids
  :args (s/cat :statement ::xs/statement
               :profile-id-set (s/every ::pan-prof/id :kind set?))
  :ret (s/or :error #{::missing-profile-reference}
             :ok (s/every ::pan-prof/id :kind set?)))

(defn get-statement-profile-ids
  "Get the category context activity IDs that are also profile IDs, or
   return `::missing-profile-reference` if none are."
  [statement profile-id-set]
  (let [cat-acts (get-in statement ["context" "contextActivities" "category"])
        cat-ids  (cset/intersection (set (map #(get % "id") cat-acts))
                                    profile-id-set)]
    (if-not (empty? cat-ids)
      cat-ids
      ::missing-profile-reference)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Registration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::registration-id ::xs/uuid)

(s/def ::registration
  (s/or :no-registration #{:no-registration}
        :registration ::registration-id))

(s/fdef get-statement-registration
  :args (s/cat ::statement ::xs/statement)
  :ret ::registration)

(defn get-statement-registration
  "Return the registration value from `statement`, or the keyword
   `:no-registration` if not present."
  [statement]
  (get-in statement ["context" "registration"] :no-registration))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Subregistration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::profile ::pan-prof/id)
(s/def ::subregistration ::xs/uuid)

(def subregistration-spec
  (s/every (s/and (s/conformer w/keywordize-keys)
                  (s/keys :req-un [::profile
                                   ::subregistration]))
           :min-count 1))

(s/fdef get-statement-subregistration
  :args (s/cat :statement ::xs/statement
               :registration ::registration)
  :ret subregistration-spec)

(defn get-statement-subregistration
  "Given `statement` and `registration`, return the subregistration
   extension value (a coll of subreg objects), or a keyword if the
   subregistration value is invalid."
  [statement registration]
  (let [subreg-ext (get-in statement ["context" "extensions" subreg-iri])]
    (when (some? subreg-ext)
      (cond
        ;; Subregistrations present without registration
        (= :no-registration registration)
        ::invalid-subreg-no-registration
        ;; Subregistration extension is an empty array or has entries
        ;; with missing keys
        (not (s/valid? subregistration-spec subreg-ext))
        ::invalid-subreg-nonconformant
        ;; Valid!
        :else
        subreg-ext))))

(s/fdef get-subregistration-id
  :args (s/cat :profile-id ::pan-prof/id
               :subreg-obj subregistration-spec)
  :ret (s/nilable ::subregistration))

(defn get-subregistration-id
  "Given `profile-id` and `subreg-objs` that is a coll of subregistration
   objects (with keys `profile` and `subregistration`), return the
   subregistration UUID corresponding to `profile-id` or `nil` if not found."
  [profile-id subreg-objs]
  (let [subreg-pred (fn [{:strs [profile subregistration]}]
                      (when (= profile-id profile)
                        subregistration))]
    (some subreg-pred subreg-objs)))
