(ns com.yetanalytics.persephone.template.errors
  (:require [clojure.string :as string]
            #?@(:cljs [[goog.string :as gstring]
                       [goog.string.format]])))

;; Format function
(def fmt #?(:clj format :cljs gstring/format))

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
  [{:keys [pred rule vals] :as _error}]
  (cond
    ;; Statement Ref Templates
    (= :statement-ref? pred)
    (let [{fail :failure sref :location} rule]
      (sref-error-str fail sref vals))
    ;; Determining Properties
    (contains? rule :determining-property)
    (let [{prop :determining-property pvals :prop-vals} rule]
      (prop-error-str prop pvals vals))
    ;; Rules
    :else
    (rule-error-str rule pred vals)))

(defn print-errors
  "Print all the errors in `error-vec`, grouped by Statement and
   Template ID."
  [error-vec]
  ;; Not exactly the most optimized way of doing things, but that's not
  ;; really a priority when printing
  (let [error-vec' (->> error-vec
                        (reduce (fn [acc {:keys [temp stmt] :as err}]
                                  (update
                                   acc
                                   [temp stmt]
                                   (fn [errs] (if err (conj errs err) [err]))))
                                {})
                        (mapv (fn [[header errs]] [header (reverse errs)])))]
    (doseq [[[temp-id stmt-id] error-subvec] error-vec']
      (print (fmt (str "----- Invalid Statement -----\n"
                       "Template ID:  %s\n"
                       "Statement ID: %s\n"
                       "\n")
                  temp-id
                  stmt-id))
      (doseq [error error-subvec]
        (println (error-msg-str error))))
    (print (fmt (str "-----------------------------\n"
                     "Total errors found: %d\n"
                     "\n")
                (count error-vec)))))
