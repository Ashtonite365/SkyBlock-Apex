#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generates the Kenney Fast Travel map (tools/fast-travel-map/kenney_map.png, 1280x720; kept in tools, not shipped).

Original art for SkyBlock Apex: every island is a stylised top-down floating island tiled from the Kenney Voxel Pack
block textures (CC0, https://kenney.nl/assets/voxel-pack). Nothing is traced from any other map. Island shapes are
built around the portal markers read straight from FastTravelMap.kt, so every marker sits on its own island.

Usage: python3 tools/fast-travel-map/gen_fast_travel_map.py [--kenney DIR] [--out PNG] [--preview PNG]
  --kenney  the Kenney Voxel Pack PNG/Tiles dir (default assets/kenney-voxel/PNG/Tiles)
  --preview also write a copy with the markers drawn as the mod draws them (MARKER=3 boxes) plus warp ids
"""
import argparse, math, pathlib, random, re, zlib
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops, ImageEnhance

ROOT = pathlib.Path(__file__).resolve().parents[2]
KT = ROOT / "src/main/kotlin/features/menus/skyblock/FastTravelMap.kt"
W, H = 1280, 720
TILE = 16

def read_markers():
    out = []
    for m in re.finditer(r'(row|unverified|bottom)\("([a-z_]+)",\s*"([^"]+)",\s*(\d+),\s*(\d+)', KT.read_text()):
        out.append(dict(kind=m[1], id=m[2], island=m[3], x=int(m[4]), y=int(m[5])))
    return out

THEME = {
    "Hub": (["grass_top"] * 6 + ["grass1", "gravel_stone", "grass_top"], "dirt", "Hub", "village", None),
    "The Rift": (["cotton_blue"], "redstone", "The Rift", "rift", (190, 60, 255)),
    "Dungeon Hub": (["greystone", "brick_grey", "gravel_stone"], "stone", "Dungeon Hub", "graves", None),
    "Home": (["grass_top"] * 5 + ["wood"], "dirt", "Private Island", "house", None),
    "Garden": (["grass_top"] * 3 + ["wheat_stage4", "wheat_stage3"], "dirt", "Garden", "fields", None),
    "The Barn": (["grass_top"] * 2 + ["dirt", "wheat_stage4", "grass_tan"], "dirt", "The Barn", "barn", None),
    "Mushroom Desert": (["redsand"] * 4 + ["sand", "mushroom_red"], "redsand", "Mushroom Desert", "mushrooms", None),
    "The Park": (["grass_top"] * 2 + ["leaves", "grass2"], "dirt", "The Park", "trees", None),
    "Gold Mine": (["stone"] * 3 + ["stone_gold", "gravel_stone"], "stone", "Gold Mine", None, None),
    "Deep Caverns": (["stone"] * 3 + ["stone_diamond", "stone_iron", "stone_coal"], "greystone", "Deep Caverns", None, None),
    "Dwarven Mines": (["greystone"] * 3 + ["stone_silver", "stone_browniron", "gravel_stone"], "stone", "Dwarven Mines", "rails", None),
    "Crystal Hollows": (["ice"] * 2 + ["greystone_ruby", "glass", "stone_diamond"], "greystone", "Crystal Hollows", None, (170, 110, 255)),
    "Spider's Den": (["gravel_dirt"] * 2 + ["stone", "rock_moss", "rock", "gravel_stone"], "stone", "Spider's Den", "webs", None),
    "The End": (["greysand"] * 4 + ["sand"], "greysand", "The End", "pillars", (235, 230, 160)),
    "Crimson Isle": (["redstone"] * 3 + ["redsand", "redstone_sand", "lava"], "redstone", "Crimson Isle", "lava", None),
    "Jerry's Workshop": (["snow"] * 4 + ["ice"], "stone_snow", "Jerry's Workshop", "pines", None),
    "Backwater Bayou": (["grass_top", "dirt", "grass_brown", "water", "grass_top"], "dirt", "Backwater Bayou", "swamp", None),
    "Lotus Atoll": (["sand"] * 4 + ["dirt_sand"], "sand", "Lotus Atoll", "lagoon", None),
    "Galatea": (["grass_top"] * 2 + ["leaves", "leaves_orange", "grass3"], "dirt", "Galatea", "trees", None),
}
PAD = {"The Rift": 11, "Dwarven Mines": 15, "Crystal Hollows": 16, "Deep Caverns": 20, "Lotus Atoll": 22,
       "Backwater Bayou": 30, "Jerry's Workshop": 30, "Gold Mine": 24, "Dungeon Hub": 26, "The Barn": 30,
       "Home": 28, "Garden": 30}
EXTRA = {
    "Hub": [(470, 560), (560, 600), (650, 640), (720, 600), (540, 380)],
    "Mushroom Desert": [(1040, 250), (1220, 230), (1150, 150)],
    "Crimson Isle": [(440, 60), (520, 40), (700, 50), (740, 120), (560, 150)],
    "The End": [(60, 110), (130, 90), (230, 150), (140, 180)],
    "The Park": [(240, 500), (330, 560), (350, 500)],
    "Spider's Den": [(260, 300), (360, 320), (420, 290)],
    "Galatea": [(100, 330), (175, 330), (140, 270)],
    "Home": [(940, 530), (1000, 580)], "Garden": [(1080, 580), (1140, 520)],
    "The Barn": [(900, 320), (970, 360)],
}
GAP = 8

def tiles(kdir):
    cache = {}
    def get(name, tint=None):
        k = (name, tint)
        if k not in cache:
            im = Image.open(kdir / f"{name}.png").convert("RGBA").resize((TILE, TILE), Image.BOX)
            if tint:
                g = im.convert("L").convert("RGBA")
                im = ImageChops.multiply(ImageEnhance.Brightness(g).enhance(1.25), Image.new("RGBA", im.size, tint + (255,)))
            cache[k] = im
        return cache[k]
    return get

def hull(pts):
    pts = sorted(set(pts))
    if len(pts) <= 2: return pts
    def cross(o, a, b): return (a[0]-o[0])*(b[1]-o[1]) - (a[1]-o[1])*(b[0]-o[0])
    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0: lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0: up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]

def island_mask(pts, pad, rng):
    m = Image.new("L", (W, H), 0); d = ImageDraw.Draw(m)
    h = hull(pts)
    if len(h) >= 3: d.polygon(h, fill=255)
    elif len(h) == 2: d.line(h, fill=255, width=2)
    for x, y in pts: d.ellipse([x-2, y-2, x+2, y+2], fill=255)
    m = m.filter(ImageFilter.MaxFilter(3))
    m = m.filter(ImageFilter.GaussianBlur(pad * 0.6)).point(lambda v: 255 if v > 18 else 0)
    r = pad
    big = Image.new("L", (W, H), 0); bd = ImageDraw.Draw(big)
    for x, y in pts: bd.ellipse([x-r, y-r, x+r, y+r], fill=255)
    m = ImageChops.lighter(m, big)
    noise = Image.new("L", (W // 8, H // 8)); noise.putdata([max(0, min(255, int(rng.gauss(128, 60)))) for _ in range(noise.width * noise.height)])
    noise = noise.resize((W, H), Image.BICUBIC)
    edge = m.filter(ImageFilter.GaussianBlur(4))
    m = ImageChops.add(edge, ImageChops.subtract(noise, Image.new("L", (W, H), 128)), 1, 0).point(lambda v: 255 if v > 140 else 0)
    return m

def dilate(m, n): return m.filter(ImageFilter.GaussianBlur(n * 0.5)).point(lambda v: 255 if v > 2 else 0) if n else m

def build(kdir, out, preview):
    rng = random.Random(1031)
    tex = tiles(kdir)
    markers = read_markers()
    islands = {}
    for mk in markers: islands.setdefault(mk["island"], []).append((mk["x"], mk["y"]))
    assert set(islands) <= set(THEME), set(islands) - set(THEME)

    masks = {}
    for name, pts in islands.items():
        masks[name] = island_mask(pts + EXTRA.get(name, []), PAD.get(name, 22), rng)
    order = sorted(masks, key=lambda n: sum(masks[n].histogram()[255:]))
    for i, small in enumerate(order):
        hole = dilate(masks[small], GAP)
        for big in order[i + 1:]: masks[big] = ImageChops.subtract(masks[big], hole)
    for mk in markers:
        mm = masks[mk["island"]]
        for dx in range(-4, 5):
            for dy in range(-4, 5):
                assert mm.getpixel((mk["x"] + dx, mk["y"] + dy)) == 255, f"marker {mk['id']} not on {mk['island']}"
        for other, om in masks.items():
            if other != mk["island"]: assert om.getpixel((mk["x"], mk["y"])) == 0, f"{mk['id']} also on {other}"

    bg = Image.new("RGBA", (W, H))
    px = bg.load()
    for y in range(H):
        t = y / H
        c = (int(40 + 30 * (1 - t)), int(70 + 60 * (1 - t)), int(120 + 70 * (1 - t)))
        for x in range(W): px[x, y] = c + (255,)
    d = ImageDraw.Draw(bg)
    for _ in range(260):
        x, y = rng.randrange(W), rng.randrange(H); b = rng.randrange(150, 255)
        d.point((x, y), fill=(b, b, 255, 255))
    clouds = Image.new("L", (W, H), 0); cd = ImageDraw.Draw(clouds)
    for _ in range(40):
        x, y = rng.randrange(W), rng.randrange(H); rw, rh = rng.randrange(40, 140), rng.randrange(10, 28)
        cd.ellipse([x - rw, y - rh, x + rw, y + rh], fill=rng.randrange(25, 55))
    clouds = clouds.filter(ImageFilter.GaussianBlur(14))
    bg = Image.composite(Image.new("RGBA", (W, H), (220, 230, 255, 255)), bg, clouds)

    def tiled(names, tint, seed):
        r = random.Random(seed); im = Image.new("RGBA", (W, H))
        for ty in range(0, H, TILE):
            for tx in range(0, W, TILE):
                im.paste(tex(names[0], tint), (tx, ty))
                im.alpha_composite(tex(r.choice(names), tint), (tx, ty))
        return im

    for name in sorted(masks, key=lambda n: -sum(masks[n].histogram()[255:])):
        tops, cliff, label, deco, tint = THEME[name]
        m = masks[name]
        shadow = m.filter(ImageFilter.GaussianBlur(6)).point(lambda v: v * 0.5)
        bg.paste((10, 15, 35, 255), (5, 16), shadow)
        depth = 9
        side = Image.new("L", (W, H), 0)
        for k in range(1, depth + 1): side = ImageChops.lighter(side, ImageChops.offset(m, 0, k))
        side_tex = ImageEnhance.Brightness(tiled([cliff], None, zlib.crc32(name.encode()))).enhance(0.6)
        bg.paste(side_tex, (0, 0), side)
        top = tiled(tops, tint, zlib.crc32(name.encode()) + 7)
        bg.paste(top, (0, 0), m)
        rim = ImageChops.subtract(m, m.filter(ImageFilter.MinFilter(3)))
        bg.paste((25, 25, 30, 255), (0, 0), rim.point(lambda v: v * 0.7))
        decorate(bg, m, deco, islands[name], tex, rng)
    labels(bg, masks, markers)
    bg.convert("RGB").save(out)
    if preview:
        pv = bg.copy(); d = ImageDraw.Draw(pv); f = font(9)
        for mk in markers:
            x, y, s = mk["x"], mk["y"], 3
            locked = mk["kind"] == "unverified"
            d.rectangle([x - s - 1, y - s - 1, x + s, y + s], fill=(0x2A, 0x2A, 0x2A) if locked else (0x3A, 0, 0x60))
            d.rectangle([x - s, y - s, x + s - 1, y + s - 1], fill=(0x55, 0x44, 0x66) if locked else (0xAA, 0x33, 0xFF))
            d.text((x + 6, y - 5), mk["id"], font=f, fill=(255, 255, 160), stroke_width=1, stroke_fill=(0, 0, 0))
        pv.convert("RGB").save(preview)

def font(sz):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]:
        try: return ImageFont.truetype(p, sz)
        except OSError: pass
    return ImageFont.load_default()

def labels(bg, masks, markers):
    """Island names (the menu draws no labels of its own): below the island, else above, never over a marker."""
    d = ImageDraw.Draw(bg); f = font(11); placed = []
    keep_out = [(mk["x"] - 6, mk["y"] - 6, mk["x"] + 6, mk["y"] + 6) for mk in markers]
    def hit(a, b): return not (a[2] < b[0] or b[2] < a[0] or a[3] < b[1] or b[3] < a[1])
    for name, m in masks.items():
        bx = m.getbbox(); label = THEME[name][2]
        tw = d.textlength(label, font=f); cx = (bx[0] + bx[2]) / 2
        for y, dxs in [(bx[3] + 11, [0, -30, 30, -60, 60]), (bx[1] - 15, [0, -30, 30]), ((bx[1] + bx[3]) / 2 - 6, [(bx[2] - bx[0] + tw) / 2 + 8, -(bx[2] - bx[0] + tw) / 2 - 8]), (bx[3] - 2, [0])]:
            for dx in dxs:
                r = (cx + dx - tw / 2 - 2, y - 1, cx + dx + tw / 2 + 2, y + 13)
                if r[0] < 2 or r[2] > W - 2 or r[1] < 2 or r[3] > H - 2: continue
                if any(hit(r, k) for k in keep_out + placed + [masks[o].getbbox() for o in masks if o != name]): continue
                placed.append(r)
                d.text((r[0] + 2, y), label, font=f, fill=(255, 255, 255), stroke_width=2, stroke_fill=(20, 20, 40))
                break
            else: continue
            break
        else: raise SystemExit(f"no label spot for {name}")

def decorate(bg, m, deco, pts, tex, rng):
    if not deco: return
    d = ImageDraw.Draw(bg)
    bx = m.getbbox()
    def free(x, y, r=9):
        if not (0 <= x < W and 0 <= y < H) or m.getpixel((int(x), int(y))) != 255: return False
        if any(m.getpixel((int(min(W-1, max(0, x + a))), int(min(H-1, max(0, y + b))))) != 255 for a in (-6, 6) for b in (-6, 6)): return False
        return all(abs(x - px) > r + 4 or abs(y - py) > r + 4 for px, py in pts)
    def spots(n):
        out = []
        for _ in range(n * 40):
            x, y = rng.randrange(bx[0], bx[2]), rng.randrange(bx[1], bx[3])
            if free(x, y) and all(abs(x - a) > 10 or abs(y - b) > 10 for a, b in out): out.append((x, y))
            if len(out) >= n: break
        return out
    def block(name, x, y, s=10, tint=None):
        im = tex(name, tint).resize((s, s), Image.NEAREST); bg.paste(im, (int(x - s / 2), int(y - s / 2)), im)
    area = sum(m.histogram()[255:]) // 900
    if deco in ("trees", "village", "pines", "swamp"):
        for x, y in spots(max(3, area // (2 if deco == "trees" else 4))):
            leaf = {"pines": "leaves", "swamp": "leaves_orange"}.get(deco, rng.choice(["leaves", "leaves", "leaves_orange"]))
            d.ellipse([x - 6, y - 4, x + 6, y + 8], fill=(15, 30, 15, 120)); block(leaf, x, y, 11)
            if deco == "pines": d.ellipse([x - 3, y - 3, x + 3, y + 1], fill=(240, 245, 255))
    if deco in ("village", "house", "barn"):
        for x, y in spots({"village": 8, "house": 2, "barn": 2}[deco]):
            block("wood_red" if deco == "barn" else rng.choice(["brick_red", "wood", "brick_grey"]), x, y, 12)
            d.rectangle([x - 6, y - 6, x + 5, y + 5], outline=(40, 25, 15))
    if deco == "fields":
        for x, y in spots(4): block("wheat_stage4", x, y, 12)
    if deco == "mushrooms":
        for x, y in spots(6): block(rng.choice(["mushroom_red", "mushroom_brown"]), x, y, 11)
    if deco == "graves":
        for x, y in spots(5): d.rectangle([x - 2, y - 4, x + 2, y + 3], fill=(90, 90, 100), outline=(30, 30, 35))
    if deco == "webs":
        for x, y in spots(6):
            for a in range(0, 360, 45):
                d.line([x, y, x + 6 * math.cos(math.radians(a)), y + 6 * math.sin(math.radians(a))], fill=(230, 230, 230), width=1)
            d.ellipse([x - 3, y - 3, x + 3, y + 3], outline=(230, 230, 230))
    if deco == "pillars":
        for x, y in spots(5): d.rectangle([x - 3, y - 3, x + 3, y + 3], fill=(25, 10, 40), outline=(120, 60, 160))
    if deco == "lava":
        for x, y in spots(8): block("lava", x, y, 14)
    if deco == "rails":
        for x, y in spots(3): block("track_straight", x, y, 12)
    if deco in ("swamp", "lagoon"):
        for x, y in spots(5 if deco == "swamp" else 3): block("water", x, y, 14)
    if deco == "rift":
        cx, cy = pts[0]
        d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], outline=(255, 180, 255), width=1)

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--kenney", default="assets/kenney-voxel/PNG/Tiles")
    ap.add_argument("--out", default=str(ROOT / "tools/fast-travel-map/kenney_map.png"))
    ap.add_argument("--preview")
    a = ap.parse_args()
    build(pathlib.Path(a.kenney), a.out, a.preview)
