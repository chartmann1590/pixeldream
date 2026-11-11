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
# Tab QA Nov 11 2026: signals ride git branch tab-qa-signal (the APK drop worker is over its daily limit).
SIGB=tab-qa-signal; GH_H="$(git config --get http.https://github.com/.extraheader)"; SR=/tmp/sigrepo
gx(){ git -c http.extraheader="$GH_H" -C "$SR" "$@"; }
[ -d $SR ] || git -c http.extraheader="$GH_H" clone -q --depth 1 -b $SIGB "https://github.com/$GITHUB_REPOSITORY" $SR </dev/null
put(){ for t in $(seq 1 10); do gx fetch -q --depth 1 origin $SIGB </dev/null && gx reset -q --hard FETCH_HEAD && mkdir -p $SR/sig && echo "$2" > "$SR/sig/$P-$1" && gx add sig && gx -c user.name=tab-ci -c user.email=ci@tab.invalid commit -qm "sig $P $1" && gx push -q origin HEAD:$SIGB </dev/null && return 0; sleep $((RANDOM%6+2)); done; return 1; }
get(){ gx fetch -q --depth 1 origin $SIGB </dev/null >/dev/null 2>&1; git -C "$SR" show "FETCH_HEAD:sig/$P-$1" 2>/dev/null; }
log(){ echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/e2e.log"; }
adb wait-for-device; adb shell getprop ro.build.version.release </dev/null | sed 's/^/android /' | tee $OUT/device.txt
adb shell cat /proc/meminfo </dev/null | head -2 | tee -a $OUT/device.txt
adb uninstall "$PKG" </dev/null >/dev/null 2>&1 || true
APKA="$APK"; [ -f dsk-apk/old.apk ] && APKA=dsk-apk/old.apk && log "phase A runs the PREVIOUS release (old.apk); A2 installs the new APK over it as an update"
log "phase A"; KEEP_APP=1 SETTLE=15 bash $D/e2e-driver.sh "$APKA" "$PKG" "$E/A.txt" A
adb shell dumpsys package "$PKG" </dev/null | grep -E "versionCode|versionName|lastUpdateTime|firstInstallTime" | sed "s/^ *//" > $OUT/version-A.txt; cat $OUT/version-A.txt | tee -a $OUT/e2e.log
put ready "$(date -u +%FT%TZ)"; log "ready posted; waiting for go"
g=""; for i in $(seq 1 180); do g=$(get go) && [ -n "$g" ] && break; sleep 20; done
log "go=$g"
log "phase A2"; KEEP_APP=1 SETTLE=15 bash $D/e2e-driver.sh "$APK" "$PKG" "$E/A2.txt" A2
adb shell dumpsys package "$PKG" </dev/null | grep -E "versionCode|versionName|lastUpdateTime|firstInstallTime" | sed "s/^ *//" > $OUT/version-A2.txt; cat $OUT/version-A2.txt | tee -a $OUT/e2e.log
log "uninstall"; adb uninstall "$PKG" </dev/null | tee -a $OUT/e2e.log
log "phase B"; KEEP_APP=1 SETTLE=15 bash $D/e2e-driver.sh "$APK" "$PKG" "$E/B.txt" B
log "phase B2"; KEEP_APP=0 SETTLE=15 bash $D/e2e-driver.sh "$APK" "$PKG" "$E/B2.txt" B2
put done "$(date -u +%FT%TZ)"
ls -la $OUT | tail -80
