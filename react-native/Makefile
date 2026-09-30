# feedoback-react-native — build and release
#
# Quality gates first, then version, then publish. `make check` is what has to
# pass before a release; `prepublishOnly` re-runs it as a backstop. npm publish
# uses the credentials in your environment — run `npm login` once beforehand.

.PHONY: install build typecheck test check clean \
        release release-dry-run

install: ## Install dependencies
	npm install

build: ## Build the lib/ bundles
	npm run build

typecheck: ## Type-check without emitting
	npm run typecheck

test: ## Unit tests (Vitest)
	npm test

check: typecheck test ## Everything that must pass before a release

clean: ## Remove build output
	rm -rf lib android/build android/.gradle

release: ## Publish the version package.json and CHANGELOG.md name, through every gate
	../../scripts/release/react-native.sh $$(node -p "require('./package.json').version")

release-dry-run: ## Every check and gate, stopping before anything is published
	DRY_RUN=1 ../../scripts/release/react-native.sh $$(node -p "require('./package.json').version")
