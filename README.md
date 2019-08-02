# project-persephone

A Clojure library for validating xAPI Statements against xAPI Profiles. 

## Usage 

Given a profile, use the `convert-profile` function in the core namespace
to convert a Profile (in JSON-LD or EDN format) into a finite state machine
that can be used by the library.

The FSM can now read, one at a time, Statements, and update its current
state. It will also return whether the Statement was accepted by the FSM and
whether the FSM is at an accept state.

### Validation on Statement Template

Validating a Statement against a Statement Template involves three main 
aspects:
- Validating against Rules. To do so, we need to use the JSONPath given by
`location` (and possibly `selector`) to return a set of values from within the
Statement and match these values against the `presence`, `any`, `all` and
`none` property. More information can be found in the docstrings and the xAPI
Profile spec.
- Validating aginst Determining Properties (the verb, the object activity type,
and the four context activity types). They can be expressed as rules in which
the respective values from the Statement MUST be included and given by the
Statement Template.
- Validating against StatementRefs (object and context StatementRefTemplates).
These are arrays of StatementTemplate IRIs, which point to _more_ Statement
Templates that we need to validate against in a recursive manner. These
additional Statements are referenced by StatementRefs in the original 
Statement. This can potentially require quering this and other Profiles.

Upon validating a Statement, `validate-statement` will either return true on
success or false on failure while printing an error message. The following is
an example error messge:

```
----- Invalid Statement -----
Statement ID: "fd41c918-b88b-4b20-a0a5-a4c32391aaa0"

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

Every pattern is represented as a regular expression on a sequence of 
Statements, which can be represented by a finite state machine (FSM). Each 
pattern type is simply a regular expression (which we can then compose):
- `sequence`: ABC
- `alternates`: A|B|C
- `optional`: A?
- `oneOrMore`: A+
- `zeroOrMore`: A\*

## TODO

- Deal with profile-external Templates and Patterns (requires a triple store)
- Deal with StatementRef properties.
- Deal with subtleties of reading Statements:
    - Statements MUST be read in timestamp order.
    - Statements have additional grouping requirements given by the
    `registration` and `subregistration` properties.
- Create a demo of the library with a Profile and a set of Statements.

## License

Copyright © 2019 Yet Analytics

Distributed under the Eclipse Public License version 1.0.
