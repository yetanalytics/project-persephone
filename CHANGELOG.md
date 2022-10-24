# Change Log

## 0.8.3 - 2022-10-24
- Update GitHub CI and CD to remove deprecation warnings

## 0.8.2 = 2022-06-30
- Deprecate `stmt-error` and `util.statement.subreg-iri` in favor of `statement-error` and `subregistration-iri`, respectively (while keeping the values identical).
- Add `pattern.failure` namespace and specs such as `::failure/failure` and `state-info-meta-spec` for failure info/state info metadata.
- Add docstrings to API specs.
- (Test-only) Add spec instrumentation fixture and apply it to API function tests.

## 0.8.1 - 2022-03-08
- Implement Clojars deployment
- Update dependencies

## 0.8.0 - 2021-12-21
- Main idea: Update the Template API in order to match the Pattern API updates
- Update Template API functions
  - Remove `validate-statement-vs-template` functions
  - Change `template->validator` to `compile-templates->validators`
    - Accepts multiple templates; can filter using `:selected-templates`
  - Change `profile->validator` to `compile-profiles->validators`
    - Accepts multiple profiles; can filter using `:selected-profiles` and `:selected-templates`
   - Change `validate-statement-vs-profile` to just `validate-statement`
     - Add multiple public validation helper functions that `validate-statement` calls
     - Rename kwargs: `:option` and `:result` OCaml-isms to `:filter` and `:errors`, respectively
     - Add `:all-valid?` kwarg to specify that ALL templates have to match for the statement
     - Add `:short-circuit?` kwarg to specify that only the first validation error should be returned
- Remove automatic JSON string to EDN coercion (**note:** affects Pattern API also)
- Apply Template ID clash check to `compile-patterns->fsms` (**note:** affects Pattern API)
- Update template validation result map
  - `:rule` can now also be `:prop` or `:sref` to distinguish between different types of errors/failures better
  - `:vals` field in template error map is now _always_ be a vector, even for Statement Ref errors
  - Change`:determining-property` and `:prop-vals` to `:det-prop` and `:match-vals`, respectively
  - Change `:failure` to `:sref-failure`
 - Move profile, template, and statement asserts and coercions to util namespaces.
 - Move `profile->id-template-map` and `statement-batch->id-statement-map` convenience fns to the new `template.statement-ref` namespace.
 - Make `?statement-ref-opts` a non-optional (albeit nilable) arg for `template/create-template-predicate` and `template/create-template-validator`.
 - Spec fixes:
   - FSMs/Patterns:
     - Update `fsm-spec/valid-transition-src-states?` such that source states only have to be a _subset_, not equals to, the total states.
     - Add `:meta?` to spec for `fsm/plus-nfa`.
     - Correct instrumentation for `read-next` to work with NFAs as well as DFAs.
     - Fix subregistration specs in `utils/statement`.
   - Templates:
     - Let `statement-ref/get-template-fn` and `statement-ref/get-statement-fn` specs to allow for `nil` returns and fix arg generation.
      - Add missing `:every-val-present?` entry to `::template/pred` spec.
     - Fix `validator-spec`, `create-template-validator` and `create-template-predicate` template specs.
   - Persephone API
     - Fix typo in `::persephone/validator-fn` and `::persephone/predicate-fn` spec names.
     - Align specs for `validate-statement-errors` and `validate-statement` (for `:fn-type :errors`).
     - Add missing `::persephone/print?` spec for `match-statement`.
     - Fix bugs relating to the `::persephone/error` spec.

## 0.7.4 - 2021-12-15
- Fix bug where the `:selected-profiles` kwarg for `compile-profiles->fsms` did not work.

## 0.7.3 - 2021-12-15
- Print match failures (if `:print?` is true) even if it was not the first time that failure was encountered.
- Add `:nfa-meta` key to the pattern FSMs map (in case meta is lost from `:nfa`).

## 0.7.2 - 2021-12-14
- Make FSM compilation thread-safe by removing use of internal atoms.
- Update non-arg `s/cat` specs to `s/tuple` specs.

## 0.7.1 - 2021-12-14
- Expose `get-subregistration-id` function in `utils.statement` namespace.

## 0.7.0 - 2021-12-14
- Main idea: Completely overhaul the Pattern API.
- Remove the following functions:
  - `profile->fsms`
  - `match-statement-vs-pattern`
  - `match-statement-vs-profile`
  - `match-statement-batch-vs-pattern`
  - `match-statement-batch-vs-profile`
- Add the following functions (see README and docstrings for details on the new functions):
  - `compile-profiles->fsms`, which accepts a coll of Profiles and returns a nested map from Profile ID to Pattern ID to FSM map.
  - `match-statement`, which accepts a compiled Profile coll, a state info map (which has been updated; see README for details), a Statement, and an optional `:print?` keyword arg.
  - `match-statement-batch`, which is the same as above except it accepts a Statement coll.
  - Note that the above matching functions now return an `{:error ...}` map instead of throwing an exception upon error.
- Change the following functions in the `pattern` namespace:
  - `update-children` has been made private.
  - `pattern->fsm` has been made private.
  - `pattern-tree->fsm` has been renamed to `pattern-tree->dfa`.
  - `pattern-tree->nfa` has been added.
  - `read-visited-templates` has been added.
- Add new `util.profile`, `util.statement`, and `pattern.errors` namespaces.
- Update `fsm/read-next` to:
  - dispatch on the `:type` field of the FSM.
  - accept and return a set of maps, instead of a single map.
  - accept an optional `start-opts` map (currently `:record-visit?` is the only accepted field).
- NFA construction functions now accept an optional `meta?` argument; if `true`, the returned NFAs have a `:states` map in the metadata.

## 0.6.0 - 2021-04-26
- Add Statement batch validation versus Patterns.
- Add support for sub-registrations.
- Add support for Statement Ref Templates.
- Further optimization of Statement validation.
- Redo directory and namespace organization:
  - Remove `gen` directory and gentest-specific runners
  - Remove the word "validate" from `pattern-validate` and `template-validate`
  - Move `fsm` and `fsm-spec` to `pattern` directory
  - Move `errors` to `template` directory
  - Separate template predicates into their own `predicate` namespace

## 0.5.2 - 2021-04-12
- Fix incorrect Determining Properties logic.
- Minor changes/refactors to error messages.

## 0.5.1 - 2021-04-07
- Updated deps to remove those that affected downstream usage:
  - Removed jsonschema npm dependency.
  - Removed jitpack Maven repo.

## 0.5.0 - 2021-03-08
- Update persephone API function names and optional args to be in line with each other.
- Statement Templates are no longer compiled on each Statement read.
- Compiled JSONPaths are now stored in a stateful cache for quick access.
- Optimized validation function creation:
  - Presence colls are now turned into sets during compilation.
  - Removed use of spec and `explain-data`.
- Optimized FSM creation:
  - Removed pattern matching from `pattern-validation/pattern->fsm`.
  - Removed `fsm/move-nfa` as a standalone function.
  - `fsm/epsilon-closure` now uses transients internally.
- Optimized Pathetic dep (see [api-refactor](https://github.com/yetanalytics/pathetic/pull/3)).

## 0.4.0 - 2021-02-25
- Added FSM specs and generative tests in the `gen` namespace.
- Added DATASIM tests in the `gen` namespace to test API functions on statement streams.
- Generative tests have their own aliases in `deps.edn`.
- Modified `match-next-statement` to handle multiple Patterns and Pattern outputs.

## 0.3.0 - 2021-01-29
- Add ClojureScript compatibility
- Update JSONPath dependencies to Java and JS-based libs

## 0.2.0 - 2021-01-21
- Update finite state machine code
  - Simplified internal representation to remove Ubergraph dependency
  - NFA to DFA conversion added
  - DFA minimization added

## 0.1.0 - 2019-08-08
- Initial alpha version
