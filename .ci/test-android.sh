#!/usr/bin/env bash
set -e
gradle --no-daemon :fixture:assembleDebug
adb install -r fixture/build/outputs/apk/debug/fixture-debug.apk
set +e
gradle --no-daemon :app:connectedDebugAndroidTest
result=$?
mkdir -p test-evidence
adb pull /sdcard/Android/data/com.kioskmdm/files/test-evidence/. test-evidence/
exit "$result"
