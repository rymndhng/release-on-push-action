#!/bin/bash -e

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

function skip_if_norelease_set {
    curl --header "Authorization: token ${GITHUB_TOKEN}" \
         --url "https://api.github.com/repos/${GITHUB_REPOSITORY}/commits/${GITHUB_SHA}" \
         > last_commit

    COMMIT_TITLE=$(jq .message last_commit | head -n 1)

    if [[ $COMMIT_TITLE == '*[norelease]*' ]]; then
        echo 'Skipping release. Reason: git commit title contains [norelease]'
        exit
    fi
}

function generate_new_release_data {
    # Get the last release tag
    curl --header "Authorization: token ${GITHUB_TOKEN}" \
         --url "https://api.github.com/repos/${GITHUB_REPOSITORY}/releases/latest" \
         > last_release

    LAST_TAG_NAME=$(jq ".tag_name" last_release -r || echo "0.0.0")
    NEXT_TAG_NAME=$(./lib/semver bump minor "$LAST_TAG_NAME")

    cat << EOF > new_release_data
{
  "tag_name": "${NEXT_TAG_NAME}",
  "target_commitish": "${GITHUB_SHA}",
  "name": "${NEXT_TAG_NAME}",
  "body": "Release ${NEXT_TAG_NAME}",
  "draft": false,
  "prerelease": false
}
EOF
}

function create_new_release {
    if [ -z "$DRYRUN" ]; then
        curl --header "Authorization: token ${GITHUB_TOKEN}" \
             --url "https://api.github.com/repos/${GITHUB_REPOSITORY}/releases" \
             --request POST \
             --data @new_release_data
    else
        echo "DRYRUN set, would have created a new release with the contents:"
        cat new_release_data

    fi
}

skip_if_norelease_set
generate_new_release_data
create_new_release
