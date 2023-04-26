(ns com.yetanalytics.persephone.server
  (:require [clojure.tools.cli       :as cli]
            [io.pedestal.http        :as http]
            [io.pedestal.interceptor :as i]
            [com.yetanalytics.persephone.server.validate :as v]
            [com.yetanalytics.persephone.server.match    :as m]
            [com.yetanalytics.persephone.server.util     :as u])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
      (let [statement (get-in context [:request :json-params :statement])
            err-res   (v/validate statement)]
        (assoc context :response {:status 200 :body err-res})))}))

(def match
  (i/interceptor
   {:name ::match
    :enter
    (fn match [context]
      (let [statements (get-in context [:request :json-params :statements])
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
     :post [validate]
     :route-name :server/validate]
    ["/match"
     :post [match]
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
