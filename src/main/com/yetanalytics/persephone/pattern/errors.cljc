(ns com.yetanalytics.persephone.pattern.errors
  "Error message namespace."
  (:require [clojure.string :as string]
            #?@(:cljs [[goog.string :as gstring]
                       [goog.string.format]])))

;; Format function
(def fmt #?(:clj format :cljs gstring/format))

(defn- pattern-path-str
  [path]
  (string/join "\n" (map #(str "  " %) path)))

(defn- template-visit-str
  [{visited-templates :templates pattern-traces :patterns}]
  (fmt (str "Statement Templates visited:\n%s\n"
            "Pattern path%s:\n%s")
       (string/join "\n" (map #(str "  " %) visited-templates))
       (if (= 1 (count pattern-traces)) "" "s")
       (string/join "\n  OR\n" (map pattern-path-str pattern-traces))))

(defn- trace-str
  [trace-coll]
  (string/join "\n\nOR\n\n"
               (map template-visit-str trace-coll)))

(defn error-msg-str
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
       (if (not-empty trace-coll)
         (trace-str trace-coll)
         "Pattern cannot match any statements.")))
