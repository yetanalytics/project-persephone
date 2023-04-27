(ns com.yetanalytics.persephone-test.server-test.validate-test
  (:require [clojure.test   :refer [deftest testing is]]
            [clojure.edn    :as edn]
            [babashka.curl  :as curl]
            [com.yetanalytics.persephone.server :as server]
            [com.yetanalytics.persephone.server.validate :as v]))

(def profile-uri "test-resources/sample_profiles/calibration.jsonld")

(def template-1-id "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-1")
(def template-2-id "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2")
(def template-3-id "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-3")

(def statement
  (slurp "test-resources/sample_statements/calibration_1.json"))

(def statement-bad
  (slurp "test-resources/sample_statements/adl_1.json"))

(defmacro test-validate-server
  [desc-string arglist & body]
  `(testing ~desc-string
     (v/compile-templates! ~arglist)
     (let [server# (server/start-server :validate "localhost" 8080)]
       (try ~@body
            (catch Exception e#
              (.printStackTrace e#)))
       (server/stop-server server#)
       nil)))

(defn- post-map [body]
  {:headers {"Content-Type" "application/json"}
   :body    body
   :throw   false})

(deftest validate-test
  (test-validate-server
   "Basic validation w/ one profile:"
   (list "--profile" profile-uri)
   (testing "health check"
     (let [{:keys [status body]}
           (curl/get "localhost:8080/health")]
       (is (= 200 status))
       (is (= "OK" body))))
   (testing "validation passes"
     (let [{:keys [status]}
           (curl/post "localhost:8080/statements"
                      (post-map statement))]
       (is (= 204 status)))
     (let [{:keys [status]}
           (curl/post "localhost:8080/statements"
                      (post-map (str "[" statement "," statement "]")))]
       (is (= 204 status))))
   (testing "validation fails"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map statement-bad))]
       (is (= 400 status))
       (is (= :validation-failure
              (-> body edn/read-string :type)))
       (is (= #{template-1-id template-2-id template-3-id}
              (-> body edn/read-string :contents keys set)))))
   (testing "statement does not conform to spec"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map "{\"id\": \"not-a-statement\"}"))]
       (is (= 400 status))
       (is (= :invalid-statement
              (-> body edn/read-string :type))))))
  (test-validate-server
   "Validation works w/ two profiles"
   (list "-p" profile-uri "-p" "test-resources/sample_profiles/catch.json")
   (let [{:keys [status]}
         (curl/post "localhost:8080/statements"
                    (post-map statement))]
     (is (= 204 status))))
  (test-validate-server
   "Validation with '--all-valid' flag"
   (list "-p" profile-uri "--all-valid")
   (testing "validation fails"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map statement))]
       (is (= 400 status))
       (is (= :validation-failure
              (-> body edn/read-string :type)))
       (is (= #{template-2-id template-3-id}
              (-> body edn/read-string :contents keys set))))))
  (test-validate-server
   "Validation with '--all-valid' and '--short-circuit' flags"
   (list "-p" profile-uri "--all-valid" "--short-circuit")
   (testing "validation fails"
     (let [{:keys [status body]}
           (curl/post "localhost:8080/statements"
                      (post-map statement))]
       (is (= 400 status))
       (is (= :validation-failure
              (-> body edn/read-string :type)))
       (is (= #{template-2-id}
              (-> body edn/read-string :contents keys set))))))
  (test-validate-server
   "Validation with '--template-id' flag"
   (list "-p" profile-uri "-i" template-2-id "-i" template-3-id)
   (let [{:keys [status body]}
         (curl/post "localhost:8080/statements"
                    (post-map statement))]
     (is (= 400 status))
     (is (= :validation-failure
            (-> body edn/read-string :type)))
     (is (= #{template-2-id template-3-id}
            (-> body edn/read-string :contents keys set)))))
  (test-validate-server
   "Validation works (vacuously) w/ zero templates"
   (list "-p" profile-uri "-i" "http://fake-template.com")
   (let [{:keys [status]}
         (curl/post "localhost:8080/statements"
                    (post-map statement))]
     (is (= 204 status)))))
