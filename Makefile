.PHONY: clean ci

clean:
	rm -rf target cljs-test-runner-out

test-clj:
	clojure -A:test:runner-clj

test-cljs:
	clojure -A:test:runner-cljs

# TODO: Re-incorporate into main test targets once Datasim is updated

test-clj-gen:
	clojure -A:test:gen:runner-clj

test-cljs-gen:
	clojure -A:test:gen:runner-cljs

coverage:
	clojure -A:test:runner-coverage

ci: clean test-clj test-cljs
