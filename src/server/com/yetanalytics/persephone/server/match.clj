(ns com.yetanalytics.persephone.server.match
  (:require [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.server.util :as u]))

(def match-statements-options
  [["-p" "--profile URI"
    "Profile filepath/location; must specify one or more."
    :id        :profiles
    :missing   "No Profiles specified."
    :multi     true
    :parse-fn  u/read-profile
    :validate  [u/profile? u/profile-err-msg]
    :update-fn (fnil conj [])]
   ["-i" "--pattern-id IRI"
    (str "IDs of primary Patterns to match against; can specify zero or more. "
         "Filters out all Patterns that are not included.")
    :id        :pattern-ids
    :multi     true
    :validate  [u/iri? u/iri-err-msg]
    :update-fn (fnil conj [])]
   ["-n" "--compile-nfa"
    (str "If set, compiles the Patterns into a non-deterministic finite "
         "automaton (NFA) instead of a deterministic one, allowing for "
         "more detailed error traces at the cost of decreased performance.")
    :id :compile-nfa]
   ["-h" "--help" "Display the 'match' subcommand help menu."]])

(defonce match-ref
  (atom {:matchers nil}))

(defn compile-patterns!
  "Parse `arglist`, compile Profiles into FSMs, and store them in-memory."
  [arglist]
  (let [options (u/handle-args arglist match-statements-options)]
    (if (keyword? options)
      options
      (let [{:keys [profiles pattern-ids compile-nfa]}
            options
            matchers
            (per/compile-profiles->fsms
             profiles
             :validate-profiles? false
             :compile-nfa? compile-nfa
             :selected-patterns (not-empty pattern-ids))]
        (swap! match-ref assoc :matchers matchers)
        nil))))

(defn match
  "Perform validation on `statements` against the FSM matchers that
   are stored in-memory in the webserver."
  [statements]
  (let [{:keys [matchers]} @match-ref]
    (per/match-statement-batch matchers nil statements)))
