#!/usr/bin/env bash
# Every check this package has to pass, in one place. The native halves are
# thin bridges to the iOS and Android SDKs, whose own jobs test them; the
# example apps that exercise the bridges need a simulator and an emulator and
# are verified by hand.
set -euo pipefail
cd "$(dirname "$0")"

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

step "Version"
PACKAGE=$(node -p 'require("./package.json").version')
LOG=$(sed -n 's/^## \([0-9][^ ]*\).*/\1/p' CHANGELOG.md | head -1)
if [ "$PACKAGE" != "$LOG" ]; then
  echo "package.json says $PACKAGE, CHANGELOG.md says $LOG." >&2
  exit 1
fi
echo "$PACKAGE"

step "Types"
npm run typecheck

step "Tests"
npm test

step "Build"
rm -rf lib
npm run build

step "What npm receives"
# Read from the tarball rather than trusted from `files`: tests and build
# output have both leaked into it before.
CONTENTS=$(npm pack --dry-run --json | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s)[0].files.map(f=>f.path).join("\n")))')
for path in lib/commonjs/index.js lib/module/index.js lib/typescript/index.d.ts FeedobackReactNative.podspec CHANGELOG.md LICENSE; do
  grep -qx "$path" <<<"$CONTENTS" || { echo "The tarball has no $path" >&2; exit 1; }
done
if grep -E '\.test\.|^android/build/|^android/\.gradle/' <<<"$CONTENTS"; then
  echo "The tarball carries files no customer needs." >&2
  exit 1
fi
echo "$(wc -l <<<"$CONTENTS" | tr -d ' ') files"

printf '\n\033[1mAll checks passed.\033[0m\n'
