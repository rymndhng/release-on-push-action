# tag-on-push-action

Github Action to create a tag on push.

## Rationale

CI & CD should work from stable identifiers. Commit SHAs are ok, but humans work
better with identifiers whose before/after relationship can be reasoned with.


## Example Workflow

``` yaml
on: 
  push:
    branches:
      - master

jobs:
  tag-on-push:
    runs-on: ubuntu-latest
    env:
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    steps:
      - uses: rymndhng/tag-on-push@master
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

## Big Thanks

- semver: https://github.com/fsaintjacques/semver-tool
- lein-v:
- learning from: https://github.com/mikeal/publish-to-github-action/
