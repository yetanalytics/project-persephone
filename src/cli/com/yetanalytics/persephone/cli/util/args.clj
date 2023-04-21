(ns com.yetanalytics.persephone.cli.util.args
  (:require [clojure.tools.cli :as cli]))

(defn- exit-on-error!
  "Print `errors` to stderr and exit with system code 1."
  [errors]
  (binding [*out* *err*]
    (run! println errors))
  (flush)
  (System/exit 1))

(defn handle-args
  "Parse `args` based on `cli-options` (which should follow the tools.cli
   specification) and either exit on error, exit on `--help` command, or
   return the parsed `options` map."
  [args cli-options]
  (let [{:keys [options summary errors]}
        (cli/parse-opts args cli-options)
        {:keys [help]}
        options]
    (cond
      ;; Display help menu and exit
      help
      (println summary)
      ;; Display error message and exit
      (not-empty errors)
      (exit-on-error! errors)
      ;; Do the things
      :else
      options)))
