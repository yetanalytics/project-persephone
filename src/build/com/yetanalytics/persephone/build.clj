(ns com.yetanalytics.persephone.build
  (:require [clojure.tools.build.api :as b]))

(def source-dirs ["src/main" "src/cli"])

(def class-dir "target/classes")

(def basis
  (b/create-basis {:project "deps.edn"
                   :aliases [:cli]}))

(defn- uber-file [jar-name]
  (format "target/bundle/%s.jar" jar-name))

(defn- main-ns [jar-name]
  (case jar-name
    "validate" 'com.yetanalytics.persephone.cli.validate
    "match"    'com.yetanalytics.persephone.cli.match))

(defn- validate-jar-name
  [jar]
  (let [jar-name (name jar)]
    (if (#{"match" "validate"} jar-name)
      jar-name
      (throw (IllegalArgumentException.
              (format "Invalid `:jar` argument: %s" jar))))))

(defn uber
  "Build a new JAR file at `target/bundle`. Takes one keyword arg: `:jar`,
   which can a keyword or symbol that is one of:
   | Keyword Arg | Description
   | ---         | ---
   | `:validate` | Create `validate.jar` to use as the `validate` CLI command.
   | `:match`    | Create `match.jar` to use as the `match` CLI command."
  [{:keys [jar]}]
  (let [jar-name (validate-jar-name jar)]
    (b/copy-dir
     {:src-dirs   source-dirs
      :target-dir class-dir})
    (b/compile-clj
     {:basis     basis
      :src-dirs  source-dirs
      :class-dir class-dir})
    (b/uber
     {:basis     basis
      :class-dir class-dir
      :uber-file (uber-file jar-name)
      :main      (main-ns jar-name)})))
