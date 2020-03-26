.PHONY: test

test:
	bb --classpath "src:test" run_tests.clj
