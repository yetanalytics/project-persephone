(ns com.yetanalytics.persephone.server
  (:require [clojure.tools.cli            :as cli]
            [io.pedestal.interceptor      :as i]
            [io.pedestal.http             :as http]
            [io.pedestal.http.body-params :as body-params]
            [com.yetanalytics.persephone.utils.cli       :as u]
            [com.yetanalytics.persephone.server.validate :as v]
            [com.yetanalytics.persephone.server.match    :as m])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def health
  (i/interceptor
   {:name ::health
    :enter (fn [context]
             (assoc context :response {:status 200 :body "OK"}))}))

(def request-body
  "Interceptor that performs parsing for request bodies `application/json`
   content type, keeping keys as strings instead of keywordizing them.
   Returns a 400 error for invalid JSON."
  (-> (body-params/body-params
       {#"^application/json" (body-params/custom-json-parser :key-fn str)})
      (assoc :error
             (fn [context _]
               (assoc context :response {:status 400
                                         :body   {:type :invalid-json}})))))

(def validate
  (i/interceptor
   {:name ::validate
    :enter
    (fn validate [context]
      (let [statements (get-in context [:request :json-params])
            statement  (if (sequential? statements)
                         (last statements)
                         statements)
            stmt-err   (u/statement-err-data statement)
            stmt-err?  (some? stmt-err)
            err-res    (and (not stmt-err?)
                            (v/validate statement))
            valid-err? (and (not stmt-err?)
                            (some? err-res))
            response   (cond
                         stmt-err?  {:status 400
                                     :body   {:type     :invalid-statement
                                              :contents stmt-err}}
                         valid-err? {:status 400
                                     :body   {:type     :validation-failure
                                              :contents err-res}}
                         :else      {:status 204})]
        (assoc context :response response)))}))

(def match
  (i/interceptor
   {:name ::match
    :enter
    (fn match [context]
      (let [statements  (get-in context [:request :json-params])
            statements  (if (sequential? statements)
                          statements
                          [statements])
            stmts-err   (u/statements-err-data statements)
            stmts-err?  (some? stmts-err)
            state-map   (and (not stmts-err?)
                             (m/match statements))
            match-err?  (and (not stmts-err?)
                             (-> state-map :error some?))
            match-fail? (and (not stmts-err?)
                             (-> state-map :rejects not-empty boolean))
            response    (cond
                          stmts-err?  {:status 400
                                       :body   {:type     :invalid-statements
                                                :contents stmts-err}}
                          match-err?  {:status 400
                                       :body   {:type     :match-error
                                                :contents state-map}}
                          match-fail? {:status 400
                                       :body   {:type     :match-failure
                                                :contents state-map}}
                          :else       {:status 204})]
        (assoc context :response response)))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server Settings + Create
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- main-interceptor [mode-k]
  (case mode-k
    :validate validate
    :match    match))

(defn- routes [mode-k]
  (let [main-intercept (main-interceptor mode-k)]
    #{["/health"
       :get [health]
       :route-name :server/health]
      ["/statements"
       :post [request-body
              main-intercept]
       :route-name :server/statements]}))

(defn start-server [mode-k host port]
  (let [routes-set (routes mode-k)
        server-map {::http/routes          routes-set
                    ::http/type            :jetty
                    ::http/allowed-origins []
                    ::http/host            host
                    ::http/port            port
                    ::http/join?           false}]
    (http/start (http/create-server server-map))))

(defn stop-server [server]
  (http/stop server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server Init CLI
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def top-level-options
  [["-H" "--host HOST" "The hostname of the webserver endpoint"
    :id      :host
    :default "localhost"]
   ["-P" "--port PORT" "The port number of the webserver endpoint; must be between 0 and 65536"
    :id       :port
    :default  8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be an integer between 0 and 65536."]]
   ["-h" "--help" "Display the top-level help guide"]])

(defn- top-level-summary [option-specs]
  (str "Usage 'server [--host|-H HOST] [--port|-P PORT] [--help|-h] <subcommand> <args>'\n"
       "\n"
       "where the subcommand can be one of the following:\n"
       "  validate  Start a webserver that performs Statement Template validation on Statement requests\n"
       "  match     Start a webserver that performs Pattern matching on Statement batch requests\n"
       "\n"
       "The 'server' command has the following optional arguments, along with defaults:\n"
       (cli/summarize option-specs) "\n"
       "\n"
       "Run 'server <subcommand> --help' for details on each subcommand."))

(defn -main [& args]
  (let [{:keys [options summary arguments]}
        (cli/parse-opts args top-level-options
                        :in-order true
                        :summary-fn top-level-summary)
        {:keys [host port help]}
        options
        [subcommand & rest]
        arguments]
    (cond
      help
      (do (println summary)
          (System/exit 0))
      (= "validate" subcommand)
      (let [k (v/compile-templates! rest)]
        (cond
          (= :help k)
          (System/exit 0)
          (= :error k)
          (System/exit 1)
          :else
          (start-server :validate host port)))
      (= "match" subcommand)
      (let [k (m/compile-patterns! rest)]
        (cond
          (= :help k)
          (System/exit 0)
          (= :error k)
          (System/exit 1)
          :else
          (start-server :match host port)))
      :else
      (do (u/printerr (format "Unknown subcommand: %s" subcommand))
          (System/exit 1)))))
