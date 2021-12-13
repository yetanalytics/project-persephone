# project-persephone

[![CI](https://github.com/yetanalytics/project-persephone/actions/workflows/main.yml/badge.svg)](https://github.com/yetanalytics/project-persephone/actions/workflows/main.yml)

_"Only Persephone, daughter of Zeus and wife of Hades, could travel between the Underworld and the world of the living. Project Persephone is the liaison between our physical world and the world of the Semantic Web."_

A Clojure library for validating xAPI Statements against xAPI Profiles. 

## Usage 

The `persephone` namespace contains functions that perform two main tasks. The first is to validate Statements against Statement Templates, which is accomplished via these two functions:

- `validate-statement-vs-template`: Taking a compiled Statement Template and a Statement as arguments, validates the Statement against the properties and rules of the Template (as described in the [xAPI Profile spec](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#statment-templates)).

- `validate-statement-vs-profile`: Taking a compiled xAPI Profile and a Statement as arguments, validates the Statement against all the Statement Templates of the Profile. The Statement is considered valid for the whole Profile if it is valid for at least one Template.

To compile a Statement Template or Profile for use with these functions, the `template->validator` and `profile->validator` functions are used, respectively.

The other task is to validate streams/collections of Statements against
Patterns, which are compiled into so-called _finite-state machines (FSMs)_. That is accomplished by the following functions:

- `match-statement`: Taking a compiled Pattern collection, a map containing the current FSM state info, and a Statement, matches the Statement against that Pattern according to the [xAPI Pattern specification](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns) and returns updated state info.

- `match-statement-batch`: Same as `match-statement`, but takes a batch (i.e. collection) of Statements as an argument, automatically sorting them by timestamp.

To compile Profiles into FSMs, use the `compile-profiles->fsms` function. This will return a map from Profile IDs to Pattern IDs to Pattern FSM maps, which can then be passed in as the first argument to `match-statement`.

### Validation on Statement Template

Validating a Statement against a Statement Template involves three aspects:

- Validating against Rules. To do so, we need to use the JSONPath given by `location` (and possibly `selector`) to return a set of values from within the Statement and match these values against the `presence`, `any`, `all` and `none` property.

- Validating against Determining Properties (the verb, the object activity  type, and the four context activity types). They can be expressed as rules in which the respective values from the Statement MUST be included and given by the Statement Template.

- Validating against StatementRefs (object and context StatementRefTemplates). These are arrays of StatementTemplate IRIs, which  point to _more_ Statement Templates that we need to validate against in a  recursive manner. These additional Statements are referenced by  StatementRefs in the original Statement. This can potentially require querying this and other Profiles; thus, that aspect of validation is, for now, unimplemented.

The `validate-statement-vs-template` and `validate-statement-vs-profile` functions take the optional argument `:fn-type`, which can be set to the
following:

- `:predicate` - Returns `true` for a valid Statement, `false` otherwise. Default for both functions.

- `:option` - Returns the statement if it's valid, `nil` otherwise. (This is similar to the Option type in OCaml or the Just type in Haskell.)

- `:result` - Returns the validation error data if the Statement is invalid, `nil` otherwise. (This is similar to the Result type in OCaml.)

- `:assertion` - Throws an exception if the Statement is invalid, returns `nil` otherwise.

`validation-statement-vs-template` takes an addition option for `:fn-type`:

- `:printer` - Prints an error message when the Statement is invalid. Always returns `nil`.

`validation-statement-vs-profile` takes an additional option for `:fn-type`:

- `:templates` - Returns a vector of the IDs of the Statement Templates the Statement is valid for.

Both compilation functions, `template->validator` and `profile->validator`, have an additional optional argument: `validate-template?` and `validate-profile?`, respectively. By default they are set to `true`, in which case they validate the syntax of the Template/Profile. Set this to `false` only when you know what you're doing!

The following is an example error message from `validate-statement-vs-template`, when `:fn-type` is set to `:printer`:

```
----- Invalid Statement -----
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

### Validation on Pattern

Each Pattern is essentially a regular expression on Statement Templates, which can be composed from other Patterns. Internally, after compilation with `compile-profiles->fsms`, each Pattern is returned as a map of the following:
```clojure
  {"profile-id" {"pattern-id" {:id "pattern-id"
                               :dfa {...}
                               :nfa {...}}}}
```
with `:dfa` and `:nfa` being two different FSMs:
- `:dfa` is a (mostly: see below) deterministic, minimized FSM used for efficient matching of Statements against a Pattern.
- `:nfa` is a non-deterministic NFA with pattern metadata associated with each of its states. This is an optional value; if present, it is used to reconstruct the path from the primary pattern to the template when constructing match failure data.

(NOTE: Unlike "true" DFAs, `:dfa` allows for some level of non-determinism, since a Statement may match against multiple Templates.)

The `compile-profiles->fsms` functions have the following keyword arguments:
- `:statement-ref-fns` - Same as in the Statement Template compilation functions.
- `:validate-profile?` - Validates Profiles and checks that there are no clashing Profile or Pattern IDs.
- `:compile-nfa?` - If `:nfa` should be compiled; doing so will allow for detailed tracing of visited Templates and involved Patterns.
- `:selected-profiles` - Which Profiles in the collection should be compiled.
- `:selected-patterns` - Which Patterns in the Profiles should be compiled. Useful for selecting only one Pattern to match against.

There are five different types of Patterns, based on which of the five following properties they have. The `sequence` and `alternates` properties are arrays of identifiers, while `zeroOrMore`, `oneOrMore` and `optional` give a map of a single identifier. The following description are taken from the [Profile section of the Profile spec](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns):
- `sequence` - The Pattern matches if the Patterns or Templates in the array match in the order listed. Equivalent to the concatenation operation in a regex.
- `alternates` - The Pattern matches if any of the Templates or Patterns in the array match. Equivalent to the union operator (the `|` operator in a regex string).
- `zeroOrMore` - The Pattern matches if the Template or Pattern matches one or more times, or is not matched against at all. Equivalent of the Kleene Star operation (the `*` operator in a regex string).
- `oneOrMore` - The Pattern matches if the Template or Pattern matches at least one time. Equivalent of the `+` operator in a regex.
- `optional` - The Pattern matches if the Template or Pattern matches exactly once, or not at all. Equivalent of the `?` operator in a regex.

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
- `:state` - The state that the FSM is currently at.
- `:accepted?` - Whether the current state is an accept state; this indicates that the stream of Statements was accepted by the Pattern (though more Patterns may be read in).
- `:visited` - A vector of template IDs that records the templates that were previously matched against. This is an optional value that is only present if the FSM map includes `:nfa` (since it is only used to reconstruct error traces).

The registration key can be a UUID string, the keyword `:no-registration`, or a pair of the registration and subregistration UUID string.

If the whole state info map is `nil`, then both match functions will begin at the start state of all FSMs in the compiled Profile map, assoc-ing the matching results of each Pattern to the Statement's registration key and that Pattern's ID. Likewise, if a particular registration key and Pattern ID are missing, and the next Statement being matched has that registration, then the matching will start at the start state for that particular pair.

If the state info for a particular registration key and Pattern ID pair is an empty set, then the FSM cannot read additional states anymore, so the Statement stream fails to conform to the Pattern. An input sequence is considered accepted if _any one_ of the `:accepted?` values in the set is `true`. The `:accepts` and `:rejects` values automatically record registration key and Pattern ID pairs as vectors that can be used in `assoc-in`, `update-in`, etc.

For more information about the technical implementation details (including about the composition, determinization, and minimization of FSMs), check out the internal documentation, especially in the `utils/fsm` namespace. It is recommended that you also read up on the mathematical theory behind FSMs via Wikipedia and other resources; useful articles include:
- [Deterministic finite automaton](https://en.wikipedia.org/wiki/Deterministic_finite_automaton)
- [Nondeterministic finite automaton](https://en.wikipedia.org/wiki/Nondeterministic_finite_automaton)
- [Thompson's construction](https://en.wikipedia.org/wiki/Thompson%27s_construction) (for NFA composition)
- [Powerset construction](https://en.wikipedia.org/wiki/Powerset_construction) (for NFA to DFA conversion)
- [DFA Minimization](https://en.wikipedia.org/wiki/DFA_minimization) (includes discussion of Brzozowski's algorithm, the algorithm used by this library.)

### Statement Ref Templates

By default, Statement Ref Templates are not supported; however, to allow for such support, `template->validator`, `profile->validator`, and `profile->fsms` take in an optional `:statement-ref-fns` argument. `:statement-ref-fns` needs to be a map consisting of the following:
- `:get-statement-fn`: A function that takes a Statement ID and returns a Statement, or `nil` if not found. This function will be called to return the Statement referenced by a `StatementRef` object.
- `:get-template-fn`: A function that takes a Template ID and returns a Statement Template, or `nil` if not found. This function will be called to return the Template referenced by `ObjectStatementRefTemplate` or `ContextStatementRefTemplate`.

This system allows for flexibility when retrieving Statements and Templates by ID, e.g. `get-statement-fn` may be a function that calls out an LRS to retrieve Statements. For convenience, the API provides two functions for use with `statement-ref-fns`:
- `profile->id-template-map`: Takes a Profile and returns a map between Template IDs and Templates.
- `statement-batch->id-statement-map`: Takes a Statement batch and returns a map between Statement IDs and Statements. Intended to be used with `match-statement-batch-vs-pattern` and `match-statement-batch-vs-profile`.

### So...does it fit the spec?

Persephone validates both Profiles and input Statements against the latest xAPI specs. However, there are some requirements that are deliberately not checked against:
- "All Statements following a primary Pattern MUST use the same registration." Other libraries developed at Yet, namely DATASIM, may assign multiple registrations to Statements following the same Pattern, e.g. to distinguish between Actors, so Persephone avoids validating against this requirement in order to be backwards compatible.
- "LRPs MUST send Statements following a Pattern ordered by Statement timestamp." This is enforced in the `match-statement-batch` function, but not in `match-statement`.

### What about Concepts?

While Concepts are an integral part of most xAPI profiles, this library does not concern itself with them. This library is strictly focused on structural validation using Statement Templates and Patterns and not on any ontological meaning given by Concepts. In other words, this is a syntax library, not a semantics library.

## TODO

- Deal with profile-external Templates and Patterns (requires a triple store).
- Squish any bugs (see Issue tracker).

## License

Copyright © 2019-2021 Yet Analytics, Inc.

Distributed under the Apache License version 2.0.
