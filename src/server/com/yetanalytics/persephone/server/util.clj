(ns com.yetanalytics.persephone.server.util
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli  :as cli]
            [xapi-schema.spec   :as xs]
            [com.yetanalytics.pan        :as pan]
            [com.yetanalytics.pan.axioms :as ax]
            [com.yetanalytics.persephone.utils.json :as json]))

(defn read-profile [profile-filename]
  (json/coerce-profile (slurp profile-filename)))

(defn read-statement [statement-filename]
  (json/coerce-statement (slurp statement-filename)))

(defn not-empty? [coll] (boolean (not-empty coll)))

(defn iri? [x] (s/valid? ::ax/iri x))

(def iri-err-msg "Must be a valid IRI.")

(defn profile? [p] (nil? (pan/validate-profile p)))

(defn profile-err-msg [p] (pan/validate-profile p :result :string))

(defn statement? [s] (s/valid? ::xs/statement s))

(defn statement-err-msg [s] (s/explain-str ::xs/statement s))

(defn printerr
  "Print the `err-messages` vector line-by-line to stderr."
  [err-messages]
  (binding [*out* *err*]
    (run! println err-messages))
  (flush))

(defn handle-args
  "Parse `args` based on `cli-options` (which should follow the tools.cli
   specification) and either return `:error`, print `--help` command and
   return `:help`, or return the parsed `options` map."
  [args cli-options]
  (let [{:keys [options summary errors]}
        (cli/parse-opts args cli-options)
        {:keys [help]}
        options]
    (cond
      ;; Display help menu and exit
      help
      (do (println summary)
          :help)
      ;; Display error message and exit
      (not-empty errors)
      (do (printerr errors)
          :error)
      ;; Do the things
      :else
      options)))
