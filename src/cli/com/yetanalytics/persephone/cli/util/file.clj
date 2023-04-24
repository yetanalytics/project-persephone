(ns com.yetanalytics.persephone.cli.util.file
  (:require [com.yetanalytics.persephone.utils.json :as json]))

(defn read-profile
  [profile-filename]
  (json/coerce-profile (slurp profile-filename)))

(defn read-statement
  [statement-filename]
  (json/coerce-statement (slurp statement-filename)))
