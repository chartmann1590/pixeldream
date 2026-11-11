#!/usr/bin/env bash
# Tab QA: push screenshots (as small jpg), e2e log and filtered billing lines (no tokens) to branch tab-qa-evidence under ev/<name>/
set -u
D="$1"; N="$2"; B=tab-qa-evidence; GH_H="$(git config --get http.https://github.com/.extraheader)"; R=/tmp/evrepo
[ -d "$D" ] || { echo "no results dir"; exit 0; }
git -c http.extraheader="$GH_H" clone -q --depth 1 -b $B "https://github.com/$GITHUB_REPOSITORY" $R </dev/null || exit 0
O=$R/ev/$N; mkdir -p $O
for f in "$D"/*.png; do [ -f "$f" ] || continue; b=$(basename "$f" .png); ffmpeg -loglevel error -y -i "$f" -vf scale=540:-2 -q:v 6 "$O/$b.jpg" </dev/null || true; done
for f in "$D"/e2e.log "$D"/device.txt "$D"/version-*.txt "$D"/steps.log; do [ -f "$f" ] && cp "$f" $O/ ; done
for f in "$D"/*-billing.txt "$D"/*-fatal.txt; do [ -f "$f" ] || continue; grep -a -E -i 'license saved|checkout opened|entitlement|no purchases|restore|429|FATAL|Exception' "$f" | sed -E 's/[A-Za-z0-9_\-]{40,}\.[A-Za-z0-9_\-\.]{20,}/[token]/g; s/(cs_live_[A-Za-z0-9]{10})[A-Za-z0-9]+/\1…/g' | tail -60 > "$O/$(basename "$f")"; done
ls $O | wc -l
cd $R && git add ev && git -c user.name=tab-ci -c user.email=ci@tab.invalid commit -qm "evidence $N" || exit 0
for t in $(seq 1 10); do git -c http.extraheader="$GH_H" push -q origin HEAD:$B </dev/null && { echo pushed; exit 0; }; sleep $((RANDOM%6+2)); git -c http.extraheader="$GH_H" pull -q --rebase --depth 1 origin $B </dev/null || true; done
