#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Regenerates CREDITS.md (repo root and jar copy) from src/main/resources/META-INF/apex/credits.json."""
import json, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
data = json.loads((root / "src/main/resources/META-INF/apex/credits.json").read_text())
out = ["# SkyBlock Apex credits", "",
       "SkyBlock Apex is based on Firmament by nea89o and contributors and is licensed GPL-3.0-or-later.",
       "Generated from `META-INF/apex/credits.json` by `scripts/gen-credits.py`; edit the JSON, not this file.",
       "This lists only projects whose code or textures were copied or ported. Projects that only inspired a",
       "feature (reimplemented from descriptions, no code copied) are named in the file headers instead.", ""]
for e in data["entries"]:
    out.append(f"## {e['name']}")
    out.append("")
    out.append(f"- Authors: {e['authors']}")
    out.append(f"- Licence: {e['licence']}")
    if e.get("link"):
        out.append(f"- Source: {e['link']}")
    for u in e["used"]:
        out.append(f"- {u}")
    out.append("")
out += ["## Rules", "",
        "Only GPL, LGPL, MIT, BSD, Apache-2.0 or CC-BY (with attribution) code and textures. AGPL and CC BY-NC(-SA)",
        "projects are reimplemented, never copied. Never Aaron's Mod, NC/NC-SA or all-rights-reserved assets. Every",
        "copied file needs a header naming its source and licence, plus an entry in credits.json.", ""]
text = "\n".join(out)
(root / "CREDITS.md").write_text(text)
(root / "src/main/resources/META-INF/apex/CREDITS.md").write_text(text)
