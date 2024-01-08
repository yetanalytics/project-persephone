# project-persephone

<img src="logo/logo.svg" alt="Persephone Logo" width="325px" align="left" />

[![CI](https://github.com/yetanalytics/project-persephone/actions/workflows/main.yml/badge.svg)](https://github.com/yetanalytics/project-persephone/actions/workflows/main.yml)

_Only Persephone, daughter of Zeus and wife of Hades, could travel between the Underworld and the world of the living. Project Persephone is the liaison between our physical world and the world of the Semantic Web._

A Clojure library for validating xAPI Statements against xAPI Profiles, featuring interactive CLI and webserver applications.

## Index

- [Library](doc/library.md): How to use the library/API functions
- [CLI](doc/cli.md): How to run the `validate` and `match` commands
- [Webserver](doc/server.md): How to start up and run a webserver

## Installation

Add the following to the `:deps` map in your `deps.edn` file:
```clojure
com.yetanalytics/project-persephone {:mvn/version "0.9.1"}
```

Alternatively, to run the CLI or server as an application, you can pull a Docker image from [DockerHub](https://hub.docker.com/repository/docker/yetanalytics/persephone):
```
docker pull yetanalytics/persephone:latest
```

## How It Works 

Persephone performs two main tasks on [xAPI Statements](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Data.md#20-statements): [Statement Template](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#statment-templates) validation and [Pattern](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#patterns) matching. The former checks if a Statement follows the specifications of one or more Statement Templates in xAPI Profiles, while the latter performs regex-like matching against Profile Patterns. Persephone does so following the [validation and matching guidelines](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-communication.md#20-algorithms) of the [xAPI Profile specification](https://github.com/adlnet/xapi-profiles).

For example, suppose that you have the following Statement:
```json
{
  "id": "00000000-4000-8000-0000-000000000000",
  "timestamp": "2022-06-27T10:10:10.000Z",
  "actor": {
    "name": "My Name",        
    "mbox": "mailto:foo@example.org",
    "objectType": "Agent"
  },
  "verb": {
    "id": "https://xapinet.org/xapi/yet/calibration/v1/concepts#did",
    "display": {"en-US": "Did"}
  },
  "object": {
    "id": "https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-1",
    "objectType": "Activity",
    "definition": {
      "name": {"en-US": "Activity 1"},
      "description": {"en-US": "The first Activity"}
    }
  },
  "context": {
    "contextActivities": {
      "category": [
        {
          "id": "https://xapinet.org/xapi/yet/calibration/v1",
       	  "objectType": "Activity"
        }
      ]
    }
  }
}
```

The Statement will match against the following Statement Template:
```json
{
  "id" : "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-1",
  "inScheme" : "https://xapinet.org/xapi/yet/calibration/v1",
  "prefLabel" : {
    "en" : "Activity Template 1"
  },
  "definition" : {
    "en" : "The statement template and rules associated with Activity 1 getting done."
  },
  "type" : "StatementTemplate",
  "verb" : "https://xapinet.org/xapi/yet/calibration/v1/concepts#did",
  "rules" : [
    {
      "location" : "$.id",
      "presence" : "included"
    },
    {
      "location" : "$.timestamp",
      "presence" : "included"
    },
    {
      "any" : ["https://xapinet.org/xapi/yet/calibration/v1/concepts#activity-1"],
      "location" : "$.object.id",
      "presence" : "included"
    },
    {
      "any" : ["Activity 1"],
      "location" : "$.object.definition.name.en-US",
      "presence" : "included"
    },
    {
      "any" : ["The first Activity"],
      "location" : "$.object.definition.description.en-US",
      "presence" : "included"
    },
    {
      "any" : ["https://xapinet.org/xapi/yet/calibration/v1"],
      "location" : "$.context.contextActivities.category[0].id",
      "presence" : "included"
    }
  ]
}
```
because the `verb` in the Statement is the same as the `verb` in the Statement Template, and because the Statement's `id`, `name`, `description`, and `category.id` property values are included in the values specified by the Template's [rules](https://github.com/adlnet/xapi-profiles/blob/master/xapi-profiles-structure.md#statement-template-rules), which are matched using the `location` [JSONPath](https://www.ietf.org/archive/id/draft-goessner-dispatch-jsonpath-00.html) strings.

Now suppose that the Statement is the first in a sequence of Statements. This Statement will match against the following Patterns:
```json
{
  "definition" : {
    "en" : "Pattern 1"
  },
  "primary" : true,
  "prefLabel" : {
    "en" : "Learning Pattern 1"
  },
  "type" : "Pattern",
  "inScheme" : "https://xapinet.org/xapi/yet/calibration/v1",
  "id" : "https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-1",
  "sequence" : [
    "https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-2",
    "https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-3"
  ]
},
{
  "definition" : {
    "en" : "Pattern 2"
  },
  "primary" : false,
  "prefLabel" : {
    "en" : "Learning Pattern 2"
  },
  "type" : "Pattern",
  "inScheme" : "https://xapinet.org/xapi/yet/calibration/v1",
  "id" : "https://xapinet.org/xapi/yet/calibration/v1/patterns#pattern-2",
  "optional" : "https://xapinet.org/xapi/yet/calibration/v1/templates#activity-1"
}
```
since Pattern 1 (which is a primary Pattern, indicating that it is the starting point for Pattern matching) specifies a sequence of Patterns 2 and 3, and Pattern 2 indicates that the Statement can optionally match against the aforementioned Statement Template, which the Statement indeed does.

## License

Copyright © 2019-2024 Yet Analytics, Inc.

Distributed under the Apache License version 2.0.

The Persephone logo is based off of [_Proserpine_](https://commons.wikimedia.org/wiki/File:%27Proserpine%27,_marble_bust_by_Hiram_Powers,_1844,_Cincinnati_Art_Museum.jpg) by [Hiram Powers](https://en.wikipedia.org/wiki/Hiram_Powers).
