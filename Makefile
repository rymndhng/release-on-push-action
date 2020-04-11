.PHONY: test integration-test

repl:
	~/Downloads/bb --verbose --classpath "src" --nrepl-server

test:
	bb --classpath "src:test" run_tests.clj

integration-test:
	bb --verbose --main release-on-push-action.core --dry-run
