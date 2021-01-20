.PHONY: clean ci

clean:
	rm -rf target

ci: clean
	clojure -M:test:runner
