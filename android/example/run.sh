#!/usr/bin/env bash
# Builds the example, points it at a project, and puts it on an emulator.
#
# The project is written into SharedPreferences rather than baked into the
# APK, so the same build can be aimed at a dev server and then at production.
set -euo pipefail

cd "$(dirname "$0")/.."
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
GRADLE="${GRADLE:-./gradlew}"
DEVICE="${DEVICE:-}"
PACKAGE="com.feedoback.example"

# A physical phone plugged into the same machine is not where this belongs.
if [ -z "$DEVICE" ]; then
  DEVICE=$("$ADB" devices | awk '/^emulator-.*device$/ {print $1; exit}')
fi
if [ -z "$DEVICE" ]; then
  echo "No emulator running. Start one, or set DEVICE." >&2
  exit 1
fi

KEY="${FEEDOBACK_KEY:-pk_missing}"
HOST="${FEEDOBACK_HOST:-http://10.0.2.2:3000}"

echo "Building…"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" "$GRADLE" :example:assembleDebug -q --console=plain

echo "Installing onto $DEVICE…"
"$ADB" -s "$DEVICE" install -r example/build/outputs/apk/debug/example-debug.apk >/dev/null

# The app has to exist before its data directory does.
"$ADB" -s "$DEVICE" shell am start -n "$PACKAGE/.SettingsActivity" >/dev/null
sleep 2
"$ADB" -s "$DEVICE" shell am force-stop "$PACKAGE"

PREFS="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <string name=\"key\">$KEY</string>
  <string name=\"host\">$HOST</string>
$( [ -n "${FEEDOBACK_USER:-}" ] && echo "  <string name=\"user\">$FEEDOBACK_USER</string>" )
$( [ -n "${FEEDOBACK_EMAIL:-}" ] && echo "  <string name=\"email\">$FEEDOBACK_EMAIL</string>" )
$( [ -n "${FEEDOBACK_HASH:-}" ] && echo "  <string name=\"hash\">$FEEDOBACK_HASH</string>" )
</map>"

echo "$PREFS" | "$ADB" -s "$DEVICE" shell "run-as $PACKAGE sh -c 'mkdir -p shared_prefs && cat > shared_prefs/example.xml'"

"$ADB" -s "$DEVICE" shell am start -n "$PACKAGE/.SettingsActivity" >/dev/null
echo "Running $PACKAGE against $HOST"
