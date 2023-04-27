# Webserver

Persephone features a webserver that can be used to validate or match Statements at the `POST /statements` endpoint.

To use the server, first run `make bundle`, then `cd` into `target/bundle`. You will then be able to run `/bin/server.sh`, which will accept either the `validate` or `match` subcommand to start the server in either Statement Template validation or Pattern matching mode, respectively. The following table shows the top-level arguments to the server init command:

| Command&nbsp;Argument | Default | Description
| :--           | :--         | :--
| `-H, --host HOST` | `localhost` | The hostname of the webserver endpoint
| `-P, --port PORT` | `8080`      | The port number of the webserver endpoint; must be between 0 and 65536
| `-h, --help`      | N/A         | Display the top-level help guide

The `validate` subcommand starts the server in validate mode, where any Statements sent to the `/statements` endpoint will undergo Template matching against the Profiles that the server was given on startup. If a Statement array is sent, only the last statement will be validated. The following table shows the arguments to `validate`:

| Subcommand&nbsp;Argument | Description
| :--                     | :--
| `-p, --profile URI`     | Profile URI filepath/location; must specify one or more.
| `-i, --template-id IRI` | IDs of Statement Templates to validate against; can specify zero or more. Filters out all Templates that are not included.
| `-a, --all-valid`       | If set, any Statement is not considered valid unless it is valid against ALL Templates. Otherwise, a Statement only needs to be valid against at least one Template.
| `-c, --short-circuit`   | If set, then print on only the first Template any Statement fails validation against.Otherwise, print for all Templates a Statement fails against.
| `-h, --help`            | Display the 'validate' subcommand help menu.

The `match` subcommand starts the server in match mode, where any Statements sent to the `/statements` endpoint will undergo Pattern matching against the Profiles that the server was given on startup. If a single Statement is sent, it is coerced into a Statement batch. The following table shows the arguments to `match`:

| Subcommand&nbsp;Argument | Description
| :--                    | :--
| `-p, --profile URI`    | Profile filepath/location; must specify one or more.
| `-i, --pattern-id IRI` | IDs of primary Patterns to match against; can specify zero or more. Filters out all Patterns that are not included.
| `-h, --help`           | Display the 'match' subcommand help menu.

In addition to the `POST /statements` endpoint, there is a `GET /health` endpoint that is used to perform a server health check:
```
% curl http://0.0.0.0:8080/health
```
which will return an response with status `200` and body `OK`.

There is no `PUT` or `GET` versions of the `/statements` endpoint, unlike what is required in a learning record store.

# Examples for validate mode

For the first few examples, let us start a webserver in validate mode with a single Profile. Assume that we have already copied the contents of `test-profile` into the `target/bundle` directory. Running this command
```
% ./bin/server.sh validate --profile sample_profiles/calibration.jsonld
```
will start up a server in validate mode on `localhost:8080` with a single Profile set to validate against.

To validate a single Statement against Templates in that Profile:
```
% curl localhost:8080/statements \
  -H "Content-Type: application/json" \
  -d @sample_statements/calibration_1.json
```
This will return a `204 No Content` response, indicating validation success. We can also input a Statement array, e.g. `sample_statements/calibration_coll.json`, but only the last Statement in that array will be validated.

If we try to validate an invalid Statement:
```
% curl localhost:8080/statements \
  -H "Content-Type: application/json" \
  -d @sample_statements/adl_1.json
```
then we receive a `400 Bad Request` response and an EDN response body that looks like the following:
```clojure
{:type :validation-failure
 :contents {...}}
```
where `:contents` is the return value of `persephone/validate-statement` with `:fn-type :errors`.

Confusingly, we will also receive a `400 Bad Request` error if we input a completely invalid statement:
```
% curl localhost:8080/statements \
  -H "Content-Type: application/json" \
  -d '{"id": "not-a-statement"}'
```
The response body will still contain `:type` and `:contents`, but `:type` will have the value `:invalid-statement` and `:contents` will be a Clojure spec error map.

Similarly, if the request is invalid JSON, `:type` will have the value `:invalid-json`.

Validation also works with two Profiles:
```
% ./bin/server.sh validate \
  --profile sample_profiles/calibration.jsonld \
  --profile sample_profiles/catch.json
```
as well as the `--template-id`, `--all-valid`, and `--short-circuit` flags. These work very similarly to how they work in the [CLI](cli.md#examples-for-persephone-validate).

# Examples for match mode

In match mode, we will first start a webserver mode with a single Profile. Running this command
```
% ./bin/server.sh match --profile sample_profiles/calibration.jsonld
```
will start up a server in validate mode on `localhost:8080` with a single Profile set to perform Pattern matching against.

To validate a Statement array against Templates in that Profile:
```
% curl localhost:8080/statements \
  -H "Content-Type: application/json" \
  -d @sample_statements/calibration_coll.json
```
This will return a `204 No Content` response, indicating match success. Likewise, we can match against a statement, in this case passing in the file `sample_statements/calibration_1.json` instead. Notice how similar the request body is to the equivalent request in validate mode.

If we try to Pattern match a Statement sequence that cannot be matched (e.g. we reverse the order of the two Statements in `calibration_coll.json`), we will receive the following EDN request body:
```clojure
{:type :match-failure
 :contents {...}}
```
where `:contents` is the return value of `persephone/match-statement-batch`.

If we have a match error, e.g. a missing Profile reference in category context activity IDs or an invalid subregistration, the request bdoy will be of the form
```clojure
{:type :match-error
 :contents {:errors {...}}}
```
where the `:errors` value is a map containing the error data.

If we have a Statement syntax error, then `:type` will have the value `:invalid-statements` and `:contents` will be a Clojure spec error map. Similarly, if we have invalid JSON, then `:type` will be `:invalid-json`.

As with validation, Pattern matching works with two or more Profiles:

```
% ./bin/server.sh match \
  --profile sample_profiles/calibration.jsonld \
  --profile sample_profiles/catch.json
```
