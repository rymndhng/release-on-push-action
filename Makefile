.PHONY: deps test

lib/semver:
	$(shell curl https://raw.githubusercontent.com/fmahnke/shell-semver/master/increment_version.sh > lib/semver)
	$(shell echo "\n# Last pulled: $(shell date -u)" >> lib/semver)
	$(shell chmod +x lib/semver)

deps: lib/semver

test:
	bats test
