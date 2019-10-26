# Github Release On Push Action

Github Action to create a release on push.

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
          strategy: minor
```


Supported strategies:

- minor
- major
- patch

## FAQ

### How do I skip creation of a release?

There are two ways to accomplish this:

1. Put `[norelease]` in the commit title.
2. If the commit has an attached PR, add the label `norelease` to the PR.

### Can I change the versioning scheme by PR?

Yes, if the PR has the label `release:major`, `release:minor`, or `release:patch`, this will override the default `strategy`.

### Do I need to setup Github Action access tokens or any other permission-related thing?

No, you do not! Github Actions will inject a token for this plugin to interact with the API. 

### Can I create a tag instead of a release?

Currently, no.

In order to reliably generate monotonic versions, we use Github Releases to
track what the last release version is. See [Release#get-the-latest-release](https://developer.github.com/v3/repos/releases/#get-the-latest-release).

## Big Thanks To

- shell-semver: https://github.com/fmahnke/shell-semver
- https://github.com/mikeal/publish-to-github-action/
- Inspiration: https://github.com/roomkey/lein-v
