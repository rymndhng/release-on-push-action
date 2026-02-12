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
- **keep**: Keeps the current version unchanged. Useful for iterating on pre-release suffixes (e.g., v2.0.0-rc1 → v2.0.0-rc2). Requirements: (1) Must provide a `tag_suffix`, (2) Previous version must have a suffix, (3) New suffix must differ from the previous one.
- **norelease**: Performs no release by default. Creation of release delegated to labels on Pull Requests.

For stability, we recommend pinning the version of the action. See [Releases](https://github.com/rymndhng/release-on-push-action/releases).

See [action.yml](./action.yml) for the full list of options.

## FAQ

### Can I skip creation of a release?

There are several approaches:

1. Put `[norelease]` in the commit title.
2. If the commit has an attached PR, add the label `norelease` to the PR.
3. Set the action's `bump_version_scheme` to `norelease` to disable this behavior by default

### How do I change the bump version scheme using Pull Requests?

If the PR has the label `release:major`, `release:minor`, `release:patch`, or `release:keep`, this will override `bump_version_scheme`.

This repository's pull requests are an example of this in action. For example, [#19](https://github.com/rymndhng/release-on-push-action/pull/19).

Only one of these labels should be present on a PR. If there are multiple, the behavior is undefined.

### How do I specify the tag suffix using Pull Request labels?

You can override the `tag_suffix` parameter using a PR label in the format `tag-suffix:VALUE`, where VALUE is your desired suffix.

**Examples:**
- `tag-suffix:rc2` → sets suffix to `rc2`
- `tag-suffix:-alpha` → sets suffix to `-alpha`
- `tag-suffix:beta3` → sets suffix to `beta3`

This is particularly useful for iterating pre-release versions without modifying workflow files:

1. Create PR with labels: `release:keep` and `tag-suffix:rc2`
2. Merge PR → automatically creates v2.0.0rc2 from v2.0.0rc1
3. Next iteration: use `tag-suffix:rc3`

**Priority:** Label-based suffix takes precedence over the workflow `tag_suffix` parameter.

### Do I need to setup Github Action access tokens or any other permission-related thing?

Github Actions will inject a token for this plugin to interact with the API.

You need to specify the following permission scopes for this action to work correctly:

```yaml
permissions:
  contents: write
  pull-requests: read
```

Alternatively, you can enable `read and write permission` to this token under `settings > actions > general > workflow permissions` (disabled by default), but beware that this provides all of the actions in your repository with read and write access to all scopes.

### Can I create a tag instead of a release?

Currently, no.

In order to reliably generate monotonic versions, we use Github Releases to
track what the last release version is. See [Release#get-the-latest-release](https://developer.github.com/v3/repos/releases/#get-the-latest-release).

### How can I get the generated tag or version?

On a successful release, this action creates an output parameters `tag_name`, `upload_url` and `version`. 

Example of how to consume this:

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
      - id: release
        uses: rymndhng/release-on-push-action@master
        with:
          bump_version_scheme: minor
          tag_prefix: v
          
      - name: Check Output Parameters
        run: |
          echo "Got tag name ${{ steps.release.outputs.tag_name }}"
          echo "Got release version ${{ steps.release.outputs.version }}"
          echo "Upload release artifacts to ${{ steps.release.outputs.upload_url }}"
```

### Can I customize the release body message?

Yes you can, with `release_body`. The contents of this be appended to the release description.

Example:

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
          release_body: "When set, adds extra text to body!"
```

### Can I use Github's Generated Release Notes?

Yes you can, by setting `use-github-release-notes` to `true`. For more information, see [Automatic Release Notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes).

Note, if enabling this feature:
- If you have set `release_body`, the `release_body` will be prepended to the Generated Release Notes
- Enabling this will disable the commit summary generated by this project

Example:

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
          use_github_release_notes: true
```


### Can I change the prefix `v` from the Git Tags?

Yes, you can customize this by changing the `tag_prefix`. Here's an example of
removing the prefix by using an empty string.

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
          tag_prefix: ""
```

### Can I add a suffix to the Git Tags for pre-releases?

Yes, you can add a suffix by using the `tag_suffix` parameter. This is useful for publishing alpha, beta, release candidate, or other pre-release versions.

``` yaml
on:
  push:
    branches:
      - develop

jobs:
  release-on-push:
    runs-on: ubuntu-latest
    env:
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    steps:
      - uses: rymndhng/release-on-push-action@master
        with:
          bump_version_scheme: minor
          tag_prefix: v
          tag_suffix: -alpha
```

This will create tags like `v1.2.3-alpha`. Other common suffixes include:
- `-beta` for beta releases (or `beta` without dash)
- `-rc1`, `-rc2`, etc. for release candidates (or `rc1`, `rc2` without dash)
- `-preview` for preview releases (or `preview` without dash)

**Note**: Suffixes can be with or without a leading dash. Both `v2.0.0-rc1` and `v2.0.0rc1` are supported.

**Releases with suffixes are automatically marked as pre-releases** in GitHub, following semantic versioning conventions.

**Tip**: To iterate on pre-release versions (e.g., v2.0.0-rc1 → v2.0.0-rc2), use `bump_version_scheme: keep` or add the `release:keep` label to your PR. This keeps the base version unchanged and only changes the suffix.

**Important**: When using `keep`, you must:
1. Provide a `tag_suffix` parameter (or `tag-suffix:VALUE` label)
2. Have a previous release with a suffix (e.g., `-rc1`, `-alpha`)
3. Use a different suffix than the previous release

Example workflows for iterating release candidates:

**Option 1: Using workflow configuration**
```yaml
# First release: v2.0.0-rc1 (using minor/major/patch)
# Next iteration: v2.0.0-rc2 (using keep)
- uses: rymndhng/release-on-push-action@master
  with:
    bump_version_scheme: keep
    tag_prefix: v
    tag_suffix: -rc2  # or "rc2" without dash
```

**Option 2: Using PR labels (recommended for iterative releases)**
```yaml
# Workflow stays the same, control via PR labels
- uses: rymndhng/release-on-push-action@master
  with:
    bump_version_scheme: minor  # overridden by PR label
    tag_prefix: v

# PR labels:
# - release:keep
# - tag-suffix:rc2
# Result: v2.0.0rc2 (if previous was v2.0.0rc1)
```

#### Controlling Pre-release Behavior

The `use_prerelease` parameter controls whether releases are marked as pre-releases:
- `auto` (default): Automatically marks as pre-release if `tag_suffix` is non-empty
- `true`: Always marks as pre-release, even without a suffix
- `false`: Never marks as pre-release, even with a suffix

Example of forcing a production release with a suffix:

``` yaml
- uses: rymndhng/release-on-push-action@master
  with:
    tag_prefix: v
    tag_suffix: -internal
    use_prerelease: false  # Prevent marking as pre-release
```

### Can I change the name of the Release?

Yes, you can customize this by changing the `release_name`. The `release_name` supports these template variables:

- `<RELEASE_VERSION>` contains the version number, i.e. `1.2.3`
- `<RELEASE_TAG>` contains the git tag name, i.e. `v1.2.3`

See example below for how to create a release with the name `Release 1.2.3`

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
          tag_prefix: "v"
          release_name: "Release <RELEASE_VERSION>"
```

### How can I configure the maximum number of commits to summarize?

Use the option `max_commits`. The default value is 50.

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
          max_commits: 100
```

## Development

Uses [babashka](https://github.com/borkdude/babashka)

To run tests:

1. Install [babashka](https://github.com/borkdude/babashka#installation).
2. Install [direnv](https://direnv.net/docs/installation.html)
3. Create a `.envrc` file at the project root from this template:

``` sh
export GITHUB_TOKEN=<YOUR_TOKEN>
export GITHUB_REPOSITORY=rymndhng/release-on-push-action
export GITHUB_SHA=167c690247d0933acde636d72352bcd67e33724b
export GITHUB_API_URL=https://api.github.com
export INPUT_BUMP_VERSION_SCHEME=minor
export INPUT_MAX_COMMITS=5
export INPUT_TAG_PREFIX=v
export INPUT_RELEASE_NAME="<RELEASE_TAG>"
export INPUT_DRY_RUN=1
```

2. Run Tests

``` sh
make test
make dryrun
```


## Big Thanks To

- shell-semver: https://github.com/fmahnke/shell-semver
- https://github.com/mikeal/publish-to-github-action/
- Inspiration: https://github.com/roomkey/lein-v
