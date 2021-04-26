(ns com.yetanalytics.persephone-test.util-test.maps-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.yetanalytics.persephone.utils.maps :as m]))

(def ex-profile
  {:_context   "https://w3id.org/xapi/profiles/context"
   :id         "http://foo.org/sample-profile"
   :type       "Profile"
   :conformsTo "https://w3id.org/xapi/profiles#1.0"
   :prefLabel  {:en "sample Profile"}
   :definition {:en "Blah blah blah"}
   :author     {:type "Person"
                :name "Kelvin Qian"}
   :versions   [{:id              "https://foo.org/version1"
                 :generatedAtTime "2017-03-27T12:30:00-07:00"}]
   :concepts   [{:id         "http://foo.org/verb1"
                 :inScheme   "https://foo.org/version1"
                 :type       "Verb"
                 :prefLabel  {:en "Verb 1"}
                 :definition {:en "First Verb"}}
                {:id         "http://foo.org/verb2"
                 :inScheme   "https://foo.org/version1"
                 :type       "Verb"
                 :prefLabel  {:en "Verb 2"}
                 :definition {:en "Second Verb"}}
                {:id         "http://foo.org/verb3"
                 :inScheme   "https://foo.org/version1"
                 :type       "Verb"
                 :prefLabel  {:en "Verb 3"}
                 :definition {:en "Third Verb"}}]
   :templates  [{:id         "http://foo.org/t1"
                 :type       "StatementTemplate"
                 :inScheme   "https://foo.org/version1"
                 :prefLabel  {:en "Template 1"}
                 :definition {:en "First Statement Template"}
                 :verb       "http://foo.org/verb1"}
                {:id         "http://foo.org/t2"
                 :type       "StatementTemplate"
                 :inScheme   "https://foo.org/version1"
                 :prefLabel  {:en "Template 2"}
                 :definition {:en "Second Statement Template"}
                 :verb       "http://foo.org/verb2"}
                {:id         "http://foo.org/t3"
                 :type       "StatementTemplate"
                 :inScheme   "https://foo.org/version1"
                 :prefLabel  {:en "Template 3"}
                 :definition {:en "Third Statement Template"}
                 :verb       "http://foo.org/verb3"}]
   :patterns   [{:id         "http://foo.org/p1"
                 :type       "Pattern"
                 :inScheme   "https://foo.org/version1"
                 :primary    true
                 :prefLabel  {:en "Pattern 1"}
                 :definition {:en "Alternate of Pattern 2 and Template 1"}
                 :alternates ["http://foo.org/p2"
                              "http://foo.org/t1"]}
                {:id         "http://foo.org/p2"
                 :type       "Pattern"
                 :inScheme   "https://foo.org/version1"
                 :primary    false
                 :prefLabel  {:en "Pattern 2"}
                 :definition {:en "Sequence of Template 2, Template 3, and Pattern 1"}
                 :sequence   ["http://foo.org/t2"
                              "http://foo.org/t3"]}
                {:id         "http://foo.org/p3"
                 :type       "Pattern"
                 :inScheme   "https://foo.org/version1"
                 :primary    true
                 :prefLabel  {:en "Pattern 3"}
                 :definition {:en "One or more iterations of Pattern 1"}
                 :oneOrMore  "http://foo.org/p1"}]})

(deftest mapify-coll-test
  (testing "mapify-coll with string key"
    (is (= {"some-id" {"id" "some-id" "foo" 2}
            "other-id" {"id" "other-id"}}
           (m/mapify-coll [{"id" "some-id" "foo" 2}
                           {"id" "other-id"}]
                          :string? true))))
  (testing "mapify-coll with keyword key"
    (is (= {"http://foo.org/p1"
            {:id         "http://foo.org/p1"
             :type       "Pattern"
             :inScheme   "https://foo.org/version1"
             :primary    true
             :prefLabel  {:en "Pattern 1"}
             :definition {:en "Alternate of Pattern 2 and Template 1"}
             :alternates ["http://foo.org/p2"
                          "http://foo.org/t1"]}
            "http://foo.org/p2"
            {:id         "http://foo.org/p2"
             :type       "Pattern"
             :inScheme   "https://foo.org/version1"
             :primary    false
             :prefLabel  {:en "Pattern 2"}
             :definition {:en "Sequence of Template 2, Template 3, and Pattern 1"}
             :sequence   ["http://foo.org/t2"
                          "http://foo.org/t3"]}
            "http://foo.org/p3"
            {:id         "http://foo.org/p3"
             :type       "Pattern"
             :inScheme   "https://foo.org/version1"
             :primary    true
             :prefLabel  {:en "Pattern 3"}
             :definition {:en "One or more iterations of Pattern 1"}
             :oneOrMore  "http://foo.org/p1"}}
           (m/mapify-coll (:patterns ex-profile))))
    (is (= {"http://foo.org/t1"
            {:id         "http://foo.org/t1"
             :type       "StatementTemplate"
             :inScheme   "https://foo.org/version1"
             :prefLabel  {:en "Template 1"}
             :definition {:en "First Statement Template"}
             :verb       "http://foo.org/verb1"}
            "http://foo.org/t2"
            {:id         "http://foo.org/t2"
             :type       "StatementTemplate"
             :inScheme   "https://foo.org/version1"
             :prefLabel  {:en "Template 2"}
             :definition {:en "Second Statement Template"}
             :verb       "http://foo.org/verb2"}
            "http://foo.org/t3"
            {:id         "http://foo.org/t3"
             :type       "StatementTemplate"
             :inScheme   "https://foo.org/version1"
             :prefLabel  {:en "Template 3"}
             :definition {:en "Third Statement Template"}
             :verb       "http://foo.org/verb3"}}
           (m/mapify-coll (:templates ex-profile))))))
