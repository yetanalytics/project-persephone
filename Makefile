.PHONY: clean ci

clean:
	rm -rf target cljs-test-runner-out

test-clj:
	clojure -A:test:runner-clj

test-cljs:
	clojure -A:test:runner-cljs

coverage:
	clojure -A:test:runner-coverage

ci: clean test-clj test-cljs
