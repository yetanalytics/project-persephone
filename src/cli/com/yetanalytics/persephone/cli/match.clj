(ns com.yetanalytics.persephone.cli.match
  (:require [clojure.tools.cli :as cli]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.utils.cli :as u])
  (:import [clojure.lang ExceptionInfo]))

(def match-statements-options
  [["-p" "--profile URI"
    "Profile filepath/location; must specify one or more."
    :id        :profiles
    :missing   "No Profiles specified."
    :multi     true
    :parse-fn  u/read-profile
    :validate  [u/profile? u/profile-err-msg]
    :update-fn u/conj-argv]
   ["-i" "--pattern-id IRI"
    "IDs of primary Patterns to match against; can specify zero or more. Filters out all Patterns that are not included."
    :id        :pattern-ids
    :multi     true
    :validate  [u/iri? u/iri-err-msg]
    :update-fn u/conj-argv]
   ["-s" "--statement URI"
    "Statement filepath/location; must specify one or more. Accepts arrays of Statements."
    :id        :statements
    :missing   "No Statements specified."
    :multi     true
    :parse-fn  u/read-statement
    :validate  [u/statements? u/statements-err-msg]
    :update-fn u/conj-argv-or-array]
   ["-n" "--compile-nfa"
    (str "If set, compiles the Patterns into a non-deterministic finite "
         "automaton (NFA) instead of a deterministic one, allowing for "
         "more detailed error traces at the cost of decreased performance.")
    :id :compile-nfa]
   ["-h" "--help" "Display the 'match' subcommand help menu."]])

(defn- match*
  "Perform Pattern matching on `statements` based on the options map; print
   match failures or errors and return `false` if errors or failures exist,
   `true` otherwise."
  [{:keys [profiles pattern-ids statements compile-nfa]}]
  (try
    (let [compiled (per/compile-profiles->fsms
                    profiles
                    :compile-nfa?       compile-nfa
                    :selected-patterns  (not-empty pattern-ids))
          state-m  (per/match-statement-batch compiled
                                              nil
                                              statements
                                              :print? true)]
      (not (boolean (or (-> state-m :error)
                        (-> state-m :rejects not-empty)))))
    (catch ExceptionInfo e
      (u/print-assert-errors e)
      false)))

(defn match
  "Perform Pattern matching based on `arglist`; print match failures or errors
   and return `false` if errors or failures exist, `true` if match passes
   or if the `--help` argument was present."
  [arglist]
  (let [parsed  (cli/parse-opts arglist match-statements-options)
        options (u/handle-parsed-args parsed)]
    (cond
      (= :help options)  true
      (= :error options) false
      :else (match* options))))
