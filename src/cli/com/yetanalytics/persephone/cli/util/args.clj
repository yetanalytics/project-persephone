(ns com.yetanalytics.persephone.cli.util.args
  (:require [clojure.tools.cli :as cli]))

(defn printerr
  "Print the `error-messages` vector line-by-line to stderr."
  [& error-messages]
  (binding [*out* *err*]
    (run! println error-messages))
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
      (do (apply printerr errors)
          :error)
      ;; Do the things
      :else
      options)))
