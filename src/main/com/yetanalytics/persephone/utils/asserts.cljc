(ns com.yetanalytics.persephone.utils.asserts
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.template :as pan-template]))

;; FIXME: We need to set the :relation? key to true, but currently this will
;; cause errors because external IRIs are not supported yet in project-pan.

(defn assert-profile
  "Assert that `profile` conforms to the xAPI spec, or else throw an
   exception. The ex-data contains an `:errors` map that is the return
   value of `pan/validate-profile`."
  [profile]
  (when-some [err (pan/validate-profile profile :ids? true :print-errs? false)]
    (throw (ex-info "Invalid Profile!"
                    {:kind   ::invalid-profile
                     :errors err}))))

(defn assert-template
  "Assert that `template` conforms to the xAPI spec, or else throw an
   exception. The ex-data contains an `:errors` map that is the `s/explain-data`
   result of `:pan.objects.template/template`."
  [template]
  (when-some [err (s/explain-data ::pan-template/template template)]
    (throw (ex-info "Invalid Statement Template!"
                    {:kind   ::invalid-template
                     :errors err}))))

;; Emptiness checks

(defn assert-not-empty-templates
  "Assert that `compiled-templates` has at least one Template."
  [compiled-templates]
  (when (empty? compiled-templates)
    (throw (ex-info "No Templates present after compilation."
                    {:kind ::no-templates}))))

(defn assert-not-empty-patterns
  "Assert that `compiled-patterns` has at least one Pattern for each Profile,
   and that at least one Profile exists."
  [compiled-patterns]
  ;; Check every `(get-in compiled-patterns [profile-version pattern-id])`
  ;; is an empty map
  (when (empty? compiled-patterns)
    (throw (ex-info "No Profiles or Patterns present after compilation."
                    {:kind ::no-patterns})))
  (when-some [[prof-id _]
              (->> compiled-patterns
                   (into [])
                   (some (fn [[_ pat-id-m :as prof-pair]]
                           (when (empty? pat-id-m) prof-pair))))]
    (throw (ex-info "No Patterns present for a Profile after compilation."
                    {:kind       ::no-patterns
                     :profile-id prof-id}))))

;; TODO: Make these asserts Project Pan's responsibility

(defn assert-profile-ids
  "Assert that the Profile IDs do not clash, or else throw an exception."
  [profiles]
  (let [prof-ids (map :id profiles)]
    (when (not= prof-ids (distinct prof-ids))
      (throw (ex-info "Profile IDs are not unique!"
                      {:type ::non-unique-profile-ids
                       :ids  prof-ids})))))

(defn assert-profile-template-ids
  "Assert that the Profiles' Template IDs do not clash,
   or else throw an exception."
  [profiles]
  (let [temp-ids (mapcat (fn [{:keys [templates]}] (map :id templates))
                         profiles)]
    (when (not= temp-ids (distinct temp-ids))
      (throw (ex-info "Template IDs are not unique!"
                      {:type ::non-unique-template-ids
                       :ids  temp-ids})))))

(defn assert-profile-pattern-ids
  "Assert that the Profiles' Pattern IDs do not clash,
   or else throw an exception."
  [profiles]
  (let [pat-ids (mapcat (fn [{:keys [patterns]}] (map :id patterns))
                        profiles)]
    (when (not= pat-ids (distinct pat-ids))
      (throw (ex-info "Pattern IDs are not unique!"
                      {:type ::non-unique-pattern-ids
                       :ids  pat-ids})))))

(defn assert-template-ids
  "Assert that the Template IDs do not clash, or else throw an exception."
  [templates]
  (let [temp-ids (map :id templates)]
    (when (not= temp-ids (distinct temp-ids))
      (throw (ex-info "Template IDs are not unique!"
                      {:type ::non-unique-template-ids
                       :ids  temp-ids})))))
