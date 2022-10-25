.PHONY: test integration-test

repl:
	bb --verbose --classpath "src:test" --nrepl-server

test:
	bb --classpath "src:test" run_tests.clj

dryrun:
	bb --verbose --classpath "src" --main release-on-push-action.core --dry-run
