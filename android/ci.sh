#!/usr/bin/env bash
# Every check this package has to pass, in one place — the workflow runs this
# file rather than a list of steps, as the iOS package does.
set -euo pipefail
cd "$(dirname "$0")"

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

step "Version"
# The coordinate's version and the changelog's newest entry, which a release
# reads for its notes. Only kept from drifting here.
GRADLE_VERSION=$(sed -n 's/^version = "\(.*\)"$/\1/p' feedoback/build.gradle.kts)
LOG=$(sed -n 's/^## \([0-9][^ ]*\).*/\1/p' CHANGELOG.md | head -1)
if [ "$GRADLE_VERSION" != "$LOG" ]; then
  echo "build.gradle.kts says $GRADLE_VERSION, CHANGELOG.md says $LOG." >&2
  exit 1
fi
echo "$GRADLE_VERSION"

step "Tests"
# The wire tests read packages/protocol/fixtures, a directory up.
./gradlew --no-daemon :feedoback:testDebugUnitTest

step "Release build"
./gradlew --no-daemon :feedoback:assembleRelease

step "Size budget"
./size.sh

step "What Central receives"
# Published into build/staging, the directory the release zips for Central,
# and read back: the four jars and a POM carrying the blocks Central refuses
# a release without. Unsigned here; signing is the release's job.
rm -rf feedoback/build/staging
./gradlew --no-daemon :feedoback:publishReleasePublicationToStagingRepository
DIR="feedoback/build/staging/com/feedoback/feedoback-android/$GRADLE_VERSION"
for file in "$GRADLE_VERSION.aar" "$GRADLE_VERSION.pom" "$GRADLE_VERSION-sources.jar" "$GRADLE_VERSION-javadoc.jar"; do
  test -s "$DIR/feedoback-android-$file" || { echo "Missing feedoback-android-$file" >&2; exit 1; }
done
for block in "<name>" "<description>" "<url>" "<licenses>" "<developers>" "<scm>"; do
  grep -q "$block" "$DIR/feedoback-android-$GRADLE_VERSION.pom" || { echo "The POM has no $block" >&2; exit 1; }
done
echo "aar, pom, sources and javadoc; the POM is complete"

printf '\n\033[1mAll checks passed.\033[0m\n'
