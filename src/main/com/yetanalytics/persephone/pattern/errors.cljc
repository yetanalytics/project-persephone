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
  [{statement-id   :statement
    pattern-id     :pattern
    trace-coll     :traces}]
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

(comment
  (println (pattern-path-str
            ["http://example.org/p2"
             "http://example.org/p1"]))
  (print (error-msg-str {:statement "http://example.org/statement-1"
                         :pattern "http://example.org/p0"
                         :fail-info
                         [{:templates ["http://example.org/t3"
                                       "http://example.org/t2"]
                           :patterns [["http://example.org/p2"
                                       "http://example.org/p1"]
                                      ["http://example.org/p4"
                                       "http://example.org/p3"]]}
                          {:templates ["http://example.org/t40"
                                       "http://example.org/t30"
                                       "http://example.org/t20"]
                           :patterns [["http://example.org/p20"
                                       "http://example.org/p10"]
                                      ["http://example.org/p40"
                                       "http://example.org/p30"]]}]}))
  )
