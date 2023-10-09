# Webserver

Persephone features a webserver that can be used to validate or match Statements at the `POST /statements` endpoint.

To use the server, first run `make bundle`, then `cd` into `target/bundle`. You will then be able to run `/bin/server.sh`, which will accept either the `validate` or `match` subcommand to start the server in either Statement Template validation or Pattern matching mode, respectively. The following table shows the top-level arguments to the server init command:

| Command&nbsp;Argument | Default | Description
| :--               | :--         | :--
| `-H, --host HOST` | `0.0.0.0`   | The hostname of the webserver endpoint
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
% curl -i 0.0.0.0:8080/health
```
which will return an response with status `200 OK`:
```http
HTTP/1.1 200 OK
Date: Mon, 01 May 2023 17:25:32 GMT
Strict-Transport-Security: max-age=31536000; includeSubdomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Download-Options: noopen
X-Permitted-Cross-Domain-Policies: none
Content-Security-Policy: object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;
Content-Type: text/plain
Transfer-Encoding: chunked
```
and a body `"OK"`.

There is no `PUT` or `GET` versions of the `/statements` endpoint, unlike what is required in a learning record store.

# Examples for validate mode

For the first few examples, let us start a webserver in validate mode with a single Profile. Assume that we have already copied the contents of `test-profile` into the `target/bundle` directory. Running this command
```
% ./bin/server.sh validate --profile sample_profiles/calibration.jsonld
```
will start up a server in validate mode on `0.0.0.0:8080` with a single Profile set to validate against.

To validate a single Statement against Templates in that Profile:
```bash
% curl -i localhost:8080/statements \
  -H "Content-Type: application/json" \
  -d @sample_statements/calibration_1.json
```
This will return the following `204 No Content` response, indicating validation success:
```http
HTTP/1.1 204 No Content
Date: Mon, 01 May 2023 17:18:14 GMT
Strict-Transport-Security: max-age=31536000; includeSubdomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Download-Options: noopen
X-Permitted-Cross-Domain-Policies: none
Content-Security-Policy: object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;
```

We can also input a Statement array, e.g. `sample_statements/calibration_coll.json`, but only the last Statement in that array will be validated.

If we try to validate an invalid Statement:
```bash
% curl -i localhost:8080/statements \
  -H "Content-Type: application/json" \
  -d @sample_statements/adl_1.json
```
then we receive a `400 Bad Request` response:
```http
HTTP/1.1 400 Bad Request
Date: Mon, 01 May 2023 17:21:23 GMT
Strict-Transport-Security: max-age=31536000; includeSubdomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Download-Options: noopen
X-Permitted-Cross-Domain-Policies: none
Content-Security-Policy: object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;
Content-Type: application/edn
Transfer-Encoding: chunked
```

and an EDN response body that looks like the following:
```clojure
{:type :validation-failure
 :contents {...}}
```
where `:contents` is the return value of `persephone/validate-statement` with `:fn-type :errors`.

We will also receive a `400 Bad Request` error if we input a completely invalid statement:
```bash
% curl -i localhost:8080/statements \
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
as well as the `--template-id`, `--all-valid`, and `--short-circuit` flags. These work very similarly to how they work in the [CLI](cli.md#examples-for-persephone-validate). Note that you should take care not to include duplicate IDs or else you will receive an init error.

# Examples for match mode

In match mode, we will first start a webserver mode with a single Profile. Running this command
```
% ./bin/server.sh match --profile sample_profiles/calibration.jsonld
```
will start up a server in validate mode on `0.0.0.0:8080` with a single Profile set to perform Pattern matching against.

To validate a Statement array against Templates in that Profile:
```bash
% curl -i localhost:8080/statements \
  -H "Content-Type: application/json" \
  -d @sample_statements/calibration_coll.json
```
This will return a `204 No Content` response, indicating match success:
```http
HTTP/1.1 204 No Content
Date: Mon, 01 May 2023 17:23:09 GMT
Strict-Transport-Security: max-age=31536000; includeSubdomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Download-Options: noopen
X-Permitted-Cross-Domain-Policies: none
Content-Security-Policy: object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;
```

Likewise, we can match against a statement, in this case passing in the file `sample_statements/calibration_1.json` instead. Notice how similar the request body is to the equivalent request in validate mode.

If we try to Pattern match a Statement sequence that cannot be matched (e.g. we reverse the order of the two Statements in `calibration_coll.json`), we will receive the following EDN `400 Bad Request` response:
```http
HTTP/1.1 400 Bad Request
Date: Mon, 01 May 2023 17:24:10 GMT
Strict-Transport-Security: max-age=31536000; includeSubdomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Download-Options: noopen
X-Permitted-Cross-Domain-Policies: none
Content-Security-Policy: object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;
Content-Type: application/edn
Transfer-Encoding: chunked
```

And the following response body:
```clojure
{:type :match-failure
 :contents {...}}
```
where `:contents` is the return value of `persephone/match-statement-batch`.

If we have a match error, e.g. a missing Profile reference in category context activity IDs or an invalid subregistration, the `400 Bad Request` response body will be of the form
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
though you should be careful not to include any duplicate Profile or Pattern IDs or else you will receive an error.
