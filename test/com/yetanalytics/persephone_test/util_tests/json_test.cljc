(ns com.yetanalytics.persephone-test.util-tests.json-test
  (:require [clojure.test :refer [deftest testing is are]]
            [com.yetanalytics.persephone.utils.json :as json]))

;; TODO Test edn-to-json and read-json

(deftest split-json-path-test
  (testing "split-json-path test: given a string of multiple JSONPaths, split
           them along the pipe character."
    (is (= (json/split-json-path "$.foo") ["$.foo"]))
    (is (= (json/split-json-path "$.foo | $.bar") ["$.foo" "$.bar"]))
    (is (= (json/split-json-path "$.foo    |    $.bar") ["$.foo" "$.bar"]))
    (is (= (json/split-json-path "$.foo|$.bar.baz") ["$.foo" "$.bar.baz"]))
    ;; Don't split among pipe chars within brackets
    (is (= (json/split-json-path "$.foo[*] | $.bar.['b|ah']")
           ["$.foo[*]" "$.bar.['b|ah']"]))
    (is (= (json/split-json-path "$.foo | $['bar']") ["$.foo" "$['bar']"]))))

;; Example 1: Goessner store example

(def example-1 {"store"
                {"book"
                 [{"category" "reference"
                   "author"   "Nigel Rees"
                   "title"    "Sayings of the Century"
                   "price"    8.95}
                  {"category" "fiction"
                   "author"   "Evelyn Waugh"
                   "title"    "Sword of Honour"
                   "price"    12.99}
                  {"category" "fiction"
                   "author"   "Herman Melville"
                   "title"    "Moby Dick"
                   "isbn"     "0-553-21311-3"
                   "price"    8.99}
                  {"category" "fiction"
                   "author"   "J.R.R. Tolkien"
                   "title"    "The Lord of the Rings"
                   "isbn"     "0-395-19395-8"
                   "price"    22.99}]
                 "bicycle"
                 {"color" "red"
                  "price" 20}}})

; TODO: Test on invalid JSONPaths

(deftest read-json-test-1
  (testing "Testing JSONPath on example provided by Goessner"
    (are [expected path]
         (= expected (json/read-json example-1 path))
      ; The authors of all books in the store
      ["Nigel Rees" "Evelyn Waugh" "Herman Melville" "J.R.R. Tolkien"]
      "$.store.book[*].author"
      ["Nigel Rees" "Evelyn Waugh" "Herman Melville" "J.R.R. Tolkien"]
      "$..author"
      ["Nigel Rees" "Evelyn Waugh" "Herman Melville" "J.R.R. Tolkien"]
      "$.store.book.*.author"
      ["Nigel Rees" "Evelyn Waugh" "Herman Melville" "J.R.R. Tolkien"]
      "$['store']['book'][*]['author']"
      ; The first author in the store
      ["Nigel Rees"]
      "$.store.book[0].author"
      ; All things in the store
      [[{"category" "reference"
         "author"   "Nigel Rees"
         "title"    "Sayings of the Century"
         "price"    8.95}
        {"category" "fiction"
         "author"   "Evelyn Waugh"
         "title"    "Sword of Honour"
         "price"    12.99}
        {"category" "fiction"
         "author"   "Herman Melville"
         "title"    "Moby Dick"
         "isbn"     "0-553-21311-3"
         "price"    8.99}
        {"category" "fiction"
         "author"   "J.R.R. Tolkien"
         "title"    "The Lord of the Rings"
         "isbn"     "0-395-19395-8"
         "price"    22.99}]
       {"color" "red"
        "price" 20}]
      "$.store.*"
      ; The price of everything in the store
      [8.95 12.99 8.99 22.99 20]
      "$.store..price"
      [8.95 12.99 8.99 22.99 20]
      "$..price"
      ; The price of only books in the store
      [8.95 12.99 8.99 22.99]
      "$.store.book[*].price"
      ; Book ISBNs
      ["0-553-21311-3" "0-395-19395-8"]
      "$.store.book.*.isbn"
      ; The third book
      [{"category" "fiction"
        "author"   "Herman Melville"
        "title"    "Moby Dick"
        "isbn"     "0-553-21311-3"
        "price"    8.99}]
      "$..book[2]"
      ; The last book via subscript array slice
      [{"category" "fiction"
        "author"   "J.R.R. Tolkien"
        "title"    "The Lord of the Rings"
        "isbn"     "0-395-19395-8"
        "price"    22.99}]
      "$..book[-1:]"
      ; Last two books
      [{"category" "fiction"
        "author"   "Herman Melville"
        "title"    "Moby Dick"
        "isbn"     "0-553-21311-3"
        "price"    8.99}
       {"category" "fiction"
        "author"   "J.R.R. Tolkien"
        "title"    "The Lord of the Rings"
        "isbn"     "0-395-19395-8"
        "price"    22.99}]
      "$..book[-2:]"
      ; The first and third books via subscript union
      [{"category" "reference"
        "author"   "Nigel Rees"
        "title"    "Sayings of the Century"
        "price"    8.95}
       {"category" "fiction"
        "author"   "Herman Melville"
        "title"    "Moby Dick"
        "isbn"     "0-553-21311-3"
        "price"    8.99}]
      "$..book[0,2]"
      ; The first two books via slice
      [{"category" "reference"
        "author"   "Nigel Rees"
        "title"    "Sayings of the Century"
        "price"    8.95}
       {"category" "fiction"
        "author"   "Evelyn Waugh"
        "title"    "Sword of Honour"
        "price"    12.99}]
      "$..book[:2]"
      [{"category" "reference"
        "author"   "Nigel Rees"
        "title"    "Sayings of the Century"
        "price"    8.95}
       {"category" "fiction"
        "author"   "Evelyn Waugh"
        "title"    "Sword of Honour"
        "price"    12.99}]
      "$..book[0:2]"
      ; Unmatchable values
      []
      "$.non-existent"
      []
      "$.stire.book.*.author"
      []
      "$.store.book[4]"
      []
      "%.store.book[*].blah")))

;; Example 2: Sample xAPI Statement (from Pathetic)

(def example-2
  {"id" "6690e6c9-3ef0-4ed3-8b37-7f3964730bee"
   "actor"
   {"name" "Team PB"
    "mbox" "mailto:teampb@example.com"
    "member" [{"name"       "Andrew Downes"
               "account"    {"homePage" "http://www.example.com"
                             "name"     "13936749"}
               "objectType" "Agent"}
              {"name"       "Toby Nichols"
               "openid"     "http://toby.openid.example.org/"
               "objectType" "Agent"}
              {"name"         "Ena Hills"
               "mbox_sha1sum" "ebd31e95054c018b10727ccffd2ef2ec3a016ee9"
               "objectType"   "Agent"}]
    "objectType" "Group"}
   "verb"
   {"id" "http://adlnet.gov/expapi/verbs/attended"
    "display" {"en-GB" "attended"
               "en-US" "attended"}}
   "result"
   {"extensions"
    {"http://example.com/profiles/meetings/resultextensions/minuteslocation"
     "X:\\meetings\\minutes\\examplemeeting.one"}
    "success"    true
    "completion" true
    "response"   "We agreed on some example actions."
    "duration"   "PT1H0M0S"}
   "context"
   {"registration" "ec531277-b57b-4c15-8d91-d292c5b2b8f7"
    "contextActivities"
    {"parent"   [{"id"         "http://www.example.com/meetings/series/267"
                  "objectType" "Activity"}]
     "category" [{"id" "http://www.example.com/meetings/categories/teammeeting"
                  "objectType" "Activity"
                  "definition"
                  {"name"        {"en" "team meeting"}
                   "description" {"en" "A category of meeting used for regular team meetings."}
                   "type"        "http://example.com/expapi/activities/meetingcategory"}}]
     "other" [{"id"         "http://www.example.com/meetings/occurances/34257"
               "objectType" "Activity"}
              {"id"         "http://www.example.com/meetings/occurances/3425567"
               "objectType" "Activity"}]}
    "instructor"
    {"name"       "Andrew Downes"
     "account"    {"homePage" "http://www.example.com"
                   "name"     "13936749"}
     "objectType" "Agent"}
    "team"
    {"name"       "Team PB"
     "mbox"       "mailto:teampb@example.com"
     "objectType" "Group"}
    "platform" "Example virtual meeting software"
    "language" "tlh"
    "statement" {"objectType" "StatementRef"
                 "id"          "6690e6c9-3ef0-4ed3-8b37-7f3964730bee"}}
   "timestamp" "2013-05-18T05:32:34.804Z"
   "stored" "2013-05-18T05:32:34.804Z"
   "authority" {"account"    {"homePage" "http://cloud.scorm.com/"
                              "name"     "anonymous"}
                "objectType" "Agent"}
   "version" "1.0.0"
   "object" {"id"         "http://www.example.com/meetings/occurances/34534"
             "definition" {"extensions"  {"http://example.com/profiles/meetings/activitydefinitionextensions/room"
                                          {"name" "Kilby"
                                           "id"   "http://example.com/rooms/342"}}
                           "name"        {"en-GB" "example meeting"
                                          "en-US" "example meeting"}
                           "description" {"en-GB" "An example meeting that happened on a specific occasion with certain people present."
                                          "en-US" "An example meeting that happened on a specific occasion with certain people present."}
                           "type"        "http://adlnet.gov/expapi/activities/meeting"
                           "moreInfo"    "http://virtualmeeting.example.com/345256"}
             "objectType" "Activity"}})

(deftest read-json-test-2
  (testing "read-json on a sample xAPI Statement"
    (are [expected path]
         (= expected (json/read-json example-2 path))
      ;; Hits
      ["6690e6c9-3ef0-4ed3-8b37-7f3964730bee"]
      "$.id"
      ["2013-05-18T05:32:34.804Z"]
      "$.timestamp"
      ["http://adlnet.gov/expapi/activities/meeting"]
      "$.object.definition.type"
      ["PT1H0M0S"]
      "$.result.duration"
      [true]
      "$.result.success"
      [true]
      "$.result.completion"
      ["http://www.example.com/meetings/categories/teammeeting"]
      "$.context.contextActivities.category[*].id"
      ["X:\\meetings\\minutes\\examplemeeting.one"]
      "$.result.extensions['http://example.com/profiles/meetings/resultextensions/minuteslocation']"
      [{"name" "Kilby" "id" "http://example.com/rooms/342"}]
      "$.object.definition.extensions['http://example.com/profiles/meetings/activitydefinitionextensions/room']"
      ;; Misses
      [] "$.context.contextActivities.grouping[*]"
      [] "$.context.extensions['https://w3id.org/xapi/cmi5/context/extensions/sessionid']"
      [] "$.result.score"
      [] "$.context.extensions['https://w3id.org/xapi/cmi5/context/extensions/launchmode']"
      [] "$.context.extensions['https://w3id.org/xapi/cmi5/context/extensions/launchurl']"
      [] "$.context.extensions['https://w3id.org/xapi/cmi5/context/extensions/moveon']"
      [] "$.context.extensions['https://w3id.org/xapi/cmi5/context/extensions/launchparameters']"
      [] "$.result['https://w3id.org/xapi/cmi5/result/extensions/reason']")))