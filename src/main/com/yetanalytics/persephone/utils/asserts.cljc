(ns com.yetanalytics.persephone.utils.asserts
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan :as pan]
            [com.yetanalytics.pan.objects.template :as pan-template]))

;; FIXME: We need to set the :relation? key to true, but currently this will
;; cause errors because external IRIs are not supported yet in project-pan.

(defn assert-profile
  "Assert that `profile` conforms to the xAPI spec, or else throw an
   exception."
  [profile]
  (when-some [err (pan/validate-profile profile :ids? true :print-errs? false)]
    (throw (ex-info "Invalid Profile!"
                    {:kind   ::invalid-profile
                     :errors err}))))

(defn assert-template
  "Assert that `template` conforms to the xAPI spec, or else throw an
   exception."
  [template]
  (when-some [err (s/explain-data ::pan-template/template template)]
    (throw (ex-info "Invalid Statement Template!"
                    {:kind   ::invalid-template
                     :errors err}))))

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
