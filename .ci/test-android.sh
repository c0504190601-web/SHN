#!/usr/bin/env bash
set -e
gradle --no-daemon :fixture:assembleDebug
adb install -r fixture/build/outputs/apk/debug/fixture-debug.apk
set +e
timeout 360s gradle --no-daemon :app:connectedDebugAndroidTest
result=$?
mkdir -p test-evidence
timeout 15s adb pull /sdcard/Android/data/com.kioskmdm/files/test-evidence/. test-evidence/
timeout 15s adb exec-out screencap -p > test-evidence/final-screen.png
timeout 15s adb logcat -d > test-evidence/logcat.txt
timeout 15s adb shell dumpsys activity activities > test-evidence/activities.txt
timeout 15s adb shell dumpsys accessibility > test-evidence/accessibility.txt
exit "$result"
