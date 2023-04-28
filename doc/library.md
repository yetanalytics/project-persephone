# Library

The `persephone` namespace contains functions that perform two main tasks: validation against Statement Templates and matching against Patterns, which is accomplished via these API functions:
<!-- NOTE: This ugly non-breaking space padding is so that function/keyword/etc names don't break on hyphens -->
| API&nbsp;Function&nbsp;Name&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description
| :--                     | :--
| `validate-statement`    | Takes a Statement and validates it against the properties and rules of compiled Statement Templates, as described in the [xAPI Statement Template specification](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#statment-templates).
| `match-statement`       | Takes a Statement and matches them against compiled Patterns according to the [xAPI Pattern specification](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns).
| `match-statement-batch` | Takes a collection of Statements and matches them in order against compiled Patterns according to the [xAPI Pattern specification](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns).

The following API functions are provided for Template/Profile compilation:

| API&nbsp;Function&nbsp;Name&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description
| :--                            | :--
| `compile-templates->validators`| Compiles a collection of Statement Templates into validators.
| `compile-profiles->validators` | Compiles a collection of Profiles into validators.
| `compile-profiles->fsms`       | Compiles a collection of Profiles into an FSM map.

NOTE: All Profiles and Statements must be already parsed into EDN format. Profiles must have keyword keys, while Statements must have string keys (to match the expected formats of [project-pan](https://github.com/yetanalytics/project-pan) and [xapi-schema](https://github.com/yetanalytics/xapi-schema), respectively). As convenience functions, Persephone provides the functions `coerce-profile` and `coerce-statement` in `utils/json.cljc` in order to guarantee correct coercion from JSON strings to EDN.

## Statement Template Validation

Validating a Statement against a Statement Template involves three aspects:

- Validating against Rules. To do so, we need to use the JSONPath given by `location` (and possibly `selector`) to return a set of values from within the Statement and match these values against the `presence`, `any`, `all` and `none` property.

- Validating against Determining Properties (the verb, the object activity  type, and the four context activity types). They can be expressed as rules in which the respective values from the Statement MUST be included and given by the Statement Template.

- Validating against StatementRefs (object and context StatementRefTemplates). These are arrays of StatementTemplate IRIs, which  point to _more_ Statement Templates that we need to validate against in a  recursive manner. These additional Statements are referenced by  StatementRefs in the original Statement. This can potentially require querying this and other Profiles; thus, that aspect of validation is, for now, unimplemented.

The compilation functions `compile-templates->validators` and `compile-profiles->validators` return a collection of maps of the following:

| Validator&nbsp;Map&nbsp;Key | Description
| :--             | :--
| `:id`           | The Statement Template ID.
| `:predicate-fn` | A function that takes a Statement and returns `true` if that Statement is valid against it, `false` otherwise.
| `:validator-fn` | A function that takes a Statement and returns `nil` if that Statement is valid against it, a map of error data otherwise.

`compile-templates->validators` takes the following keyword args:

| Keyword&nbsp;Argument&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description
| :--                    | :--
| `:statement-ref-fns`   | A map used for Statement Ref validation; if `nil`, then Statement Ref validation is ignored. See the [Statement Ref Templates](#statement-ref-templates) section for more details.
| `:validate-templates?` | Validates the Templates against the xAPI Profile spec and checks for ID clashes. If validation fails, the function throws a `::assert/invalid-template` exception. Default `true`.
| `:validate-not-empty?` | Asserts that at least one Template validator exists after compilation; if `true`, the function throws a `::assert/no-templates` exception, e.g. if an empty Profile coll was provided or if `:selected-profiles` or `:selected-patterns` filtered out all Patterns. Default `true`; if `false`, Template validation would vacuously pass because there would be no Templates to fail against.
| `:selected-templates`  | Which Statement Templates in the Profiles should be compiled. Useful for selecting only one Template to match against.

`compile-profiles->validators` is similar, except that it takes Profiles instead of Templates, has `:validate-profiles?` instead of `:validate-templates?`, and has an additional `:selected-profiles` argument. If Profile validation fails when `:validate-profiles?` is `true`, then an `::assert/invalid-profile` exception is thrown.

The `validate-statement` function take the keyword argument `:fn-type`, which can be set to the following:

| Keyword      | Description
| :--          | :--
| `:predicate` | Returns `true` for a valid Statement, `false` otherwise. Default.
| `:filter`    | Returns the Statement if it's valid, `nil` otherwise.
| `:errors`    | Returns the validation error data if the Statement is invalid, `nil` otherwise.
| `:assertion` | Throws an exception if the Statement is invalid, returns `nil` otherwise.
| `:printer`   | Prints an error message when the Statement is invalid. Always returns `nil`.
| `:templates` | Returns a vector of the IDs of the Statement Templates the Statement is valid for.

`validate-statement` also takes the following two keyword args:

| Keyword&nbsp;Argument  | Description
| :--               | :--
| `:all-valid?`     | If `false` (default), the Statement is considered valid if _any_ of the Statement Template is valid for it. If `true`, validity is if _all_ of the Templates are valid for it. Applicable to all function types except `:templates`.
| `:short-circuit?` | If `false` (default), returns error data for all invalid Templates; if `true`, returns data for only the first invalid Template found. Applicable to `:result`, `:assertion`, and `:printer`.

In addition to the main `valid-statement` function, there are additional validation functions that perform a specific type of validation, e.g. `validated-statement?` is always a predicate (and is in fact what `validate-statement` calls in `:predicate` mode).

The following is an example error message from `validate-statement-vs-template`, when `:fn-type` is set to `:printer`:
```
----- Statement Validation Failure -----
Statement ID: "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"
Template ID: http://foo.org/example/template

Template Verb property was not matched.
 template Verb:
   http://foo.org/verb
 statement Verb:
   http://example.com/xapi/verbs#sent-a-statement

Template rule was not followed:
  {:location "$.actor.member[*].name",
   :presence "included",
   :any ["Will Hoyt" "Milt Reder" "John Newman"],
   :none ["Shelly Blake-Plock" "Brit Keller" "Mike Anthony"]}
 failed: some evaluated values must be matchable
 statement values:
   no values found at location

-----------------------------
Total errors found: 2
```

The above error message indicates that the Statement's Verb property has an incorrect ID and does not match the rule (which requires that the Statement have a name property for its actor members).

### Statement Ref Templates

By default, Statement Ref Template validation is not supported; however, to allow for such support, `compile-profiles->validators` and `compile-profiles->fsms` take in an optional `:statement-ref-fns` argument. Its value needs to be a map of the following:

| Argument&nbsp;Map&nbsp;Key&nbsp;&nbsp; | Description
| :--                 | :--
| `:get-statement-fn` | A function that takes a Statement ID and returns a Statement, or `nil` if not found. This function will be called to return the Statement referenced by a `StatementRef` object.
| `:get-template-fn`  | A function that takes a Template ID and returns a Statement Template, or `nil` if not found. This function will be called to return the Template referenced by `ObjectStatementRefTemplate` or `ContextStatementRefTemplate`.

This system allows for flexibility when retrieving Statements and Templates by ID, e.g. `get-statement-fn` may be a function that calls out an LRS to retrieve Statements. For convenience, two functions are provided in the `persephone.template.statement-ref` namespace for use with `statement-ref-fns`:

| Statement&nbsp;Ref&nbsp;Function&nbsp;Name&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description
| :--                                 | :--
| `profile->id-template-map`          | Takes a Profile and returns a map between Template IDs and Templates.
| `statement-batch->id-statement-map` | Takes a Statement batch and returns a map between Statement IDs and Statements.

## Pattern Matching

Each Pattern is essentially a regular expression on Statement Templates, which can be composed from other Patterns. Internally, after compilation with `compile-profiles->fsms`, each Pattern is returned as a map of the following:
```clojure
  {"profile-id" {"pattern-id" {:id "pattern-id"
                               :dfa {...}
                               :nfa {...}}}}
```
with `:dfa` and `:nfa` being two different FSMs:

| Map&nbsp;Key&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description |
| :--         | :--
| `:dfa`      | A (mostly: see below) deterministic, minimized FSM used for efficient matching of Statements against a Pattern.
| `:nfa`      | A non-deterministic NFA with pattern metadata associated with each of its states. This is an optional value; if present, it is used to reconstruct the path from the primary pattern to the template when constructing match failure data.
| `:nfa-meta` | The metadata for the `:nfa` value; this is only present if `:nfa` is present.

NOTE: Unlike "true" DFAs, `:dfa` allows for some level of non-determinism, since a Statement may match against multiple Templates.

The `compile-profiles->fsms` functions have the following keyword arguments:

| Keyword&nbsp;Argument&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Description
| :--                    | :--
| `:statement-ref-fns`   | Same as in the Statement Template compilation functions.
| `:validate-profile?`   | Validates Profiles and checks that there are no clashing Profile or Pattern IDs. If validation fails, the function throws a `::assert/invalid-profile` exception.
| `:validate-not-empty?` | Asserts that at least one Pattern FSM exists after compilation, and that one exists for each Profile; if `true`, the function throws a `::assert/no-patterns` exception, e.g. if an empty Profile coll was provided or if `:selected-profiles` or `:selected-patterns` filtered out all Patterns. If `false`, then Statements may vacuously match since there would be no Patterns to fail matching against.
| `:compile-nfa?`        | If `:nfa` should be compiled; doing so will allow for detailed tracing of visited Templates and involved Patterns.
| `:selected-profiles`   | Which Profiles in the collection should be compiled.
| `:selected-patterns`   | Which Patterns in the Profiles should be compiled. Useful for selecting only one Pattern to match against.

There are five different types of Patterns, based on which of the five following properties they have. The `sequence` and `alternates` properties are arrays of identifiers, while `zeroOrMore`, `oneOrMore` and `optional` give a map of a single identifier. The following description are taken from the [Profile section of the Profile spec](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns):

| Property&nbsp;Name | Description
| :--          | :--
| `sequence`   | The Pattern matches if the Patterns or Templates in the array match in the order listed. Equivalent to the concatenation operation in a regex.
| `alternates` | The Pattern matches if any of the Templates or Patterns in the array match. Equivalent to the union operator (the `\|` operator in a regex string).
| `zeroOrMore` | The Pattern matches if the Template or Pattern matches one or more times, or is not matched against at all. Equivalent of the Kleene Star operation (the `*` operator in a regex string).
| `oneOrMore`  | The Pattern matches if the Template or Pattern matches at least one time. Equivalent of the `+` operator in a regex.
| `optional`   | The Pattern matches if the Template or Pattern matches exactly once, or not at all. Equivalent of the `?` operator in a regex.

The `match-statement` and `match-statement-batch` functions take in a compiled Profile collection (returned by `compile-profiles->fsms`), a state info map, and a Statement or Statement collection, respectively. They return a state info map for easy pipelining. The following is an example state info map:
```clojure
  {:accepts [["registration-key" "pattern-id"]]
   :rejects []
   :states-data {"registration-key"
                 {"pattern-id" #{{:state 1
                                  :accepted? true
                                  :visited ["template-id"]}}}}}
```
where the following are the values in the leaf `:states-data` map:

| Map&nbsp;Key | Description
| :--          | :--
| `:state`     | The state that the FSM is currently at.
| `:accepted?` | Whether the current state is an accept state; this indicates that the stream of Statements was accepted by the Pattern (though more Patterns may be read in).
| `:visited`   | A vector of template IDs that records the templates that were previously matched against. This is an optional value that is only present if the FSM map includes `:nfa` (since it is only used to reconstruct error traces).

The registration key can be a UUID string, the keyword `:no-registration`, or a pair of the registration and subregistration UUID string.

If the whole state info map is `nil`, then both match functions will begin at the start state of all FSMs in the compiled Profile map, assoc-ing the matching results of each Pattern to the Statement's registration key and that Pattern's ID. Likewise, if a particular registration key and Pattern ID are missing, and the next Statement being matched has that registration, then the matching will start at the start state for that particular pair.

If the state info for a particular registration key and Pattern ID pair is an empty set, then the FSM cannot read additional states anymore, so the Statement stream fails to conform to the Pattern. An input sequence is considered accepted if _any one_ of the `:accepted?` values in the set is `true`. The `:accepts` and `:rejects` values automatically record registration key and Pattern ID pairs as vectors that can be used in `assoc-in`, `update-in`, etc.

`match-statement` and `match-statement-batch` also take in an optional `:print?` keyword arg; if set to true, any match failure messages will be printed.

For more information about the technical implementation details (including about the composition, determinization, and minimization of FSMs), check out the internal documentation, especially in the `utils/fsm` namespace. It is recommended that you also read up on the mathematical theory behind FSMs via Wikipedia and other resources; useful articles include:
- [Deterministic finite automaton](https://en.wikipedia.org/wiki/Deterministic_finite_automaton)
- [Nondeterministic finite automaton](https://en.wikipedia.org/wiki/Nondeterministic_finite_automaton)
- [Thompson's construction](https://en.wikipedia.org/wiki/Thompson%27s_construction) (for NFA composition)
- [Powerset construction](https://en.wikipedia.org/wiki/Powerset_construction) (for NFA to DFA conversion)
- [DFA Minimization](https://en.wikipedia.org/wiki/DFA_minimization) (includes discussion of Brzozowski's algorithm, the algorithm used by this library.)

### So...does it fit the spec?

Persephone validates both Profiles and input Statements against the latest xAPI specs. However, there are some requirements that are deliberately not checked against:
- "All Statements following a primary Pattern MUST use the same registration." Other libraries developed at Yet, namely DATASIM, may assign multiple registrations to Statements following the same Pattern, e.g. to distinguish between Actors, so Persephone avoids validating against this requirement in order to be backwards compatible.
- "LRPs MUST send Statements following a Pattern ordered by Statement timestamp." This is enforced in the `match-statement-batch` function, but not in `match-statement`.

## Concepts???

While Concepts are an integral part of most xAPI profiles, this library does not concern itself with them. This library is strictly focused on structural validation using Statement Templates and Patterns and not on any ontological meaning given by Concepts. In other words, this is a syntax library, not a semantics library.
