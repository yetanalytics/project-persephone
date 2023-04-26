(ns com.yetanalytics.persephone.cli.match
  (:require [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.cli.util.args :as a]
            [com.yetanalytics.persephone.cli.util.file :as f]
            [com.yetanalytics.persephone.cli.util.spec :as s]))

(def match-statements-options
  [["-p" "--profile URI"
    "Profile filepath/location; must specify one or more."
    :id        :profiles
    :missing   "No Profiles specified."
    :multi     true
    :parse-fn  f/read-profile
    :validate  [s/profile? s/profile-err-msg]
    :update-fn (fnil conj [])]
   ["-i" "--pattern-id IRI"
    "IDs of primary Patterns to match against; can specify zero or more. Filters out all Patterns that are not included."
    :id        :pattern-ids
    :multi     true
    :validate  [s/iri? s/iri-err-msg]
    :update-fn (fnil conj [])]
   ["-s" "--statement URI"
    "Statement filepath/location; must specify one or more. Accepts arrays of Statements."
    :id        :statements
    :missing   "No Statements specified."
    :multi     true
    :parse-fn  f/read-statement
    :validate  [s/statements? s/statements-err-msg]
    :update-fn (fn [xs s]
                 (let [xs (or xs [])]
                   (if (vector? s) (into xs s) (conj xs s))))]
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
  (let [compiled (per/compile-profiles->fsms
                  profiles
                  :validate-profiles? false
                  :compile-nfa?       compile-nfa
                  :selected-patterns  (not-empty pattern-ids))
        state-m  (per/match-statement-batch compiled
                                            nil
                                            statements
                                            :print? true)]
    (not (boolean (or (-> state-m :error)
                      (-> state-m :rejects not-empty))))))

(defn match
  "Perform Pattern matching based on `arglist`; print match failures or errors
   and return `false` if errors or failures exist, `true` if match passes
   or if the `--help` argument was present."
  [arglist]
  (let [options (a/handle-args arglist match-statements-options)]
    (cond
      (= :help options)  true
      (= :error options) false
      :else (match* options))))
