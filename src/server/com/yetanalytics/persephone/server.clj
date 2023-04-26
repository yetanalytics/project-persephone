(ns com.yetanalytics.persephone.server
  (:require [clojure.tools.cli            :as cli]
            [io.pedestal.interceptor      :as i]
            [io.pedestal.http             :as http]
            [io.pedestal.http.body-params :as body-params]
            [com.yetanalytics.persephone.server.validate :as v]
            [com.yetanalytics.persephone.server.match    :as m]
            [com.yetanalytics.persephone.server.util     :as u])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def request-body
  "Interceptor that performs parsing for request bodies `application/json`
   content type, keeping keys as strings instead of keywordizing them."
  (body-params/body-params
   {#"^application/json" (body-params/custom-json-parser :key-fn str)}))

(def health
  (i/interceptor
   {:name ::health
    :enter (fn [context]
             (assoc context :response {:status 200 :body "OK"}))}))

(def validate
  (i/interceptor
   {:name ::validate
    :enter
    (fn validate [context]
      (let [statement (get-in context [:request :json-params])
            err-res   (v/validate statement)]
        (assoc context :response {:status 200 :body err-res})))}))

(def match
  (i/interceptor
   {:name ::match
    :enter
    (fn match [context]
      (let [statements (get-in context [:request :json-params])
            state-map  (m/match statements)]
        (assoc context :response {:status 200 :body state-map})))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server Settings + Create
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  #{["/health"
     :get [health]
     :route-name :server/health]
    ["/validate"
     :post [request-body validate]
     :route-name :server/validate]
    ["/match"
     :post [request-body match]
     :route-name :server/match]})

(defn- create-server []
  (http/create-server
   {::http/routes          routes
    ::http/type            :jetty
    ::http/allowed-origins []
    ::http/host            "localhost"
    ::http/port            8080
    ::http/join?           false}))

(defn- start-server []
  (http/start (create-server)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server Init CLI
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def top-level-options
  [["-h" "--help" "Display the top-level help guide."]])

(def top-level-summary
  (str "Usage 'persephone-server <subcommand> <args>' or 'persephone-server [-h|--help]'\n"
       "\n"
       "where the subcommand can be one of the following:\n"
       "  validate  Start a webserver that performs Statement Template validation on Statement requests\n"
       "  match     Start a webserver that performs Pattern matching on Statement batch requests\n"
       "\n"
       "Run 'persephone-server <subcommand> --help' for details on each subcommand"))

(defn -main [& args]
  (let [{:keys [options summary arguments]}
        (cli/parse-opts args top-level-options
                        :in-order true
                        :summary-fn (fn [_] top-level-summary))
        [subcommand & rest]
        arguments]
    (cond
      (:help options)
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
          (start-server)))
      (= "match" subcommand)
      (let [k (m/compile-patterns! rest)]
        (cond
          (= :help k)
          (System/exit 0)
          (= :error k)
          (System/exit 1)
          :else
          (start-server)))
      :else
      (do (u/printerr [(format "Unknown subcommand: %s" subcommand)])
          (System/exit 1)))))
