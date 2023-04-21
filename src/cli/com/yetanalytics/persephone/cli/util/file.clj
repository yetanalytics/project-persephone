(ns com.yetanalytics.persephone.cli.util.file
  (:require [clojure.java.io :as io]
            [clojure.string  :as cstr]
            [cheshire.core :as json]))

(defn- coerce-profile-key [k]
  (if (cstr/starts-with? k "@")
    (keyword (cstr/replace-first k #"@" "_"))
    (keyword k)))

(defn read-profile
  [profile-filename]
  (with-open [reader (io/reader profile-filename)]
    (let [res (json/parse-stream reader coerce-profile-key)]
      res)))

(defn read-template
  [template-filename]
  (with-open [reader (io/reader template-filename)]
    (json/parse-stream reader coerce-profile-key)))

(defn read-statement
  [statement-filename]
  (with-open [reader (io/reader statement-filename)]
    (json/parse-stream reader)))
