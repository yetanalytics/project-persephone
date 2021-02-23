.PHONY: clean ci

clean:
	rm -rf target cljs-test-runner-out

test-clj:
	clojure -A:test:runner-deps-clj:run-clj

test-gen-clj:
	clojure -A:gen:runner-deps-clj:run-gen-clj

test-cljs:
	clojure -A:test:runner-deps-cljs:run-cljs

test-gen-cljs:
	clojure -A:gen:runner-deps-cljs:run-gen-cljs

coverage:
	clojure -A:test:runner-cov

ci: clean test-clj test-gen-clj test-cljs test-gen-cljs
