#!/usr/bin/env bash
# What the SDK costs a customer's APK.
#
# A budget rather than a measurement, for the same reason widget.js has one:
# this ships inside every copy of somebody's app. Without a number that fails
# a build, a dependency gets added one afternoon and nobody notices until an
# app is a megabyte heavier.
#
# Measured as dex, not as the jar. A jar is JVM bytecode that no phone runs;
# what lands in an APK is what d8 makes of it, and the two differ by enough
# that budgeting the wrong one budgets nothing.
#
# The numbers: the SDK measured 150 kB the first time this script was written,
# which is where the budget sits plus enough headroom that an ordinary change
# does not fail the build while a new dependency does. The method count is a
# tripwire and not a limit — it mattered when the 64k dex ceiling forced a
# customer into a multidex build, and minSdk 24 has native multidex, so what
# it catches now is something arriving that nobody meant to add.
set -euo pipefail

cd "$(dirname "$0")"
BUDGET_KB=${BUDGET_KB:-160}
BUDGET_METHODS=${BUDGET_METHODS:-1400}
BUILD="build/size"

: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
D8=$(ls -d "$ANDROID_HOME"/build-tools/*/d8 2>/dev/null | sort -V | tail -1)
if [ -z "$D8" ]; then
  echo "No d8 in $ANDROID_HOME/build-tools. Set ANDROID_HOME." >&2
  exit 1
fi

JAR="feedoback/build/intermediates/aar_main_jar/release/syncReleaseLibJars/classes.jar"
if [ ! -f "$JAR" ]; then
  echo "Build the library first: gradle :feedoback:assembleRelease" >&2
  exit 1
fi

rm -rf "$BUILD"; mkdir -p "$BUILD"
# --release so it is optimised the way a customer's release build would be.
"$D8" --release --min-api 24 --output "$BUILD" \
  --lib "$ANDROID_HOME/platforms/android-35/android.jar" "$JAR"

BYTES=$(stat -f%z "$BUILD/classes.dex" 2>/dev/null || stat -c%s "$BUILD/classes.dex")
KB=$(( BYTES / 1024 ))

DEXDUMP=$(dirname "$D8")/dexdump
# Read to the end rather than exiting on the first match: leaving dexdump
# writing into a closed pipe is a SIGPIPE, and `pipefail` turns that into a
# failed build with nothing printed.
METHODS=$("$DEXDUMP" -f "$BUILD/classes.dex" | awk -F': ' '/method_ids_size/ {v=$2} END {print v+0}')

echo "feedoback-android: ${KB} kB of dex, ${METHODS} methods"
echo "                   (budget ${BUDGET_KB} kB, ${BUDGET_METHODS} methods)"

OVER=0
if [ "$KB" -gt "$BUDGET_KB" ]; then
  echo "Over the size budget by $(( KB - BUDGET_KB )) kB." >&2
  OVER=1
fi
if [ "$METHODS" -gt "$BUDGET_METHODS" ]; then
  echo "Over the method budget by $(( METHODS - BUDGET_METHODS ))." >&2
  OVER=1
fi
if [ "$OVER" -eq 1 ]; then
  echo "This ships inside every copy of a customer's app. Take something out." >&2
  exit 1
fi
