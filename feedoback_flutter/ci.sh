#!/usr/bin/env bash
# Every check this package has to pass, in one place.
set -euo pipefail
cd "$(dirname "$0")"

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

step "Version"
PUBSPEC=$(sed -n 's/^version: *//p' pubspec.yaml)
LOG=$(sed -n 's/^## \([0-9][^ ]*\).*/\1/p' CHANGELOG.md | head -1)
if [ "$PUBSPEC" != "$LOG" ]; then
  echo "pubspec.yaml says $PUBSPEC, CHANGELOG.md says $LOG." >&2
  exit 1
fi
echo "$PUBSPEC"

step "Dependencies"
flutter pub get

step "Analysis"
flutter analyze

step "Tests"
flutter test

step "What pub.dev receives"
# The validation pub.dev runs on upload, minus the upload. Warnings fail it:
# every one costs the package points on its page.
flutter pub publish --dry-run

printf '\n\033[1mAll checks passed.\033[0m\n'
