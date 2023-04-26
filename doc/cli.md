# Command Line Interface

Persephone supports the `persephone` CLI command with two subcommands: `validate` and `match`, which performs Statement Template validation and Pattern matching, respectively.

To use the CLI, first run `make bundle`, then `cd` to `target/bundle`. You will then be able to run `/bin/persephone.sh`, which will accept a subcommand followed by arguments for Profiles, Statements, and so on. You can also provide the command with a `--help` or `-h` flag and it will list its subcommands.

Note that Profile and Statement arguments accept URIs, e.g. filepaths, not the JSON data itself. In addition, some arguments (e.g. `--profile`) may be repeated to allow the CLI to accept multiple arguments.

The `validate` subcommand validates a single Statement against Statement Templates in one or more Profiles. The following table shows its arguments:

| Subcommand&nbsp;Argument | Description
| :--                          | :--
| `-p, --profile URI`          | Profile URI filepath/location; must specify one or more.
| `-i, --template-id IRI`      | IDs of Statement Templates to validate against; can specify zero or more. Filters out all Templates that are not included.
| `-s, --statement URI`        | Statement filepath/location; must specify one.
| `-e, --extra-statements URI` | Extra Statement batch filepath/location; can specify zero or more. If specified, activates [Statement Ref property](library.md#statement-ref-templates) validation, where the referred object/context Statement exists in this batch and its Template exists in a provided Profile.
| `-a, --all-valid`            | If set, the Statement is not considered valid unless it is valid against ALL Templates. Otherwise, it only needs to be valid against at least one Template.
| `-c, --short-circuit`        | If set, then print on only the first Template the Statement fails validation against.Otherwise, print for all Templates the Statement fails against.
| `-h, --help`                 | Display the help menu.

The `match` subcommand matches a Statement batch against Patterns in one or more Profiles. The following table shows its arguments:

| Subcommand&nbsp;Argument | Description
| :--                    | :--
| `-p, --profile URI`    | Profile filepath/location; must specify one or more.
| `-i, --pattern-id IRI` | IDs of primary Patterns to match against; can specify zero or more. Filters out all Patterns that are not included.
| `-s, --statement URI`  | Statement filepath/location; must specify one or more.
| `-n, --compile-nfa`    | If set, compiles the Patterns into a non-deterministic finite automaton (NFA) instead of a deterministic one, allowing for more detailed error traces at the cost of decreased performance.
| `-h, --help`           | Display the help menu.

## Examples for `persephone validate`

In the examples in this and the next section, assume that the `test-resources/sample_profiles` and `test-resources/sample_statements` directories were copied into `target/bundle`, i.e. the location where we run `./bin/persephone`.

For our first example, we can validate the `calibration_1.json` Statement against the Statement Templates in the `calibration.jsonld` Profile:
```
% ./bin/persephone.sh validate \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json
```
This will run, print nothing, and exit with code 0, indicating successful validation.

We can also add additional Profiles as so:
```
% ./bin/persephone.sh validate \
  --profile sample_profiles/calibration.jsonld \
  --profile sample_profiles/catch.json \
  --statement sample_statements/calibration_1.json
```
and it will output the same result, as the Statement will still match against the `calibration.jsonld` Profile, even though it will fail validation against all Templates in the `catch.json` profile.

To see an example of failed validation, set the `--all-valid` flag so that the Statement has to be valid against all Templates:
```
% ./bin/persephone.sh validate \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --all-valid
```
This will run and print the following message to stdout before exiting with code 1:
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

----- Statement Validation Failure -----
Template ID:  https://xapinet.org/xapi/yet/calibration/v1/templates#activity-3
Statement ID: 00000000-4000-8000-0000-000000000000

Template rule was not followed:
  {:any [\"https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-3\"],
   :location \"$.object.id\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-1

Template rule was not followed:
  {:any [\"Activity 3\"],
   :location \"$.object.definition.name.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   Activity 1

Template rule was not followed:
  {:any [\"The third Activity\"],
   :location \"$.object.definition.description.en-US\",
   :presence \"included\"}
 failed 'any' property: evaluated values must include some values given by 'any'
 statement values:
   The first Activity

-----------------------------
Total errors found: 7
```

If we set the `--short-circuit` flag along with `--all-valid`:
```
% ./bin/persephone.sh validate \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --short-circuit --all-valid
```
then only validation failures for the first failing Template will appear.

We can use the `--template-id` argument to select which Templates the Statement is validated against.
```
% ./bin/persephone.sh validate \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --template-id https://xapinet.org/xapi/yet/calibration/v1/templates#activity-2 \
  --template-id https://xapinet.org/xapi/yet/calibration/v1/templates#activity-3
```
This way, we pass to the CLI the two Templates the Statement is invalid against, so running this will result in the same validation failure output as in the previous example.

Note that if we set `--template-id` to the ID of a non-existent Template, e.g.
```
% ./bin/persephone.sh validate \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --template-id http://random-template.org
```
the validation will pass vacuously, as there are technically no Templates the Statement is invalid against.

## Examples for `persephone match`

We can match the Statement batch consisting of `calibration_1.json` and `calibration_2.json` against the Patterns in the `calibration.jsonld` Profile as so:
```
% ./bin/persephone.sh match \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --statement sample_statements/calibration_2.json
```
This will run, print nothing, and exit with code 0, indicating match success.

The `--statement` argument can also accept a Statement array file; in this example, `calibration_coll.json` is an array of the Statements in `calibration_1.json` and `calibration_2.json`, in that order.
```
% ./bin/persephone.sh match \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_coll.json
```
Individual Statements and Statement arrays can be mixed and matched when using `--statement` multiple times.

However, if we were to match a Statement that was not intended to be matched with the Profile, for example:
```
% ./bin/persephone.sh match \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/adl_3.json
```
the CLI will print an error message to stdout indicating that the Statement does not refer to the profile version in its category context activity IDs:
```
----- Pattern Match Error -----
Error Description:  Missing Profile version in context category activity IDs
Statement ID:       6690e6c9-3ef0-4ed3-8b37-7f3964730bee

Category contextActivity IDs:
http://www.example.com/meetings/categories/teammeeting
```

Errors would also appear if the Statement included a sub-registration without a registration, or if the sub-registration did not conform to spec.

The order in which the Statements are passed to the `match` subcommand matters greatly. If we switch the order of our Statements from the first example, Pattern matching fails:
```
% ./bin/persephone.sh match \
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
% ./bin/persephone.sh match \
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

Note that similar to `--template-id`, we can use `--pattern-id` to filter out Patterns (note that these must be primary Patterns). If we set `--pattern-id` to a non-existent Pattern:
```
% ./bin/persephone.sh match \
  --profile sample_profiles/calibration.jsonld \
  --statement sample_statements/calibration_1.json \
  --statement sample_statements/calibration_2.json \
  --pattern-id http://random-pattern.org
```
then matching will vacuously pass, as there would be no Patterns the Statements will fail to match against.
