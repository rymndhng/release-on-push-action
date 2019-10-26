# tag-on-push-action
Github Action to Tag a Repository on Push

## Rationale

Creates git tags based on pushes to a matching master branch in a tooling agnostic way.

Pains this solves:

1. Developers don't need to run another script manually to create a tag
2. Enforces consistent tagging policy
3. Language/Ecosystem agnostic.

## Inspiration

- `lein-v`


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


## Big Thanks

- semver: https://github.com/fsaintjacques/semver-tool
- lein-v:
- learning from: https://github.com/mikeal/publish-to-github-action/
