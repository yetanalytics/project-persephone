(ns com.yetanalytics.persephone.utils.maps)

(defn mapify-coll
  "Given a `coll` containing IDs, turn it into an ID-to-object
   map. If :string? is true, use `\"id\"` as the ID key; otherwise
   use `:id`."
  [coll & {:keys [string?]}]
  (if string?
    (zipmap (mapv #(get % "id") coll) coll)
    (zipmap (mapv :id coll) coll)))
