#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Applies the approved settings regroup plan (GROUPS.json, 29 Oct 2026) to the settings resources.

Reads the plan and the current migration-map.tsv and writes:
  - migration-map.tsv     : every planned option gets new_path = <tab>.<subsection>.<config.key> and group = <section>
                            ('' for a tab's own page); rows the plan does not list are kept as they are; options the map
                            did not have yet (and the new rows) are appended in one block.
  - layout-order.tsv      : main, group, subsection, config.key in plan order (screen order of pages, subsections, rows).
  - layout-moves-1006.tsv : old tree path -> new tree path, for migration 1006 of settings-v2.json.
  - translations/en_us.json: tab, page and subsection labels from the plan; label and description of the new rows when
                            missing; the typed warning sign dropped from the labels the plan lists as allowed.
Usage: settings_groups.py GROUPS.json src/main/resources/assets/apex/settings translations/en_us.json
"""
import json, sys, os, re

KINDS = {"riskySubToggle": "riskyToggle", "keyBindingExact": "keyBinding", "map": "option", "decimal": "option",
         "info": "button", "dynamicButton": "button"}
RENAMES = {("timers", "dungeons", "cooldowns"): "creation-cooldown"}
EXTRA = {
    "bang-shortcuts": ("misc", "chat-commands", "party-commands"),
    "toolkit-key": ("gui", "inventory", "item-hotkeys"),
    "hud": ("hud", "(page)", "style"),
}
EXTRA_KEYS = {
    "bang-shortcuts": ["enabled", "help", "warp", "transfer", "promote", "demote", "kick", "kickoffline", "invite",
                       "allinvite", "reinvite", "coords", "location", "holding", "fps", "time", "downtime",
                       "instances", "coinflip", "dice", "eightball", "others-prompts", "dm-invite-prompt"],
    "toolkit-key": ["key"],
    "hud": ["hidden-elements"],
}
EXTRA_KIND = {"bang-shortcuts": "toggle", "toolkit-key": "keyBindingWithDefaultUnbound", "hud": "button"}
EXTRA_LABELS = {
    "apex.timer-boards.reset-own": "Put back under Board 1",
    "apex.config.hud.hidden-elements": "Show Hidden HUD Elements",
    "apex.config.hud.hidden-elements.description":
        "Shows every HUD element hidden with the eye button in the HUD editor again. Hidden elements keep working "
        "(their feature stays on), they are just not drawn in game; the HUD editor shows them greyed out.",
}
TEMPLATE = re.compile(r"\$\{?[a-zA-Z]+\}?")


def kind_of(t):
    k = t.split(" ")[0]
    return KINDS.get(k, k)


def key_part(path):
    """The key of a tree path (main.subsection.key; the key may contain dots)."""
    return path.split(".", 2)[2]


def plan_rows(plan):
    """(main, group, subsection, configId, kind) in plan order."""
    out = []
    for tab in plan["tabs"]:
        for sec in tab["sections"]:
            group = "" if sec["id"] == "(page)" else sec["id"]
            for ss in sec["subsections"]:
                sub = RENAMES.get((tab["id"], sec["id"], ss["id"]), ss["id"])
                for o in ss["options"]:
                    if " " in o["configId"]:
                        continue
                    out.append((tab["id"], group, sub, o["configId"], kind_of(o["type"])))
                for holder, (t, s, x) in EXTRA.items():
                    if (tab["id"], sec["id"], ss["id"]) == (t, s, x):
                        out += [(t, group, sub, f"{holder}.{k}", EXTRA_KIND[holder]) for k in EXTRA_KEYS[holder]]
    return out


WARN = "\u26a0"
ALLOWED_LABELS = ["auto-completions.transfer-cooldown", "auto-completions.prevent-early-commands",
                  "kuudra-info.pile-waypoints", "kuudra-supplies.supply-waypoints", "kuudra-supplies.drop-waypoints",
                  "kuudra-supplies.render-area", "kuudra-supplies.pearl-waypoints"]


CHOICE_LABELS = {"BOARD_1": "Timer Board 1", "BOARD_2": "Timer Board 2", "INVENTORY_BOARD": "Inventory Timer Board",
                 "OWN_HUD": "Its own HUD element", "FOLLOW_MENUS": "Follow menus", "TIME_LEFT": "Time left"}


def translations(plan, path, old_subsections):
    """Labels are only added where the screen has none yet: existing tabs, pages and subsections keep their text."""
    tr = json.load(open(path))
    for tab in plan["tabs"]:
        tr.setdefault(f"apex.settings.section.{tab['id']}", tab["name"])
        tr.setdefault(f"key.category.apex.{tab['id']}", f"SkyBlock Apex > {tab['name']}")
        for sec in tab["sections"]:
            g = sec["id"]
            if g != "(page)" and f"apex.settings.group.{g}" not in tr:
                tr.setdefault(f"apex.settings.{tab['id']}.group.{g}", sec["name"])
            for ss in sec["subsections"]:
                sub = RENAMES.get((tab["id"], sec["id"], ss["id"]), ss["id"])
                if (tab["id"], sub) not in old_subsections:
                    tr.setdefault(f"apex.settings.{tab['id']}.{sub}", ss.get("name") or ss["id"])
                for o in ss["options"]:
                    if not o.get("new") or TEMPLATE.search(o["configId"]) or " " in o["configId"]:
                        continue
                    key = f"apex.config.{o['configId']}"
                    tr.setdefault(key, o["name"].replace(WARN, "").strip())
                    if o.get("note"):
                        tr.setdefault(key + ".description", o["note"])
                    for v in o.get("values") or []:
                        tr.setdefault(f"{key}.choice.{v.lower()}", CHOICE_LABELS.get(v, v.replace("_", " ").title()))
    for key, text in EXTRA_LABELS.items():
        tr.setdefault(key, text)
    for cid in ALLOWED_LABELS:
        key = f"apex.config.{cid}"
        if key in tr and tr[key].startswith(WARN):
            tr[key] = tr[key][len(WARN):].lstrip()
    open(path, "w").write(json.dumps(tr, indent="\t", ensure_ascii=False, sort_keys=True) + "\n")


LEGACY_FEEDS = {
    "sea-creature-extras.health-display": "fishing-helper.nearby-creatures-health",
    "sea-creature-extras.health-hud": "fishing-helper.nearby-creatures-hud",
    "sea-creature-extras.health-limit": "fishing-helper.nearby-creatures-limit",
}


def main(plan_path, res_dir, tr_path=None):
    plan = json.load(open(plan_path))
    rows = plan_rows(plan)
    groups = {}
    for m, g, s, cid, _ in rows:
        prev = groups.setdefault((m, s), g)
        if prev != g:
            raise SystemExit(f"subsection {m}.{s} in two groups: {prev!r} and {g!r}")
    map_path = os.path.join(res_dir, "migration-map.tsv")
    lines = open(map_path).read().split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    planned = {}
    for m, g, s, cid, k in rows:
        if TEMPLATE.search(cid):
            continue
        if cid in planned:
            raise SystemExit(f"{cid} placed twice")
        planned[cid] = (m, g, s, k)
    seen, moves, out, taken = set(), [], [], set()
    prefix_targets = {}
    for m, g, s, cid, k in rows:
        mt = TEMPLATE.search(cid)
        if mt:
            prefix_targets[cid[:mt.start()]] = (m, g, s)
    for line in lines:
        if line.startswith("#") or not line.strip():
            out.append(line)
            continue
        old, new, kind, group = (line.split("\t") + ["", "", "", ""])[:4]
        if old.endswith("*") or old.endswith("#"):
            base = old[:-1]
            tgt = prefix_targets.get(base)
            if tgt:
                m, g, s = tgt
                np = f"{m}.{s}.{key_part(new)}"
                if np != new:
                    moves.append((new, np))
                new, group = np, g
            out.append("\t".join([old, new, kind, group]))
            continue
        if old in planned:
            m, g, s, _ = planned[old]
            np = f"{m}.{s}.{key_part(new)}"
            if np in taken:
                np = f"{m}.{s}.{old}"
            taken.add(np)
            if np != new:
                moves.append((new, np))
            seen.add(old)
            out.append("\t".join([old, np, kind, g]))
        else:
            m, s = new.split(".")[0], new.split(".")[1]
            if (m, s) in groups and groups[(m, s)] != group:
                group = groups[(m, s)]
            out.append("\t".join([old, new, kind, group]))
    taken.update(l.split("\t")[1] for l in out if l and not l.startswith("#"))
    added = []
    for cid, (m, g, s, k) in planned.items():
        if cid in seen:
            continue
        np = f"{m}.{s}.{cid.split('.', 1)[1]}"
        if np in taken:
            np = f"{m}.{s}.{cid}"
        taken.add(np)
        added.append((cid, np, k, g))
    if added:
        out.append("# Added by scripts/settings_groups.py (settings regroup, 29 Oct 2026): rows the map did not have yet")
        out += ["\t".join(a) for a in sorted(added)]
    row_of = {l.split("\t")[0]: l.split("\t") for l in out if l and not l.startswith("#")}
    feeds = []
    for legacy, live in LEGACY_FEEDS.items():
        if legacy in row_of and live in row_of:
            old_path, new_path = row_of[legacy][1], row_of[live][1]
            if old_path != new_path:
                moves.append((old_path, new_path))
            feeds.append(f"{live},{legacy}\t{new_path}\tFIRST_SAVED")
    new_paths = [l.split("\t")[1] for l in out if l and not l.startswith("#")]
    dup = {p for p in new_paths if new_paths.count(p) > 1}
    if dup:
        raise SystemExit(f"duplicate new paths: {sorted(dup)[:10]}")
    open(map_path, "w").write("\n".join(out) + "\n")
    moved = dict(moves)
    for name in ("duplicates.tsv", "derived.tsv"):
        path = os.path.join(res_dir, name)
        text = open(path).read().split("\n")
        for i, line in enumerate(text):
            cols = line.split("\t")
            if line.startswith("#") or len(cols) < 2:
                continue
            cols[1] = moved.get(cols[1], cols[1])
            text[i] = "\t".join(cols)
        if name == "duplicates.tsv" and feeds:
            while text and text[-1] == "":
                text.pop()
            text.append("# Added by scripts/settings_groups.py (settings regroup): legacy D13 keys feed the live options' rows")
            text += feeds
            text.append("")
        open(path, "w").write("\n".join(text))
    with open(os.path.join(res_dir, "layout-order.tsv"), "w") as f:
        f.write("# SPDX-License-Identifier: GPL-3.0-or-later\n")
        f.write("# Generated by scripts/settings_groups.py from the approved settings regroup plan (29 Oct 2026).\n")
        f.write("# Screen order: main<TAB>group<TAB>subsection<TAB>config.key ('' group = the tab's own page).\n")
        for m, g, s, cid, _ in rows:
            f.write(f"{m}\t{g}\t{s}\t{cid}\n")
    with open(os.path.join(res_dir, "layout-moves-1006.tsv"), "w") as f:
        f.write("# SPDX-License-Identifier: GPL-3.0-or-later\n")
        f.write("# Generated by scripts/settings_groups.py. Settings tree paths moved by config version 1006: old<TAB>new\n")
        for a, b in moves:
            f.write(f"{a}\t{b}\n")
    if tr_path:
        old_subs = {tuple(l.split("\t")[1].split(".")[:2]) for l in lines if l and not l.startswith("#")}
        translations(plan, tr_path, old_subs)
    print(f"rows {len(rows)}, planned {len(planned)}, moved {len(moves)}, added {len(added)}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
