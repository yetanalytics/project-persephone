.PHONY: clean ci

clean:
	rm -rf target

test-clj:
	clojure -A:test:runner-clj

test-cljs:
	clojure -A:test:runner-cljs

ci: clean test-clj test-cljs
