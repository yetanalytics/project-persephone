# project-persephone

A Clojure library for validating xAPI Statements against xAPI Profiles. 

## Gameplan

### Profile Input Processing

1. Convert from JSON to EDN.
2. ???
3. Profit!

### Validation on Statement Template

Validating a Statement against a Statement Template involves several aspects:
- Validating aginst Determining Properties (the verb, the object activity type,
and the four context activity types). There are all IRIs or arrays of IRIs, so
we only need to do a string comparison (more or less).
- Validating against StatementRefs (object and context StatementRefTemplates).
These are arrays of StatementTemplate IRIs, which point to _more_ Statement
Templates that we need to validate against in a recursive manner. These
additional Statements are referenced by StatementRefs in the original 
Statement. This can potentially require quering this and other Profiles.
- Validating against Rules. To do so, we need to use the JSONPath given by
`location` (and possibly `selector`). and match against `presence` keywords,
`any`, `all` and `none`.

### Validation on Pattern

Every pattern is basically a regular expression on a sequence of Statements,
which can be represented by a finite state machine (FSM). Each pattern type
is simply a regular expression (which we can then compose):
- `sequence`: ABC
- `alternates`: A|B|C
- `optional`: A?
- `oneOrMore`: A+
- `zeroOrMore`: A\*
To build our FSM, we will use a Dativity process model (because Will likes it),
where we have one Role - the Pattern - whose Actions are each Statement 
Templates (once expanded out). Each Data point is equivalent to a node in an
FSM (preceding nodes = "required" data; succeeding nodes = "produced" data).

An additional challenge is that we may need to query external profiles for
Patterns and Statement Templates.

## License

Copyright © 2019 Yet Analytics

Distributed under the Eclipse Public License version 1.0.
