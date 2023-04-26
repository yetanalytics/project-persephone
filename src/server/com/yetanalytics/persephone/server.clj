(ns com.yetanalytics.persephone.server
  (:require [io.pedestal.http :as http]
            [io.pedestal.interceptor :as i]
            [com.yetanalytics.persephone :as per])
  (:gen-class))

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
      (let [{:keys [request]}     context
            {:keys [json-params]} request
            {:keys [profiles
                    statement]}   json-params
            ;; Compile and validate
            compiled (per/compile-profiles->validators profiles)
            err-res  (per/validate-statement compiled statement
                                             :fn-type :errors)]
        (assoc context :response {:status 200 :body err-res})))}))

(def match
  (i/interceptor
   {:name ::match
    :enter
    (fn match [context]
      (let [{:keys [request]}     context
            {:keys [json-params]} request
            {:keys [profiles
                    statements]}  json-params
            ;; Compile and match
            compiled (per/compile-profiles->fsms profiles)
            state-m (per/match-statement-batch compiled nil statements)]
        (assoc context :response {:status 200 :body state-m})))}))

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

(defn -main [& _]
  (http/start (create-server)))
