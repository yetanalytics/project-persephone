# Change Log

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
