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
  (fmt (str "----- Pattern Match Failure -----\n"
            "Primary Pattern ID: %s\n"
            "Statement ID:       %s\n"
            "\n"
            "%s")
       pattern-id
       statement-id
       (cond
         (nil? trace-coll)   "Pattern matching has failed."
         (empty? trace-coll) "Pattern cannot match any statements."
         :else               (trace-str trace-coll))))

;; TODO: Delete in the next break ver
(defn ^:deprecated error-msg-str [failure]
  (failure-message-str failure))

(defn error-message-str
  "Given a pattern match error map, create a pretty error message
   detailing the error, Statement ID, and relevant Statement details
   (i.e. context activities, registration, and extensions)."
  [{error-type :type
    {statement-id "id"
     stmt-context "context"} :statement}]
  (let [type-str
        (case error-type
          ::stmt/missing-profile-reference
          "No Profile reference in category contextActivity IDs"
          ::stmt/invalid-subreg-no-registration
          "Subregistration without registration property"
          ::stmt/invalid-subreg-nonconformant
          "Subregistration does not conform to spec"
          ;; else
          "Unknown")
        details-str
        (condp contains? error-type
          ;; contextActivities error
          #{::stmt/missing-profile-reference}
          (let [ccats (get-in stmt-context ["contextActivities" "category"])]
            (->> (or (not-empty (map #(get % "id") ccats))
                     "(None)")
                 (cstr/join "\n")
                 (fmt "Category contextActivity IDs:\n%s")))
          #{::stmt/invalid-subreg-no-registration
            ::stmt/invalid-subreg-nonconformant}
          (let [subregs (get-in stmt-context ["extensions"
                                              stmt/subregistration-iri])]
            (->> subregs
                 pprint/pprint
                 with-out-str
                 (fmt "Subregistration Extension:\n%s")))
          ;; else
          "")]
    (fmt (str "----- Pattern Match Error -----\n"
              "Error:        %s\n"
              "Statement ID: %s\n"
              "\n"
              "%s")
         type-str
         statement-id
         details-str)))

(comment
  (println
   (error-message-str
    {:type ::stmt/missing-profile-reference
     :statement {"id" "000000000-4000-8000-0000-111111111111"
                 "context" {"contextActivities" {"category" [#_{"id" "http://foo.org"}
                                                             #_{"id" "http://bar.org"}]}}}}))
  
  (println
   (error-message-str
    {:type ::stmt/invalid-subreg-no-registration
     :statement {"id" "000000000-4000-8000-0000-111111111111"
                 "context" {"extensions" {stmt/subregistration-iri
                                          [{"profile" "http://foo.org"
                                            "subregistration" 2 #_"00000000-4000-8000-0000-111122223333"}]}}}}))
  (pr-str
   [{"profile" "http://profile.org"
     "subregistration" "000000000-4000-8000-0000-000000000000"}])
  (with-out-str
    (pprint/pprint
     [{"profile" "http://profile.org"
       "subregistration" "000000000-4000-8000-0000-000000000000"}])))
