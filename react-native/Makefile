# feedoback-react-native — build and release
#
# Quality gates first, then version, then publish. `make check` is what has to
# pass before a release; `prepublishOnly` re-runs it as a backstop. npm publish
# uses the credentials in your environment — run `npm login` once beforehand.

.PHONY: install build typecheck test check clean \
        release-patch release-minor release-major publish

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

release-patch: check ## Bug fixes: 0.1.0 -> 0.1.1
	npm version patch
	$(MAKE) publish
	git push --follow-tags

release-minor: check ## New features, backwards compatible: 0.1.0 -> 0.2.0
	npm version minor
	$(MAKE) publish
	git push --follow-tags

release-major: check ## Breaking changes: 0.1.0 -> 1.0.0
	npm version major
	$(MAKE) publish
	git push --follow-tags

publish: ## Publish the current version to npm (public)
	npm publish --access public
