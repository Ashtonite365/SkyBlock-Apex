#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Settings rewrite (REWRITE_PLAN.md §8): merges translations/rewrite/<chunk>.json into translations/en_us.json.

Key merge only: new keys are added, identical values are skipped, a different value for an existing key is an error
unless --override is given (then the chunk wins). Output keeps en_us.json's format (jq -S --tab: sorted, tab indent),
so check-translation-ordering.sh stays green. Usage: scripts/merge-rewrite-translations.py [--override] [files...]
"""
import json, pathlib, sys
root = pathlib.Path(__file__).resolve().parent.parent
args = [a for a in sys.argv[1:] if a != "--override"]
override = "--override" in sys.argv
files = [pathlib.Path(a) for a in args] or sorted((root / "translations/rewrite").glob("*.json"))
target = root / "translations/en_us.json"
en = json.loads(target.read_text())
conflicts, added = [], 0
for f in files:
    for k, v in json.loads(f.read_text()).items():
        if k in en and en[k] != v:
            conflicts.append(f"{f.name}: {k}: {en[k]!r} -> {v!r}")
            if not override:
                continue
        elif k in en:
            continue
        else:
            added += 1
        en[k] = v
text = json.dumps(dict(sorted(en.items())), indent="\t", ensure_ascii=False) + "\n"
target.write_text(text)
print(f"added {added} keys from {len(files)} file(s)")
if conflicts:
    print("\n".join(conflicts), file=sys.stderr)
    sys.exit(0 if override else 1)
