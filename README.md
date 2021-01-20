# project-persephone

_"Only Persephone, daughter of Zeus and wife of Hades, could travel
between the Underworld and the world of the living. Project Persephone
is the liaison between our physical world and the world of the Semantic
Web."_

A Clojure library for validating xAPI Statements against xAPI Profiles. 

## Usage 

The `persephone` namespace has four methods:
- `profile-templates`: Returns a vector of Statement Templates (as EDN) from
a Profile (for use with `validate-statement`).
- `validate-statement`: Check an individual Statement against an
  individual Statement Template.
- `compile-profile`: Compile all primary Patterns from a given Profile into
  a form usable by the library.
- `read-next-statement`: Read a Statement (which may be part of a stream)
  against a compiled Pattern.

`check-individual-statement` returns a boolean while also printing on false.
All the other methods return data structures (maps for `compile-profile` and `read-next-statement` and a vector of maps for
`profile-templates`) that can be used later. It is _your_ responsibility as the
user to store the returned structure as an atom or other stateful form in your
application.

### Validation on Statement Template

Validating a Statement against a Statement Template involves three main 
aspects:
- Validating against Rules. To do so, we need to use the JSONPath given by
`location` (and possibly `selector`) to return a set of values from within the
Statement and match these values against the `presence`, `any`, `all` and
`none` property. More information can be found in the docstrings and the [xAPI Profile spec](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#statment-templates).
- Validating against Determining Properties (the verb, the object activity type,
and the four context activity types). They can be expressed as rules in which
the respective values from the Statement MUST be included and given by the
Statement Template.
- Validating against StatementRefs (object and context StatementRefTemplates).
These are arrays of StatementTemplate IRIs, which point to _more_ Statement
Templates that we need to validate against in a recursive manner. These
additional Statements are referenced by StatementRefs in the original 
Statement. This can potentially require querying this and other Profiles (and so that aspect of validation is, for now, unimplemented).

A user will use the `validate-statement` method to validate. Upon validating a 
Statement, `validate-statement` will either return `true` on success, or return 
`false` on failure while printing an error message. The following is an example 
error message from `validate-statement`:

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
 failed 'included': not all evaluated values were matchable
 statement values:
   no values found at location

-----------------------------
Total errors found: 2
```

The above error message indicates that the statement's Verb property has an
incorrect ID and does not match the rule (which requires that the Statement
have a name property for all of its actor members).

### Validation on Pattern

Each Pattern is essentially a regular expression on Statement Templates, which
can be composed from other Patterns. Internally, after compilation using the
`compile-profile` method, each Pattern is implemented as a finite state
machine (FSM), which is mathematically equivalent to regular expressions.

There are five different types of Patterns, based on which of the five
following properties they have. The `sequence` and `alternates` properties are
arrays of identifiers, while `zeroOrMore`, `oneOrMore` and `optional` give
a map of a single identifier. The following description are taken from the
[Profile section of the xAPI Profile spec](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns):
- `sequence`: The Pattern matches if the Patterns or Templates in the array 
match in the order listed. Equivalent to the concatenation operation in a regex.
- `alternates`: The Pattern matches if any of the Templates or Patterns in the
array match. Equivalent to the union operator (`|` in a regex string).
- `zeroOrMore`: The Pattern matches if the Template or Pattern matches one or
more times, or is not matched against at all. Equivalent of the Kleene Star operation (`\*` in a regex string).
- `oneOrMore`: The Pattern matches if the Template or Pattern matches at least
one time. Equivalent of the `+` operator in a regex.
- `optional`: The Pattern matches if the Template or Pattern matches exactly
once, or not at all. Equivalent of the `?` operator in a regex.

Using `read-next-statement`, a compiled Pattern can read a stream of Statements
(e.g. from a Kafka stream); in addition to a Statement, the function also
takes a map representing state info, which contains the following fields:
- `:state` - The current set of states that the Pattern FSM is at (which is implemented as a string).
- `:accepted?` - Whether the current state is an accept state; this indicates that the stream of Statements was accepted by the Pattern (though more Patterns may be read in).

`read-next-statement` returns an updated version of the state info map, with the new `state` value set after the FSM reads the Statement. If the `state` value is `nil`, that indicates that the FSM cannot read the Statement, so it rejects it.

If the state info map is `nil`, then `read-next-statement` will begin at the start state of the FSM. If the `state` value is `nil`, then `read-next-statement` will return the same map, since it cannot read any more states.

For more information about the technical details (including how the composition
of FSMs and the reading of inputs is done), please check out the internal
documentation, especially in the `utils/fsm` namespace. It is recommended that
you also read up on the mathematical theory behind FSMs via Wikipedia and other
resources; important concepts include deterministic and non-deterministic finite automata, Thompson's Algorithm for NFA composition, and the powerset construction for NFA to DFA conversion.

### What about Concepts?

While Concepts are an integral part of most xAPI profiles, this library does
not concern itself with them. This library is strictly focused on structural
validation using Statement Templates and Patterns and not on any ontological
meaning given by Concepts. In other words, this is a syntax library, not a
semantics library.

## TODO

- Migrate JSONPath library to Jayway Java implementation
    - Current lib fails when using recursive descent ("..") for numerical values.
    - Current lib does not support string-valued keys.
- Deal with profile-external Templates and Patterns (requires a triple store)
    - Deal with StatementRef properties.
- Deal with subtleties of reading Statements:
    - Statements MUST be read in timestamp order.
    - Statements have additional grouping requirements given by the
    `registration` and `subregistration` properties.
    - Statements may be accepted by multiple StatementTemplates at the same time. Current `read-next-statement` assumes that Statements can only be accepted by one StatementTemplate at a time.
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
