.PHONY: clean ci

clean:
	rm -rf target cljs-test-runner-out

test-clj:
	clojure -M:test:runner-clj

test-cljs:
	clojure -M:test:runner-cljs

coverage:
	clojure -M:test:runner-cov

ci: clean test-clj test-cljs
