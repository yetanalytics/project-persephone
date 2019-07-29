.PHONY: clean ci

clean:
	rm -rf target

ci:
	clean
	clojure -A:test:runner
