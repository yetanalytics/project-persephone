(ns com.yetanalytics.persephone.utils.errors
  (:require [clojure.string :as string]
            #?@(:cljs [[goog.string :as gstring]
                       [goog.string.format]])))

;; Format function

(def fmt #?(:clj format :cljs gstring/format))

;; Generic error messages
(def profile-exception-msg
  "Exception thrown: Cannot process profile.")

(def statement-exception-msg
  "Exception thrown: Cannot process statement.")

;; Statement Template error messages
(def error-msgs-map
  {:all-matchable?     "failed: all values need to be matchable"
   :none-matchable?    "failed: no values can be matchable"
   :any-matchable?     "failed: at least one matchable value must exist"
   :some-any-values?   "failed 'any' property: evaluated values must include some values given by 'any'"
   :only-all-values?   "failed 'all' property: evaluated values must only include values given by 'all'"
   :no-unmatch-vals?   "failed 'all' property: evaluated values must not include unmatchable values"
   :no-none-values?    "failed 'none' property: evaluated values must exclude values given by 'none'"
   :every-val-present? "failed: all values given by rule must be found in evaluated values."})

(defn- val-str
  "Create a pretty-print string representation of a vector, where each
   entry is on a new line. Expound if no values are found."
  [val-arr]
  ;; Same predicate as `non-matchable?` in template-validation
  (if (or (empty? val-arr) (every? nil? val-arr))
    (str "   no values found at location") ; redundant `str` for aesthetics
    (str "   " (string/join "\n   " val-arr))))

(defn- rule-str
  "Create a pretty-print string representation of a rule, with each
   property and its value on a new line."
  [rule]
  (str "  " (-> rule str (string/replace #", (?=:)" ",\n   "))))

(defn prop-error-str
  "Return an error message string for a Determining Property error.
   Format:
   ```
   Template <property> property was not matched.
    template <property>:
      <value A>
    statement <property>:
      <value B>
   ```"
  [rule prop values]
  (fmt (str "Template %s property was not matched.\n"
            " template %s:\n"
            "%s\n"
            " statement %s:\n"
            "%s\n")
       prop
       prop
       (-> rule :prop-vals val-str)
       prop
       (-> values val-str)))

(defn rule-error-str
  "Return an error message string for a rule error. Format:
   ```
   Template rule was not followed:
     {:location ...,
      :property ...}
    failed: <reason for error>
    statement values:
     <value 1>
     <value 2>
   ```"
  [rule pred values]
  (fmt (str "Template rule was not followed:\n"
            "%s\n"
            " %s\n"
            " statement values:\n"
            "%s\n")
       (rule-str rule)
       (get error-msgs-map pred "failed: unknown error occured")
       (val-str values)))

(defn error-msg-str
  "Create a pretty error log output when a property or rule is not
   followed."
  [{:keys [pred rule values] :as _error}]
  (let [prop (get rule :determining-property nil)]
    (if (some? prop)
      (prop-error-str rule prop values)
      (rule-error-str rule pred values))))

(defn print-error
  "Prints an error message for all Template validation errors on a
   Statement."
  [error-vec tid sid]
  (print (fmt (str "----- Invalid Statement -----\n"
                   "Template ID:  %s\n"
                   "Statement ID: %s\n"
                   "\n")
              (str tid)
              (str sid)))
  (doseq [error error-vec]
    (println (error-msg-str error)))
  (print (fmt (str "-----------------------------\n"
                   "Total errors found: %d\n"
                   "\n")
              (count error-vec))))

(defn print-bad-statement
  "Prints the Statmeent ID if it is rejected by a Pattern."
  [statement]
  (let [err-msg (str "Pattern rejected statement " (:id statement))]
    (println err-msg)))
