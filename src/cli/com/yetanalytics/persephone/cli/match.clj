(ns com.yetanalytics.persephone.cli.match
  (:require [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.cli.util.args :as a]
            [com.yetanalytics.persephone.cli.util.file :as f]
            [com.yetanalytics.persephone.cli.util.spec :as s])
  (:gen-class))

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
    "IDs of Patterns to match against; can specify zero or more."
    :id        :pattern-ids
    :multi     true
    :validate  [s/iri? s/iri-err-msg]
    :update-fn (fnil conj [])]
   ["-s" "--statement URI"
    "Statement filepath/location; must specify one or more."
    :id        :statements
    :missing   "No Statements specified."
    :multi     true
    :parse-fn  f/read-statement
    :validate  [s/statement? s/statement-err-msg]
    :update-fn (fnil conj [])]
   ["-n" "--compile-nfa"
    (str "If set, compiles the Patterns into a non-deterministic finite "
         "automaton (NFA) instead of a deterministic one, allowing for "
         "more detailed error traces at the cost of decreased performance.")
    :id :compile-nfa]
   ["-h" "--help" "Display the help menu."]])

(defn- match
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
                      ;; TODO: This really shouldn't be meta...
                      (-> state-m meta :failure))))))

(defn -main [& args]
  (if (match (a/handle-args args match-statements-options))
    (System/exit 0)
    (System/exit 1)))

(comment
  (match
   (a/handle-args
    '("-p"
      "test-resources/sample_profiles/cmi5.json"
      "-s"
      "sample-statement.json")
    match-statements-options)))
