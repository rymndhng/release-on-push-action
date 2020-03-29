.PHONY: test

repl:
	~/Downloads/bb --verbose --classpath "src" --nrepl-server

test:
	bb --classpath "src:test" run_tests.clj
