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
  [prop prop-vals real-vals]
  (fmt (str "Template %s property was not matched.\n"
            " template %s:\n"
            "%s\n"
            " statement %s:\n"
            "%s\n")
       prop
       prop
       (val-str prop-vals)
       prop
       (val-str real-vals)))

(defn sref-error-str
  "Return an error message string for a Statement Ref Template error.
   Format:
   ```
   <error message>
   <error data>
   ```"
  [failure sref value]
  (let [sref-name
        (cond
          (= "$.object" sref)
          "Object Statement Ref"
          (= "$.context.statement" sref)
          "Context Statement Ref"
          :else
          "Statement Ref")
        err-msg
        (case failure
          :sref-not-found
          (fmt "No %s is present in the Statement:"
               sref-name)
          :sref-object-type-invalid
          (fmt "The objectType of the following %s is not \"StatementRef\":"
               sref-name)
          :sref-id-missing
          (fmt "The following %s has no id value:"
               sref-name)
          :sref-stmt-not-found
          (fmt "Cannot find Statement given by the id in the %s:"
               sref-name))]
    (fmt (str "%s\n"
              "%s")
         err-msg
         (str value))))

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
  (cond
    ;; Statement Ref Templates
    (= :statement-ref? pred)
    (let [{fail :failure sref :location} rule]
      (sref-error-str fail sref values))
    ;; Determining Properties
    (contains? rule :determining-property)
    (let [{prop :determining-property vals :prop-vals} rule]
      (prop-error-str prop vals values))
    ;; Rules
    :else
    (rule-error-str rule pred values)))

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
