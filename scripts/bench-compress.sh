#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
RAW=$1 ZST=$2 PACK=$3 FORKS=${4:-5} WARM=${5:-20} ITERS=${6:-30}
J=${JAVA_HOME:?}/bin
T=$(mktemp -d /tmp/apex-bench.XXXXXX); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/old" "$T/cls"
for f in $(git ls-tree --name-only 59c9f067 src/main/java/dev/ashtonite365/apex/thirdparty/zstd/ | grep '\.java$'); do git show "59c9f067:$f" > "$T/old/$(basename "$f")"; done
CP="$T/cls:test-libs/aircompressor-v3-3.13.jar"
"$J/javac" -nowarn -XDsuppressNotes -d "$T/cls" "$T/old"/*.java scripts/bench/OldAirOneShot.java src/main/java/dev/ashtonite365/apex/compress/*.java 2>/dev/null
"$J/javac" -nowarn -cp "$CP" -d "$T/cls" scripts/bench/CompressBench.java
CODECS="ours-zstd air-zstd libzstd ours-pack ours-zstd-stream air-zstd-stream ours-pack-stream"
for i in $(seq "$FORKS"); do
	for c in $CODECS; do
		case $c in *pack*) IN=$PACK ;; *) IN=$ZST ;; esac
		"$J/java" -Xms2g -Xmx2g -XX:+UseParallelGC --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
			-cp "$CP" CompressBench "$c" "$IN" "$RAW" "$WARM" "$ITERS" | tee -a "$T/out"
	done
done
echo "== median of $FORKS fork medians (ms)  sizes: zst $(stat -c%s "$ZST") pack $(stat -c%s "$PACK") raw $(stat -c%s "$RAW")"
for c in $CODECS; do
	grep "^$c median" "$T/out" | awk '{print $3}' | sort -n | awk -v c="$c" '{v[NR]=$1} END {printf "%-18s %8.2f  (forks: ", c, v[int((NR+1)/2)]; for (i=1;i<=NR;i++) printf "%s ", v[i]; print ")"}'
done
