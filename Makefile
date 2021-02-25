.PHONY: clean ci

clean:
	rm -rf target cljs-test-runner-out

test-clj:
	clojure -A:test:runner-clj

test-gen-clj:
	clojure -A:test:runner-gen-clj

test-cljs:
	clojure -A:test:runner-cljs

test-gen-cljs:
	clojure -A:test:runner-gen-cljs

coverage:
	clojure -A:test:runner-cov

ci: clean test-clj test-gen-clj test-cljs test-gen-cljs
