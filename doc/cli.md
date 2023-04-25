# Command Line Interface

Persephone supports a CLI with two commands `validate` and `match`, which performs Statement Template validation and Pattern matching, respectively.

To use the CLI, first run `make bundle`, then `cd` to `target/bundle`. You will then be able to run `/bin/validate.sh` and `/bin/match.sh` from there, which will accept arguments for Profiles, Statements, and so on. 

Note that Profile and Statement arguments accept URIs, e.g. filepaths, not the JSON data itself. In addition, some arguments (e.g. `--profile`) may be repeated to allow the command to accept multiple arguments.

The `validate` command validates a single Statement against Statement Templates in one or more Profiles. The following table shows its arguments:

| CLI&nbsp;Command&nbsp;Argument | Description
| :--                          | :--
| `-p, --profile URI`          | Profile URI filepath/location; must specify one or more.
| `-i, --template-id IRI`      | IDs of Statement Templates to validate against; can specify zero or more.
| `-s, --statement URI`        | Statement filepath/location; must specify one.
| `-e, --extra-statements URI` | Extra Statement batch filepath/location; can specify zero or more. If specified, activates [Statement Ref property](library.md#statement-ref-templates) validation, where the referred object/context Statement exists in this batch and its Template exists in a provided Profile.
| `-a, --all-valid`            | If set, the Statement is not considered valid unless it is valid against ALL Templates. Otherwise, it only needs to be valid against at least one Template.
| `-c, --short-circuit`        | If set, then print on only the first Template the Statement fails validation against.Otherwise, print for all Templates the Statement fails against.
| `-h, --help`                 | Display the help menu.

The `match` command matches a Statement batch against Patterns in one or more Profiles. The following table shows its arguments:

| CLI&nbsp;Command&nbsp;Argument | Description
| :--                    | :--
| `-p, --profile URI`    | Profile filepath/location; must specify one or more.
| `-i, --pattern-id IRI` | IDs of Patterns to match against; can specify zero or more.
| `-s, --statement URI`  | Statement filepath/location; must specify one or more.
| `-n, --compile-nfa`    | If set, compiles the Patterns into a non-deterministic finite automaton (NFA) instead of a deterministic one, allowing for more detailed error traces at the cost of decreased performance.
| `-h, --help`           | Display the help menu.

## Example: `validate` command

In the examples in this and the next section, assume that the `test-resources/sample_profiles` and `test-resources/sample_statements` directories were copied into `target/bundle`, i.e. the location where we run our CLI commands.

For our first example, we can validate the `calibration_1.json` Statement against the Statement Templates in the `calibration.jsonld` Profile:
```
% ./bin/validate.sh \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json
```
This command will run, print nothing, and exit with exit code 0, indicating successful validation.

We can also add additional Profiles as so:
```
% ./bin/validate.sh \
  --profile sample_profiles/calibration.jsonld \
  --profile test-resources/sample_profiles/catch.json \
  --statement sample_statements/calibration_1.json
```
and it will output the same result, as the Statement will still match against the `calibration.jsonld` Profile, even though it will fail validation against all Templates in the `catch.json` profile.

To see an example of failed validation, set the `--all-valid` argument so that the Statement has to be valid against all Templates:
```
% ./bin/validate.sh \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --all-valid
```
This command will run and print the following message to stdout:
```
----- Statement Validation Failure -----
Template ID:  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
Statement ID: 00000000-4000-8000-0000-000000000000

Template Verb property was not matched.
 template Verb:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#didnt
 statement Verb:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#did

Template rule was not followed:
  {:any [\"https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-2\"],
   :location \"$.object.id\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-1

Template rule was not followed:
  {:any [\"Activity 2\"],
   :location \"$.object.definition.name.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   Activity 1

Template rule was not followed:
  {:any [\"The second Activity\"],
   :location \"$.object.definition.description.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   The first Activity

-----------------------------
Total errors found: 7
```
and exit with exit code 1. If we set the `--short-circuit` command along with `--all-valid`, then only the first validation failure will display.

## Example: `match` command

We can match the Statement batch consisting of `calibration_1.json` and `calibration_2.json` against the Patterns in the `calibration.jsonld` Profile as so:
```
% ./bin/match.sh \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --statement sample_statements/calibration_2.json
```
This command will run, print nothing, and exit with code 0, indicating match success.

However, if we were to match a Statement that was not intended to be matched with the Profile, for example:
```
% ./bin/match.sh \
  --profile sample_profiles/calibration.jsonld \
  --statement test-resources/sample_statements/adl_3.json
```
the command will print an error message to stdout indicating that the Statement does not refer to the profile version in its category context activity IDs:
```
----- Pattern Match Error -----
Error Description:  Missing Profile version in context category activity IDs
Statement ID:       6690e6c9-3ef0-4ed3-8b37-7f3964730bee

Category contextActivity IDs:
http://www.example.com/meetings/categories/teammeeting
```

Errors would also appear if the Statement included a sub-registration without a registration, or if the sub-registration did not conform to spec.

The order in which the Statements are passed to the `match` command matters greatly. If we switch the order of our Statements from the first example, Pattern matching fails:
```
% ./bin/match.sh \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_2.json \
  --statement sample_statements/calibration_1.json
```
and this message will be printed to stdout:
```
----- Pattern Match Failure -----
Primary Pattern ID: https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
Statement ID:       00000000-4000-8000-0000-000000000000

Pattern matching has failed.
```

If we set the `--compile-nfa` flag, then we can get a detailed trace of the Pattern matching path:
```
% ./bin/match.sh \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_2.json \
  --statement sample_statements/calibration_1.json \
  --compile-nfa
```

```
----- Pattern Match Failure -----
Primary Pattern ID: https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
Statement ID:       00000000-4000-8000-0000-000000000000

Statement Templates visited:
  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
Pattern path:
  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2
  https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-3
  https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1
```
