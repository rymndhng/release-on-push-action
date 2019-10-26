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
          strategy: minor-version

```


Supported strategies

- minor-version
- major-version
- calver

## FAQ

### How do I skip creation of a release?

Put `[norelease]` in the title.

## Big Thanks

- semver: https://github.com/fsaintjacques/semver-tool
- lein-v:
- learning from: https://github.com/mikeal/publish-to-github-action/
