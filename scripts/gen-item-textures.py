#!/usr/bin/env python3
"""Generate the SkyBlock Apex item texture patch dataset (for the SkyBlockApex-REPO bundle).

Per SkyBlock item id it emits:
  item_model    -> "hypixel_skyblock:item/<path>" (only if the Hypixel pack defines it)
  skull_texture -> base64 textures Value for player-head items (from Hypixel's public API)
  base_material -> real minecraft item id for items the bundle has as minecraft:barrier
  enchanted     -> true when Hypixel marks the item glowing (bundle lacks ench:[])
  model_source  -> "api" | "pack_filename"

Inputs are local-only: the Hypixel server pack is NEVER copied into the output, the
mod jar, or any public repo. Only identifiers and Mojang skin texture values are emitted.
The mod itself never calls the API; this script runs on a dev box.

usage: gen-item-textures.py --pack DIR_OR_ZIP --bundle repo.json.zst --out DIR [--api-json FILE]
"""
import argparse, io, json, os, re, sys, urllib.request, zipfile

API = "https://api.hypixel.net/v2/resources/skyblock/items"

def load_pack_models(pack):
    """Return set of model ids defined under assets/hypixel_skyblock/items/**.json."""
    pre = "assets/hypixel_skyblock/items/"
    names = []
    if os.path.isdir(pack):
        root = os.path.join(pack, pre)
        for dp, _, fs in os.walk(root):
            for f in fs:
                if f.endswith(".json"):
                    names.append(os.path.relpath(os.path.join(dp, f), root).replace(os.sep, "/"))
    else:
        with zipfile.ZipFile(pack) as z:
            names = [n[len(pre):] for n in z.namelist() if n.startswith(pre) and n.endswith(".json")]
    return {"hypixel_skyblock:" + n[:-5] for n in names}

def load_bundle(path):
    import zstandard
    with open(path, "rb") as f:
        raw = zstandard.ZstdDecompressor().stream_reader(f).read()
    files = json.loads(raw)["files"]
    return {k[6:-5]: json.loads(v) for k, v in files.items()
            if k.startswith("items/") and k.endswith(".json")}

def norm_material(m):
    m = m.lower()
    legacy = {"skull_item": "player_head", "wood_sword": "wooden_sword", "gold_sword": "golden_sword",
              "ink_sack": "ink_sac", "watch": "clock", "wool": "white_wool", "stained_clay": "terracotta"}
    return "minecraft:" + legacy.get(m, m)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", required=True)
    ap.add_argument("--bundle", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--api-json")
    a = ap.parse_args()
    models = load_pack_models(a.pack)
    by_base = {}
    for m in models:
        by_base.setdefault(m.rsplit("/", 1)[-1], []).append(m)
    if a.api_json:
        api = json.load(open(a.api_json))
    else:
        api = json.load(urllib.request.urlopen(urllib.request.Request(API, headers={"User-Agent": "apex-datagen"})))
    api = {i["id"]: i for i in api["items"]}
    bundle = load_bundle(a.bundle)

    out, stats = {}, {"bundle_items": len(bundle), "pack_models": len(models)}
    for sid, item in sorted(bundle.items()):
        e, ai = {}, api.get(sid, {})
        im = ai.get("item_model")
        if im and im in models:
            e["item_model"], e["model_source"] = im, "api"
        else:
            cands = by_base.get(sid.lower(), [])
            if len(cands) == 1:
                e["item_model"], e["model_source"] = cands[0], "pack_filename"
        nbt = item.get("nbttag", "")
        skin = (ai.get("skin") or {}).get("value")
        if skin and "SkullOwner" not in nbt:
            e["skull_texture"] = skin
        if item.get("itemid") == "minecraft:barrier" and ai.get("material"):
            e["base_material"] = norm_material(ai["material"])
            if ai.get("durability") is not None:
                e["damage"] = ai["durability"]
        if ai.get("glowing") and not re.search(r"\bench:", nbt):
            e["enchanted"] = True
        if e:
            out[sid] = e
    for k in ("item_model", "skull_texture", "base_material", "enchanted"):
        stats[k] = sum(k in v for v in out.values())
    os.makedirs(a.out, exist_ok=True)
    json.dump({"format": "apex-item-texture-patch", "version": 1, "stats": stats, "items": out},
              open(os.path.join(a.out, "item-texture-patch.json"), "w"), indent=1, sort_keys=True)
    print(json.dumps(stats))

if __name__ == "__main__":
    main()
