#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
import json, sys

FIELDS = ("name", "type", "shape", "cores", "maxSecrets", "crypts")
SOURCE = ("Room names, types, shapes, core hashes, secret and crypt counts from a BSD-3-Clause room list "
          "(Copyright (c) 2025, odtheking; see NOTICE). Built by tools/build-secret-rooms.py")

def build(rooms):
    out = []
    for r in sorted(rooms, key=lambda r: r["name"]):
        out.append({f: r.get(f) for f in FIELDS})
    return {"_source": SOURCE, "rooms": out}

if __name__ == "__main__":
    src, dst = sys.argv[1:3]
    data = build(json.load(open(src)))
    with open(dst, "w") as f:
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print(f"{len(data['rooms'])} rooms -> {dst}")
