# feedoback-react — build and release
#
# Quality gates first, then version, then publish. `make release` is the whole
# thing: it will not publish unless typecheck, tests, e2e and build all pass
# (prepublishOnly re-runs the checks as a backstop). npm publish itself uses the
# credentials in your environment — run `npm login` once beforehand.

.PHONY: install build dev typecheck test e2e check clean \
        release-patch release-minor release-major publish

install: ## Install dependencies
	npm install

build: ## Build the dist bundles
	npm run build

dev: ## Rebuild on change
	npm run dev

typecheck: ## Type-check without emitting
	npm run typecheck

test: ## Unit + component tests (Vitest)
	npm test

e2e: ## Browser end-to-end tests (Playwright)
	npm run test:e2e

check: typecheck test e2e ## Everything that must pass before a release

clean: ## Remove build output
	rm -rf dist e2e/.artifacts playwright-report test-results

# Release: verify, bump the version (creates a git tag), publish, push the tag.
# Pick the bump that matches the change.
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
