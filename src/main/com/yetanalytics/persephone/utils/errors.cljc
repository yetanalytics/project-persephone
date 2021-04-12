(ns com.yetanalytics.persephone.utils.errors
  (:require [clojure.string :as string]))

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

(defn val-str
  "Create a pretty-print string representation of a vector, where each entry
  is on a new line. Expound if no values are found."
  [val-arr]
  ;; Same predicate as non-matchable? in template-validation
  (if (or (empty? val-arr) (every? nil? val-arr))
    "   no values found at location"
    (str "   " (string/join "\n   " val-arr))))

(defn rule-str
  "Create a pretty-print string representation of a rule, with each property
  and its value on a new line."
  [rule]
  (str "  " (-> rule str (string/replace #", (?=:)" ",\n   "))))

(defn prop-error-str
  "Return an error message string for a Determining Property error. Format:
  > Template <property> property was not matched.
  >  template <property>:
  >    <value A>
  >  statement <property>:
  >    <value B>"
  [rule prop values]
  (str "Template " prop " property was not matched.\n"
       " template " prop ":\n"
       (-> rule :prop-vals val-str) "\n"
       " statement " prop ":\n"
       (val-str values) "\n"))

(defn rule-error-str
  "Return an error message string for a rule error. Format:
  > Template rule was not followed:
  >   {:location ...,
  >    :property ...}
  >  failed: <reason for error>
  >  statement values:
  >   <value 1>
  >   <value 2>"
  [rule pred values]
  (str "Template rule was not followed:\n"
       (rule-str rule) "\n"
       " " (get error-msgs-map pred "failed: unknown error occured") "\n"
       " statement values:\n"
       (val-str values) "\n"))

(defn error-msg-str
  "Create a pretty error log output when a property or rule is not followed."
  [{:keys [pred rule values]}]
  (let [prop (get rule :determining-property nil)]
    (if (some? prop)
      (prop-error-str rule prop values)
      (rule-error-str rule pred values))))

(defn print-error
  "Prints an error message for all Template validation errors on a Statement."
  [error-vec tid sid]
  (print (str "----- Invalid Statement -----\n"
              "Statement ID: " (str sid) "\n"
              "Template ID: " (str tid) "\n\n"))
  (doseq [error error-vec]
    (println (error-msg-str error)))
  (print (str "-----------------------------\n"
              "Total errors found: " (pr-str (count error-vec)) "\n\n")))

(defn print-bad-statement
  "Prints the Statmeent ID if it is rejected by a Pattern."
  [statement]
  (let [err-msg (str "Pattern rejected statement " (:id statement))]
    (println err-msg)))
