(ns com.yetanalytics.persephone.pattern.errors
  "Error message namespace."
  (:require [clojure.string :as cstr]
            [com.yetanalytics.persephone.utils.statement :as stmt]
            #?(:clj [clojure.pprint :as pprint]
               :cljs [cljs.pprint :as pprint])
            #?@(:cljs [[goog.string :as gstring]
                       [goog.string.format]])))

;; Format function
(def fmt #?(:clj format :cljs gstring/format))

(defn- pattern-path-str
  [path]
  (cstr/join "\n" (map #(str "  " %) path)))

(defn- template-visit-str
  [{visited-templates :templates pattern-traces :patterns}]
  (fmt (str "Statement Templates visited:\n%s\n"
            "Pattern path%s:\n%s")
       (cstr/join "\n" (map #(str "  " %) visited-templates))
       (if (= 1 (count pattern-traces)) "" "s")
       (cstr/join "\n  OR\n" (map pattern-path-str pattern-traces))))

(defn- trace-str
  [trace-coll]
  (cstr/join "\n\nOR\n\n"
             (map template-visit-str trace-coll)))

(defn failure-message-str
  "Given an pattern match failure map, create a pretty error message
   detailing the statement ID, primary pattern ID, and the traces
   containing visited templates and pattern paths."
  [{statement-id :statement
    pattern-id   :pattern
    trace-coll   :traces}]
  (let [details-str
        (cond
          (nil? trace-coll)   "Pattern matching has failed."
          (empty? trace-coll) "Pattern cannot match any statements."
          :else               (trace-str trace-coll))]
    (fmt (str "----- Pattern Match Failure -----\n"
              "Primary Pattern ID: %s\n"
              "Statement ID:       %s\n"
              "\n"
              "%s")
         pattern-id
         statement-id
         details-str)))

;; TODO: Delete in the next break ver
(defn ^:deprecated error-msg-str [failure]
  (failure-message-str failure))

(defn error-message-str
  "Given a pattern match error map, create a pretty error message
   detailing the error, Statement ID, and relevant Statement details
   (i.e. context activities, registration, and subregistrations).
   
   Each error type has a corresponding error description (the `stmt`
   alias is for the `persephone.utils.statement` namespace):
   | Keyword | Description
   | ---     | ---
   | `::stmt/missing-profile-reference`      | Missing Profile version in context category activity IDs
   | `::stmt/invalid-subreg-no-registration` | Invalid subregistration - no statement registration
   | `::stmt/invalid-subreg-nonconformant`   | Invalid subregistration - does not conform to spec"
  [{error-type :type
    {statement-id "id"
     stmt-context "context"} :statement}]
  (let [type-string
        (case error-type
          ::stmt/missing-profile-reference
          "Missing Profile version in context category activity IDs"
          ::stmt/invalid-subreg-no-registration
          "Invalid subregistration - no statement registration"
          ::stmt/invalid-subreg-nonconformant
          "Invalid subregistration - does not conform to spec"
          ;; else
          "Unknown error")
        details-string
        (condp contains? error-type
          ;; contextActivities error
          #{::stmt/missing-profile-reference}
          (let [ccats (get-in stmt-context ["contextActivities" "category"])]
            (fmt "Category contextActivity IDs:\n%s"
                 (or (->> ccats (map #(get % "id")) (cstr/join "\n") not-empty)
                     "(None)")))
          ;; subregistration error
          #{::stmt/invalid-subreg-no-registration
            ::stmt/invalid-subreg-nonconformant}
          (let [registration (get-in stmt-context ["registration"])
                subregs      (get-in stmt-context ["extensions"
                                                   stmt/subregistration-iri])]
            (fmt (str "Registration value:\n"
                      "%s\n"
                      "Subregistration extension value:\n"
                      "%s")
                 registration
                 (->> subregs pprint/pprint with-out-str cstr/trim)))
          ;; else
          "")]
    (fmt (str "----- Pattern Match Error -----\n"
              "Error Description:  %s\n"
              "Statement ID:       %s\n"
              "\n"
              "%s")
         type-string
         statement-id
         details-string)))

(comment
  (println
   (error-message-str
    {:type ::stmt/missing-profile-reference
     :statement {"id" "000000000-4000-8000-0000-111111111111"
                 "context" {"contextActivities" {"category" [{"id" "http://foo.org"}
                                                             #_{"id" "http://bar.org"}]}}}}))
  
  (println
   (error-message-str
    {:type ::stmt/invalid-subreg-no-registration
     :statement {"id" "000000000-4000-8000-0000-111111111111"
                 "context" {"extensions" {stmt/subregistration-iri
                                          [{"profile" "http://foo.org"
                                            "subregistration" "00000000-4000-8000-0000-111122223333"}]}}}}))
  
  (println
   (error-message-str
    {:type ::stmt/invalid-subreg-nonconformant
     :statement {"id" "000000000-4000-8000-0000-111111111111"
                 "context" {"registration" "00000000-4000-8000-0000-555555555555"
                            "extensions" {stmt/subregistration-iri
                                          [{"profile" "http://foo.org"
                                            "subregistration" 2}]}}}}))
  (pr-str
   [{"profile" "http://profile.org"
     "subregistration" "000000000-4000-8000-0000-000000000000"}])
  (with-out-str
    (pprint/pprint
     [{"profile" "http://profile.org"
       "subregistration" "000000000-4000-8000-0000-000000000000"}])))
