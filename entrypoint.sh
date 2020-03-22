#!/bin/bash -e

if [[ "true" == "${DEBUG}" ]]; then
    set -x
fi

CURRENT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

function check_preconditions {
    if [ -z "${GITHUB_TOKEN}" ]; then
        echo "error: not found GITHUB_TOKEN"
        exit 1
    fi

    # These are provided by actions
    if [ -z "${GITHUB_REPOSITORY}" ]; then
        echo "error: not found GITHUB_REPOSITORY"
        exit 1
    fi

    # These are provided by actions
    if [ -z "${GITHUB_SHA}" ]; then
        echo "error: not found GITHUB_SHA"
        exit 1
    fi

    # ensure BUMP_VERSION_SCHEME is set
    ALLOWED_BUMP_VERSION_SCHEMES="norelease major minor patch"
    if ! echo "$ALLOWED_BUMP_VERSION_SCHEMES" | xargs -n1 | grep "$INPUT_BUMP_VERSION_SCHEME"; then
        echo "error: unexpected INPUT_BUMP_VERSION_SCHEME"
        exit 1
    fi
}

function fetch_related_files {
    QUERY="q=repo:${GITHUB_REPOSITORY}%20type:pr%20is:closed%20is:merged%20SHA:${GITHUB_SHA}"

    # find related prs & commit info to extract the release options
    curl --silent --header "Authorization: token ${GITHUB_TOKEN}" \
         --url "https://api.github.com/search/issues?${QUERY}" \
         > related_prs

    curl --silent --header "Authorization: token ${GITHUB_TOKEN}" \
         --url "https://api.github.com/repos/${GITHUB_REPOSITORY}/commits/${GITHUB_SHA}" \
         > last_commit

    # find previous release version for bumping
    curl --silent --header "Authorization: token ${GITHUB_TOKEN}" \
         --url "https://api.github.com/repos/${GITHUB_REPOSITORY}/releases/latest" \
         > last_release
}

function pr_has_label {
    # If true, writes "true" to stdout if label exists in any PRs containing the SHA, otherwise writes "false"
    jq --arg labelname "$1" '.items | map(.labels) | flatten | map_values(.name) | contains([$labelname])' -r
}

function generate_new_release_data {
    BUMP_VERSION_SCHEME="$INPUT_BUMP_VERSION_SCHEME"
    if [[ "true" == $(cat related_prs | pr_has_label "release:patch") ]]; then
        BUMP_VERSION_SCHEME="patch"
    elif [[ "true" == $(cat related_prs | pr_has_label "release:minor") ]]; then
        BUMP_VERSION_SCHEME="minor"
    elif [[ "true" == $(cat related_prs | pr_has_label "release:major") ]]; then
        BUMP_VERSION_SCHEME="major"
    fi

    if [[ "norelease" == "$BUMP_VERSION_SCHEME" ]]; then
        echo "Skipping release, no version is bumped."
        exit 1;
    fi

    LAST_TAG_NAME=$(jq ".tag_name // '0.0.0'" last_release -r || echo "0.0.0")
    LAST_VERSION=${LAST_TAG_NAME#v}
    NEXT_VERSION=$("${CURRENT_DIR}/lib/semver" "$BUMP_VERSION_SCHEME" "$LAST_VERSION")

    cat << EOF > new_release_data
{
  "tag_name": "v${NEXT_VERSION}",
  "target_commitish": "${GITHUB_SHA}",
  "name": "${NEXT_VERSION}",
  "body": "Version ${NEXT_VERSION}",
  "draft": false,
  "prerelease": false
}
EOF

    echo "Next version set to be: ${NEXT_VERSION}"
}


function skip_if_norelease_set {
    COMMIT_TITLE=$(jq '.commit.message' last_commit -r | head -n 1)

    if [[ $COMMIT_TITLE == *'[norelease]'* ]]; then
        echo 'Skipping release. Reason: git commit title contains [norelease]'
        exit
    fi

    PR_URL=$(jq '.items | map(.url) | flatten' -r related_prs || echo "false")
    if [[ "true" == $(cat related_prs | pr_has_label "norelease") ]]; then
        echo "Skipping release. Reason: related PR has label norelease. PR URLs: ${PR_URL}"
        exit
    fi
}


function create_new_release {
    if [ -z "$DRYRUN" ]; then
        curl --silent --header "Authorization: token ${GITHUB_TOKEN}" \
             --url "https://api.github.com/repos/${GITHUB_REPOSITORY}/releases" \
             --request POST \
             --data @new_release_data
    else
        echo "DRYRUN set, would have created a new release with the contents:"
        cat new_release_data
    fi
}

function main {
    check_preconditions
    fetch_related_files
    generate_new_release_data
    skip_if_norelease_set
    create_new_release
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "@"
fi
