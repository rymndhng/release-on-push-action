.PHONY: deps

lib/semver:
	$(shell curl https://raw.githubusercontent.com/fsaintjacques/semver-tool/master/src/semver > lib/semver)
	$(shell echo "\n# Last pulled: $(shell date -u)" >> lib/semver)
	$(shell chmod +x lib/semver)

deps: lib/semver
