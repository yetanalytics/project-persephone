(ns com.yetanalytics.persephone-test.server-test.match-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.edn  :as edn]
            [babashka.curl :as curl]
            [com.yetanalytics.persephone.server :as server]
            [com.yetanalytics.persephone.server.match :as m]))

(def profile-uri "test-resources/sample_profiles/calibration.jsonld")

(def pattern-id "https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1")

(def statement-1
  (slurp "test-resources/sample_statements/calibration_1.json"))

(def statement-2
  (slurp "test-resources/sample_statements/calibration_2.json"))

(def statement-bad
  (slurp "test-resources/sample_statements/adl_1.json"))

(defmacro test-match-server
  [desc-string arglist & body]
  `(testing ~desc-string
     (m/compile-patterns! ~arglist)
     (let [server# (server/start-server :match "localhost" 8080)]
       (try ~@body
            (catch Exception e#
              (.printStackTrace e#)))
       (server/stop-server server#)
       nil)))

(defn- post-map [body]
  {:headers {"Content-Type" "application/json"}
   :body    body
   :throw   false})

(deftest match-test
  (test-match-server
   "Basic pattern matching w/ one profile:"
   (list "--profile" profile-uri)
   (testing "health check"
     (let [{:keys [status body]}
           (curl/get "localhost:8080/health")]
       (is (= 200 status))
       (is (= "OK" body))))
   (testing "match passes"
     (let [{:keys [status]}
           (curl/post "localhost:8080/statements"
                      (post-map statement-1))]
       (is (= 204 status)))
     (let [{:keys [status]}
           (curl/post "localhost:8080/statements"
                      (post-map (str "[" statement-1 "," statement-2 "]")))]
       (is (= 204 status))))
   (testing "match fails"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map (str "[" statement-2 "," statement-1 "]")))]
       (is (= 400 status))
       (is (= :match-failure
              (-> body edn/read-string :type)))
       (is (= {:accepts    []
               :rejects    [[:no-registration pattern-id]]
               :states-map {:no-registration {pattern-id #{}}}}
              (-> body edn/read-string :contents)))))
   (testing "match errors out"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map statement-bad))]
       (is (= 400 status))
       (is (= :match-error
              (-> body edn/read-string :type)))))
   (testing "statement does not conform to spec"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map "{\"id\": \"not-a-statement\"}"))]
       (is (= 400 status))
       (is (= :invalid-statements
              (-> body edn/read-string :type)))))
   (testing "invalid JSON"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map "{\"id\":"))]
       (is (= 400 status))
       (is (= :invalid-json
              (-> body edn/read-string :type)))))
   (testing "invalid Content-Type"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      {:headers {"Content-Type" "application/edn"}
                       :body    statement-1
                       :throw   false})]
       (is (= 400 status)) ; ideally should be 415 Unsupported Media Type
       (is (= :invalid-statements
              (-> body edn/read-string :type))))))
  (test-match-server
   "Pattern matching works w/ two profiles"
   (list "-p" profile-uri "-p" "test-resources/sample_profiles/catch.json")
   (let [{:keys [status]}
         (curl/post "localhost:8080/statements"
                    (post-map (str "[" statement-1 "," statement-2 "]")))]
     (is (= 204 status))))
  (test-match-server
   "Pattern matching works (vacuously) w/ zero patterns"
   (list "-p" profile-uri "-i" "http://fake-pattern.com")
   (let [{:keys [status]}
         (curl/post "localhost:8080/statements"
                    (post-map (str "[" statement-1 "," statement-2 "]")))]
     (is (= 204 status)))))
