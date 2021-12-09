(ns com.yetanalytics.persephone.utils.profile
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.pan.objects.profile :as pan-profile]
            [com.yetanalytics.pan.objects.profiles.versions :as pan-versions]
            [com.yetanalytics.persephone.utils.time :as time]))

;; TODO: Move other util functions like `pat/mapify-all` or
;; `pat/primary-patterns` to this namespace.

(s/fdef latest-version
  :args (s/cat :profile ::pan-profile/profile)
  :ret ::pan-versions/version)

;; O(n) search instead of O(n log n) sorting.
(defn latest-version
  "Get the most recent version object of `profile`."
  [profile]
  (let [{:keys [versions]} profile]
    (reduce (fn [{latest-ts :generatedAtTime :as latest-ver}
                 {newest-ts :generatedAtTime :as newest-ver}]
              (if (neg-int? (time/compare-timestamps latest-ts newest-ts))
                newest-ver   ; latest-ts occured before newest-ts
                latest-ver))
            (first versions) ; okay since empty arrays are banned by the spec
            versions)))
