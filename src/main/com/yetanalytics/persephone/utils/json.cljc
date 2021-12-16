(ns com.yetanalytics.persephone.utils.json
  (:require [com.yetanalytics.pathetic :as pathetic]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSONPath operations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Parsing is an expensive operation, and many JSONPath strings are repeated in
;; a given Profile, so we call `memoize` to cache already parsed paths.

(def parse-jsonpath
  "Parse a single arg `path-str` and return a vector of parsed
   JSONPaths."
  (memoize pathetic/parse-paths))

;; Wrapper for pathetic/get-values*
;; We don't use `memoize` here because `json` Statements are usually different.

(def opts-map {:return-missing? true})

(defn get-jsonpath-values
  "Given `json` and parsed JSONPaths `paths`, return a vector of JSON
   valuess."
  [json paths]
  (pathetic/get-values* json paths opts-map))
