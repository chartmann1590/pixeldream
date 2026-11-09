#!/usr/bin/env bash
# Tab Stripe restore end-to-end on the CI emulator (PixelDream needs >= 6 GB RAM, more than the Pixel 3a has).
# A: fresh install, open Stripe checkout from the app (buyer email) -> signal "ready" on Tab's private drop, wait for
# "go" (Tab completes that checkout for $0 with a single-use 100%-off code) -> A2: relaunch, unlocked? ->
# uninstall + reinstall -> B: Restore with an unknown email (must be refused), then with the buyer's email -> B2: relaunch.
set -u
export OUT=/tmp/dsk-out; mkdir -p $OUT
D=.github/direct; E=$D/e2e/$E2E_APP
APK=dsk-apk/app.apk; PKG=$E2E_PKG
U=https://hs-apk-drop.charles-h-hartmann1.workers.dev; P="e2e-${E2E_APP}-${E2E_ID}"
put(){ echo "$2" | curl -s -m 30 -X PUT -H "X-Key: $DROP_KEY" --data-binary @- "$U/c/$P-$1/0" >/dev/null; }
get(){ curl -sf -m 30 -H "X-Key: $DROP_KEY" "$U/c/$P-$1/0"; }
log(){ echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/e2e.log"; }
adb wait-for-device; adb shell getprop ro.build.version.release </dev/null | sed 's/^/android /' | tee $OUT/device.txt
adb shell cat /proc/meminfo </dev/null | head -2 | tee -a $OUT/device.txt
adb uninstall "$PKG" </dev/null >/dev/null 2>&1 || true
log "phase A"; KEEP_APP=1 bash $D/direct-test.sh "$APK" "$PKG" "$E/A.txt" A
put ready "$(date -u +%FT%TZ)"; log "ready posted; waiting for go"
g=""; for i in $(seq 1 120); do g=$(get go) && [ -n "$g" ] && break; sleep 10; done
log "go=$g"
log "phase A2"; KEEP_APP=1 bash $D/direct-test.sh "$APK" "$PKG" "$E/A2.txt" A2
log "uninstall"; adb uninstall "$PKG" </dev/null | tee -a $OUT/e2e.log
log "phase B"; KEEP_APP=1 bash $D/direct-test.sh "$APK" "$PKG" "$E/B.txt" B
log "phase B2"; KEEP_APP=0 bash $D/direct-test.sh "$APK" "$PKG" "$E/B2.txt" B2
put done "$(date -u +%FT%TZ)"
ls -la $OUT | tail -80
