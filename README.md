# Github Release On Push Action

> Stop using files for versioning. Use git tags instead!

Github Action to create a Github Release on pushes to master.

## Features

- Flexible version bumping scheme with a project default or overrides using Pull Request Labels
- Creates Release Notes automatically (with a list of commits since the last release)

## Rationale

CI & CD systems are simpler when they work with immutable monotonic identifers
from the get-go. Trigger your release activites by subscribing to new tags
pushed from this Action.

For automation, Github Releases (and by extension git tags) are better than
versioned commit files for these reasons:

- Agnostic of language & ecosystem (i.e. does not rely on presence of package.json)
- Tagging does not require write permissions to bump version
- Tagging does not trigger infinite-loop webhooks from pushing version bump commits

## Example Workflow

``` yaml
on: 
  push:
    branches:
      - master

jobs:
  release-on-push:
    runs-on: ubuntu-latest
    env:
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    steps:
      - uses: rymndhng/release-on-push-action@master
        with:
          bump_version_scheme: minor
```

Allowed values of `bump_version_scheme`:

- minor
- major
- patch
- **norelease**: Performs no release by default. Creation of release delegated to labels on Pull Requests.

For stability, we recommend pinning the version of the action. See [Releases](https://github.com/rymndhng/release-on-push-action/releases).

## FAQ

### Can I skip creation of a release?

There are several approaches:

1. Put `[norelease]` in the commit title.
2. If the commit has an attached PR, add the label `norelease` to the PR.
3. Set the action's `bump_version_scheme` to `norelease` to disable this behavior by default

### How do I change the bump version scheme using Pull Requests?

Iif the PR has the label `release:major`, `release:minor`, or `release:patch`, this will override `bump_version_scheme`. 

This repository's pull requests are an example of this in action. For example, [#19](https://github.com/rymndhng/release-on-push-action/pull/19).

Only one of these labels should be present on a PR. If there are multiple, the behavior is undefined.

### Do I need to setup Github Action access tokens or any other permission-related thing?

No, you do not! Github Actions will inject a token for this plugin to interact with the API. 

### Can I create a tag instead of a release?

Currently, no.

In order to reliably generate monotonic versions, we use Github Releases to
track what the last release version is. See [Release#get-the-latest-release](https://developer.github.com/v3/repos/releases/#get-the-latest-release).

## Development

Uses [babashka](https://github.com/borkdude/babashka).

To run tests:

1. Install babashka ([link](https://github.com/borkdude/babashka#installation)).
2. Run Tests

``` sh

make test
```


## Big Thanks To

- shell-semver: https://github.com/fmahnke/shell-semver
- https://github.com/mikeal/publish-to-github-action/
- Inspiration: https://github.com/roomkey/lein-v
