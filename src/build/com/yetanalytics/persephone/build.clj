(ns com.yetanalytics.persephone.build
  (:require [clojure.tools.build.api :as b]))

(def valid-jar-names #{"cli" "server"})

(defn- source-directories [jar-name]
  ["src/main" (format "src/%s" jar-name)])

(defn- class-directory [jar-name]
  (format "target/classes/%s/" jar-name))

(defn- project-basis [jar-name]
  (b/create-basis {:project "deps.edn"
                   :aliases [(keyword jar-name)]}))

(defn- uberjar-file [jar-name]
  (format "target/bundle/%s.jar" jar-name))

(defn- main-namespace [jar-name]
  (symbol (format "com.yetanalytics.persephone.%s" jar-name)))

(defn- validate-jar-name
  "Return `jar` as a string if valid, throw otherwise."
  [jar]
  (let [jar-name (name jar)]
    (if (valid-jar-names jar-name)
      jar-name
      (throw (IllegalArgumentException.
              (format "Invalid `:jar` argument: %s" jar))))))

(defn uber
  "Build a new JAR file at `target/bundle`. Takes one keyword arg: `:jar`,
   which can a keyword or symbol that is one of:
   
   | Keyword Arg | Description
   | ---         | ---
   | `:cli`      | Create `cli.jar` for the command line interface.
   | `:server`   | Create `server.jar` for the webserver."
  [{:keys [jar]}]
  (let [jar-name  (validate-jar-name jar)
        src-dirs  (source-directories jar-name)
        class-dir (class-directory jar-name)
        basis     (project-basis jar-name)
        uber-file (uberjar-file jar-name)
        main-ns   (main-namespace jar-name)]
    (b/copy-dir
     {:src-dirs   src-dirs
      :target-dir class-dir})
    (b/compile-clj
     {:basis     basis
      :src-dirs  src-dirs
      :class-dir class-dir})
    (b/uber
     {:basis     basis
      :class-dir class-dir
      :uber-file uber-file
      :main      main-ns})))
