.PHONY: clean

clean:
	rm -rf target cljs-test-runner-out

################################################################################
# Testing/CI
################################################################################

.PHONY: test-clj test-cljs coverage ci

test-clj:
	clojure -A:test:runner-clj

test-cljs:
	clojure -A:test:runner-cljs

coverage:
	clojure -A:test:runner-coverage

ci: clean test-clj test-cljs

################################################################################
# Command Line Interface
################################################################################

.PHONY: bundle

target/bundle/cli.jar:
	clojure -T:build uber :jar cli

target/bundle/bin:
	mkdir -p target/bundle/bin
	cp bin/*.sh target/bundle/bin
	chmod +x target/bundle/bin/*.sh

target/bundle: target/bundle/cli.jar target/bundle/bin

bundle: target/bundle
