#!/usr/bin/env bash
# Tab QA: push screenshots (as small jpg), UI dumps, e2e log and filtered billing lines (no tokens) to branch tab-qa-evidence under ev/<name>/
set -u
D="$1"; N="$2"; B=tab-qa-evidence; GH_H="$(git config --get http.https://github.com/.extraheader)"; R=/tmp/evrepo
[ -d "$D" ] || { echo "no results dir"; exit 0; }
ls -la "$D" | head -100
git -c http.extraheader="$GH_H" clone -q --depth 1 -b $B "https://github.com/$GITHUB_REPOSITORY" $R </dev/null || exit 0
O=$R/ev/$N; mkdir -p $O
command -v ffmpeg >/dev/null || python3 -c "import PIL" 2>/dev/null || python3 -m pip install -q --user pillow >/dev/null 2>&1 || true
for f in "$D"/*.png; do [ -f "$f" ] || continue; b=$(basename "$f" .png)
  if command -v ffmpeg >/dev/null; then ffmpeg -loglevel error -y -i "$f" -vf scale=540:-2 -q:v 6 "$O/$b.jpg" </dev/null || true
  else python3 -c "import sys;from PIL import Image;im=Image.open(sys.argv[1]).convert('RGB');w,h=im.size;im.resize((540,int(h*540/w))).save(sys.argv[2],quality=60)" "$f" "$O/$b.jpg" || true; fi
done
for f in "$D"/*.log "$D"/device.txt "$D"/version-*.txt; do [ -f "$f" ] && cp "$f" $O/ ; done
for f in "$D"/*.txt; do b=$(basename "$f"); case "$b" in *-billing.txt|*-fatal.txt|*-logcat.txt|*-stream.txt|*-crash.txt|*-ads.txt) continue;; esac; [ -f "$O/$b" ] || cp "$f" $O/; done
for f in "$D"/*-billing.txt "$D"/*-fatal.txt; do [ -f "$f" ] || continue; grep -a -E -i 'license saved|checkout opened|entitlement|no purchases|restore|429|FATAL|Exception' "$f" | sed -E 's/[A-Za-z0-9_\-]{40,}\.[A-Za-z0-9_\-\.]{20,}/[token]/g; s/(cs_live_[A-Za-z0-9]{10})[A-Za-z0-9]+/\1…/g' | tail -60 > "$O/$(basename "$f")"; done
ls $O | wc -l
cd $R && git add ev && git -c user.name=tab-ci -c user.email=ci@tab.invalid commit -qm "evidence $N" || exit 0
for t in $(seq 1 10); do git -c http.extraheader="$GH_H" push -q origin HEAD:$B </dev/null && { echo pushed; exit 0; }; sleep $((RANDOM%6+2)); git -c http.extraheader="$GH_H" pull -q --rebase --depth 1 origin $B </dev/null || true; done
