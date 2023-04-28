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
    :update-fn (fnil conj [])]
   ["-i" "--pattern-id IRI"
    (str "IDs of primary Patterns to match against; can specify zero or more. "
         "Filters out all Patterns that are not included.")
    :id        :pattern-ids
    :multi     true
    :validate  [u/iri? u/iri-err-msg]
    :update-fn (fnil conj [])]
   ["-h" "--help" "Display the 'match' subcommand help menu."]])

(defonce match-ref
  (atom {:matchers nil}))

(defn compile-patterns!
  "Parse `arglist`, compile Profiles into FSMs, and store them in-memory.
   Return either `:help`, `:error`, or `nil`."
  [arglist]
  (let [parsed  (cli/parse-opts arglist match-statements-options)
        options (u/handle-parsed-args parsed)]
    (if (keyword? options)
      options
      (try (let [{:keys [profiles pattern-ids compile-nfa]}
                 options
                 matchers
                 (per/compile-profiles->fsms
                  profiles
                  :compile-nfa? compile-nfa
                  :selected-patterns (not-empty pattern-ids))]
             (swap! match-ref assoc :matchers matchers)
             true)
           (catch ExceptionInfo e
             (u/print-assert-errors e)
             :error)))))

(defn match
  "Perform validation on `statements` against the FSM matchers that
   are stored in-memory in the webserver."
  [statements]
  (let [{:keys [matchers]} @match-ref]
    (per/match-statement-batch matchers nil statements)))
