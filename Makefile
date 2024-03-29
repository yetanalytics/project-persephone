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

MACHINE ?= $(shell bin/machine.sh)
JAVA_MODULES ?= $(shell cat .java_modules)
BUNDLE_RUNTIMES ?= true # false for Docker

target/bundle/cli.jar:
	clojure -T:build uber :jar cli

target/bundle/server.jar:
	clojure -T:build uber :jar server

target/bundle/bin:
	mkdir -p target/bundle/bin
	cp bin/*.sh target/bundle/bin
	chmod +x target/bundle/bin/*.sh

target/bundle/runtimes:
	mkdir -p target/bundle/runtimes
	jlink --output target/bundle/runtimes/${MACHINE} --add-modules ${JAVA_MODULES}

ifeq ($(BUNDLE_RUNTIMES),true)
target/bundle: target/bundle/cli.jar target/bundle/server.jar target/bundle/bin target/bundle/runtimes
else
target/bundle: target/bundle/cli.jar target/bundle/server.jar target/bundle/bin
endif

bundle: target/bundle
