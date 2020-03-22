#!/usr/bin/env bats
#; -*-shell-script-*-

load test_helper

@test "input is invalid" {
    related_prs=''
    result=$(echo $related_prs | pr_has_label "foo")
    [ "$result" == "" ]
}

@test "no prs" {
    related_prs='{ "items": [] }'
    result=$(echo $related_prs | pr_has_label "foo")
    [ "$result" == "false" ]

    result=$(echo $related_prs | pr_has_label "bar")
    [ "$result" == "false" ]
}

@test "single pr with label" {
    related_prs='{ "items": [{ "labels": [{"name": "wontfix" }]}]}'

    result=$(echo $related_prs | pr_has_label "wontfix")
    echo $result
    [ "$result" == "true" ]

    result=$(echo $related_prs | pr_has_label "foo")
    [ "$result" == "false" ]
}

@test "multiple prs with no labels" {
    related_prs='{ "items": [{ "labels": []}, { "labels": []}]}'

    result=$(echo $related_prs | pr_has_label "wontfix")
    [ "$result" == "false" ]

    result=$(echo $related_prs | pr_has_label "foo")
    [ "$result" == "false" ]
}

@test "multiple prs with labels" {
    related_prs='{ "items": [
      { "labels": [{ "name": "wontfix"}, { "name": "foo"} ]},
      { "labels": [{ "name": "wontfix"} ]},
      { "labels": []}
    ]}'

    result=$(echo $related_prs | pr_has_label "wontfix")
    [ "$result" == "true" ]

    result=$(echo $related_prs | pr_has_label "foo")
    [ "$result" == "true" ]

    result=$(echo $related_prs | pr_has_label "hello")
    [ "$result" == "false" ]
}
