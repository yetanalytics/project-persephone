(ns com.yetanalytics.persephone.server.match
  (:require [clojure.tools.cli :as cli]
            [com.yetanalytics.persephone :as per]
            [com.yetanalytics.persephone.utils.cli :as u])
  (:import [clojure.lang ExceptionInfo]))

;; TODO: There is no --compile-nfa flag since there is no trace printing, and
;; the trace is not present in the match response.

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
    (str "IDs of primary Patterns to match against; can specify zero or more. "
         "Filters out all Patterns that are not included.")
    :id        :pattern-ids
    :multi     true
    :validate  [u/iri? u/iri-err-msg]
    :update-fn u/conj-argv]
   ["-s" "--persist-state"
    "If the current state of the pattern FSM should be retained."
    :id :persist-state]
   ["-h" "--help" "Display the 'match' subcommand help menu."]])

(defonce state-ref
  (atom {:matchers nil
         :state    nil
         :persist? false}))

(defn compile-patterns!
  "Parse `arglist`, compile Profiles into FSMs, and store them in-memory.
   Also set the initial state and state persistence mode.
   Return either `:help`, `:error`, or `true`."
  [arglist]
  (let [parsed  (cli/parse-opts arglist match-statements-options)
        options (u/handle-parsed-args parsed)]
    (if (keyword? options)
      options
      (try (let [{:keys [profiles pattern-ids compile-nfa persist-state]}
                 options
                 matchers
                 (per/compile-profiles->fsms
                  profiles
                  :compile-nfa? compile-nfa
                  :selected-patterns (not-empty pattern-ids))]
             (swap! state-ref assoc
                    :matchers matchers
                    :state    nil
                    :persist? (boolean persist-state))
             true)
           (catch ExceptionInfo e
             (u/print-assert-errors e)
             :error)))))

(defn match
  "Perform validation on `statements` against the FSM matchers that
   are stored in-memory in the webserver."
  [statements]
  (let [{:keys [matchers persist? state]} @state-ref
        state* (per/match-statement-batch matchers state statements)]
    (when persist?
      (swap! state-ref assoc :state state*))
    state*))
