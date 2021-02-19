# project-persephone

_"Only Persephone, daughter of Zeus and wife of Hades, could travel
between the Underworld and the world of the living. Project Persephone
is the liaison between our physical world and the world of the Semantic
Web."_

A Clojure library for validating xAPI Statements against xAPI Profiles. 

## Usage 

The `persephone` namespace contains functions that perform two main tasks.
The first is to validate Statements against Statement Templates, which is
accomplished via these two functions:
- `validate-statement-vs-template`: Takes a Statement Template and a
  Statement as arguments, then validates the Statement against the
  properties and rules of the Template (as described in the
  [xAPI Profile spec](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#statment-templates)).
- `validate-statement-vs-profile`: Takes a xAPI Profile and a Statement
  as arguments, then validates the Statement against the Statement
  Templates of the Profile. The Statement is considered valid for the
  whole Profile if it is valid for at least one of its Templates.

The other task is to validate streams/collections of Statements against
Patterns, which are accomplished by the following functions:
- `compile-profile`: Takes a Profile and compiles it to a form usable by
  the library to validate Statement streams. Returns a map between
  primary Pattern IDs and their respective compiled Patterns.
- `match-next-statement`: Takes a compiled Profile, a current state info
  map that is the return value of a previous `match-next-statement` call,
  and a Statement, and matches the Statement against the Pattern according to the [xAPI Pattern specification](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns).
- `match-next-statement*`: Takes a single compiled Pattern, a current
  state info map that is the return value of a previous `match-next-statement*`
  call, and a Statement, and matches the Statement against that Pattern.

### Validation on Statement Template

Validating a Statement against a Statement Template involves three main 
aspects:
- Validating against Rules. To do so, we need to use the JSONPath given by
`location` (and possibly `selector`) to return a set of values from within
the Statement and match these values against the `presence`, `any`, `all`
and `none` property.
- Validating against Determining Properties (the verb, the object activity 
type, and the four context activity types). They can be expressed as rules 
in which the respective values from the Statement MUST be included and
given by the Statement Template.
- Validating against StatementRefs (object and context
StatementRefTemplates). These are arrays of StatementTemplate IRIs, which 
point to _more_ Statement Templates that we need to validate against in a 
recursive manner. These additional Statements are referenced by 
StatementRefs in the original Statement. This can potentially require 
querying this and other Profiles (and so that aspect of validation is, for 
now, unimplemented).

The `validate-statement-vs-template` and `validate-statement-vs-profile` functions take the optional argument `:fn-type`, which can be set to the
following:
- `:predicate` - Returns `true` for a valid Statement, `false` otherwise.
  Default for both functions.
- `:option` - Returns the statement if it's valid, `nil` otherwise. (This is
  similar to the Option type in OCaml or the Just type in Haskell.)
- `:result` - Returns the validation error data if the Statement is invalid,
  `nil` otherwise. (This is similar to the Result type in OCaml.)
- `:assertion` - Throws an exception if the Statement is invalid, returns
  `nil` otherwise.

`validation-statement-vs-template` takes an addition option for `:fn-type`:
- `:printer` - Prints an error message when the Statement is invalid.
  Always returns `nil`.

`validation-statement-vs-profile` takes an addition option for `:fn-type`:
- `:templates` - Returns a vector of the IDs of the Statement Templates the
  Statement is valid for.

Both functions have an additional optional argument: `validate-template?`
and `validate-profile?`, respectively. By default they are set to `true`,
in which case they validate the syntax of the Template/Profile. Set this to
false only when you know what you're doing!

The following is an example error message from 
`validate-statement-vs-template`, when `:fn-type` is set to `:printer`:

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

The above error message indicates that the statement's Verb property has an
incorrect ID and does not match the rule (which requires that the Statement
have a name property for its actor members).

### Validation on Pattern

Each Pattern is essentially a regular expression on Statement Templates, 
which can be composed from other Patterns. Internally, after compilation 
with `compile-profile`, each Pattern is implemented as a finite state
machine (FSM), which is mathematically equivalent to regular expressions.

There are five different types of Patterns, based on which of the five
following properties they have. The `sequence` and `alternates` properties 
are arrays of identifiers, while `zeroOrMore`, `oneOrMore` and `optional` give a map of a single identifier. The following description are taken from the
[Profile section of the xAPI Profile spec](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns):
- `sequence`: The Pattern matches if the Patterns or Templates in the array 
match in the order listed. Equivalent to the concatenation operation in a 
regex.
- `alternates`: The Pattern matches if any of the Templates or Patterns in 
the array match. Equivalent to the union operator (`|` in a regex string).
- `zeroOrMore`: The Pattern matches if the Template or Pattern matches one or
more times, or is not matched against at all. Equivalent of the Kleene Star
operation (`\*` in a regex string).
- `oneOrMore`: The Pattern matches if the Template or Pattern matches at 
least one time. Equivalent of the `+` operator in a regex.
- `optional`: The Pattern matches if the Template or Pattern matches exactly
once, or not at all. Equivalent of the `?` operator in a regex.

Using `match-next-statement`, a compiled Profile can read a stream of 
Statements (e.g. from a Kafka stream). Each call to `match-next-statement`
returns a map between Statement registration values and a map between
Pattern IDs to state info. Each state info map has the following fields:
- `:state` - The state that the FSM is currently at.
- `:accepted?` - Whether the current state is an accept state; this indicates
  that the stream of Statements was accepted by the Pattern (though more Patterns may be read in).
- `:rejected?` - Whether the FSM can no longer read additional states.
  If this is set to `true`, this signifies that the Statement stream fails
  to conform to the Pattern.

That state info map is the return value for `match-next-statement*`, which
is designed to be used with single Patterns instead of a whole Profile.

If the state info map is `nil`, then `read-next-statement*` will begin at the
start state of the FSM. If the `state` value is `nil`, then 
`read-next-statement*` will return the same map, since it cannot read any m
ore states; otherwise, it returns an updated map with a new `:state` value.

`match-next-statement` attempts to call `match-next-statement*` on each
compiled Pattern in the Pattern map. If a sequence of Statements with
different registrations is passed to `match-next-statement`, then each
set of same-registration Patterns is treated as its own stream, hence the
need for a mapping between registrations and state info.

For more information about the technical implementation details (including 
about the composition, determinization, and minimization of FSMs), please 
check out the internal documentation, especially in the `utils/fsm` 
namespace. It is recommended that you also read up on the mathematical 
theory behind FSMs via Wikipedia and other resources; important concepts 
include deterministic and non-deterministic finite automata, Thompson's 
Algorithm for NFA composition, the powerset construction for NFA to DFA 
conversion, and Brzozowski's algorithm for DFA minimization.

### What about Concepts?

While Concepts are an integral part of most xAPI profiles, this library does
not concern itself with them. This library is strictly focused on structural
validation using Statement Templates and Patterns and not on any ontological
meaning given by Concepts. In other words, this is a syntax library, not a
semantics library.

## TODO

- Deal with profile-external Templates and Patterns (requires a triple store)
    - Deal with StatementRef properties.
- Deal with subtleties of reading Statements:
    - Statements MUST be read in timestamp order.
    - Statements have additional grouping requirements given by the
    `registration` and `subregistration` properties.
    - Statements may be accepted by multiple StatementTemplates at the same time.
      Current `read-next-statement` assumes that Statements can only be accepted by one StatementTemplate at a time.
- Integrate project-pan and xapi-schema
    - We need these libraries to validate Statements, Templates and Patterns
      (or else we potentially get exceptions, infinite loops, etc.)
- Work on error messaging/logging
    - Perform actual logging (rather than simply printing to the console).
    - Display better errors (rather than simply the Statement ID) if a Pattern
      cannot accept a Statement. (This requires work on the FSM library.)
    - Catch exceptions and properly display error messages.
- Squish bugs (see Issue tracker).

## License

Copyright © 2019 Yet Analytics

Distributed under the Eclipse Public License version 1.0.
