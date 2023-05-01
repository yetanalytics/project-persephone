(ns com.yetanalytics.persephone.cli
  (:require [clojure.tools.cli :as cli]
            [com.yetanalytics.persephone.utils.cli :as u]
            [com.yetanalytics.persephone.cli.match     :as m]
            [com.yetanalytics.persephone.cli.validate  :as v])
  (:gen-class))

(def top-level-options
  [["-h" "--help" "Display the top-level help guide."]])

(def top-level-summary
  (str "Usage: 'persephone <subcommand> <args>' or 'persephone [-h|--help]'\n"
       "\n"
       "where the subcommand can be one of the following:\n"
       "  validate  Perform Statement Template validation against a Statement\n"
       "  match     Perform Pattern matching against one or more Statements\n"
       "\n"
       "Run 'persephone <subcommand> --help' for details on each subcommand"))

(defn -main [& args]
  (let [{:keys [options summary arguments]}
        (cli/parse-opts args top-level-options
                        :in-order true
                        :summary-fn (fn [_] top-level-summary))
        [subcommand & rest]
        arguments]
    (cond
      (:help options)
      (do
        (println summary)
        (System/exit 0))
      (= "validate" subcommand)
      (if (v/validate rest)
        (System/exit 0)
        (System/exit 1))
      (= "match" subcommand)
      (if (m/match rest)
        (System/exit 0)
        (System/exit 1))
      :else
      (do
        (u/printerr (format "Unknown subcommand: %s" subcommand))
        (System/exit 1)))))
