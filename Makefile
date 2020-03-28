.PHONY: test

repl:
	~/Downloads/bb --classpath "src" --nrepl-server

test:
	bb --classpath "src:test" run_tests.clj
