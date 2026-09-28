#!/usr/bin/env bash
# App-launch smoke test, run by reactivecircus/android-emulator-runner in
# .github/workflows/android-ci.yml once the emulator has booted.
#
# Why a script: the runner action executes each line of its `script:` input as
# a separate `sh -c`, so retries and diagnostics can't be written inline.
#
# Why every adb call is bounded: in the runs that hung (#4), the emulator's adb
# transport closed ~30 s after the app started (`adb shell pidof` → "error:
# closed"), and the old failure branch's `adb logcat` then sat in "waiting for
# device" until GitHub's 6 h job limit. adb never gives up on its own.

set -u

PKG=com.flux.hourglass
APK=app/build/outputs/apk/debug/app-debug.apk

# Input: seconds, then adb arguments. Output: adb's output and exit status, or
# 124 when it ran out of time.
adbt() {
  local secs=$1
  shift
  timeout "$secs" adb "$@"
}

# Why: a failed run should say whether the app crashed or the emulator itself
# went away — those need different fixes, and the old job printed neither.
diagnose() {
  echo "::group::Diagnostics"
  adbt 10 devices -l || true
  pgrep -af qemu-system || echo "emulator process is not running"
  adbt 30 logcat -d -t 300 '*:E' || echo "logcat unavailable (device unreachable)"
  # The emulator has died without a word on stdout; the kernel log shows an
  # OOM kill or a KVM fault if that was the cause.
  sudo -n dmesg 2>/dev/null | tail -n 40 || true
  echo "::endgroup::"
}

fail() {
  echo "::error::$1"
  diagnose
  exit 1
}

adbt 120 wait-for-device || fail "emulator not reachable over adb"
adbt 300 install --no-streaming -r "$APK" || fail "install failed"
adbt 60 shell am start -W -n "$PKG/$PKG.MainActivity" || fail "am start failed"
sleep 5

# Two tries: if the adb transport dropped but the emulator is still up, a fresh
# adb server rediscovers it (emulators are found by port scan) and the second
# check tells a transport glitch apart from a dead app or a dead emulator.
for attempt in 1 2; do
  pid=$(adbt 30 shell pidof "$PKG" | tr -d '\r')
  if [ -n "$pid" ]; then
    echo "App is running (pid $pid)."
    exit 0
  fi
  [ "$attempt" = 2 ] && break
  echo "::warning::pidof found no app process (attempt $attempt); restarting the adb server and checking again"
  adb kill-server >/dev/null 2>&1 || true
  adbt 30 start-server >/dev/null 2>&1 || true
  adbt 60 wait-for-device || fail "emulator did not come back after restarting the adb server"
done

fail "$PKG is not running 5 s after launch"
