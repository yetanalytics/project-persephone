(ns com.yetanalytics.utils.errors
  (:require [clojure.string :as string]))

;; Generic error messages
(def profile-exception-msg
  "Exception thrown: Cannot process profile.")

(def statement-exception-msg
  "Exception thrown: Cannot process statement.")

;; Statement Template error messages
(def error-msgs
  {"all-matchable?" "failed 'included': not all evaluated values were matchable"
   "none-matchable?" "failed 'excluded': all evaluated values must not be matchable"
   "any-matchable?" "failed: some evaluated values must be matchable"
   "rule-any?" "failed 'any' property: evaluated values must include some values given by 'any'"
   "rule-all?" "failed 'all' property: evaluated values must only include values given by 'all'"
   "rule-none?" "failed 'none' property: evaluated values must exclude values given by 'none'"})

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
  (str "  " (-> rule str (string/replace #", " ",\n   "))))

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
       (-> rule :all val-str) "\n"
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
       " " (get error-msgs pred "failed: unknown error occured") "\n"
       " statement values:\n"
       (val-str values) "\n"))

(defn error-msg-str
  "Create a pretty error log output when a property or rule is not followed."
  [{:keys [error rule values]}]
  (let [prop (get rule :determiningProperty nil)
        pred (-> error :pred name)]
    (if (some? prop)
      (prop-error-str rule prop values)
      (rule-error-str rule pred values))))

(defn print-error
  "Prints an error message for all Template validation errors on a Statement."
  [error-vec tid sid]
  (do (print (str "----- Invalid Statement -----\n"
                  "Statement ID: " (str sid) "\n"
                  "Template ID: " (str tid) "\n\n"))
      (doseq [error error-vec]
        (println (error-msg-str error)))
      (print (str "-----------------------------\n"
                  "Total errors found: " (pr-str (count error-vec)) "\n\n"))))

(defn print-bad-statement
  "Prints the Statmeent ID if it is rejected by a Pattern."
  [statement]
  (let [err-msg (str "Pattern rejected statement " (:id statement))]
    (println err-msg)))
