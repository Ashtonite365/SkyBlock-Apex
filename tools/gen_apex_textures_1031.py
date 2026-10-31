#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
import os, random, sys, math
from PIL import Image, ImageDraw

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
SPR = 'assets/apex/textures/gui/sprites/'
GUI = 'assets/apex/textures/gui/'

SG_GLASS = (22, 32, 44)
SG_RIM = (255, 255, 255)
SG_ACC = (124, 196, 255)
SG_ACC2 = (140, 245, 210)
FG_BODY = (26, 20, 16)
FG_CARD = (36, 27, 21)
FG_EDGE = (10, 7, 5)
FG_RIM = (90, 65, 48)
FG_ORANGE = (255, 138, 61)
FG_GOLD = (255, 200, 87)


def img(w, h): return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)


def blend(im, x, y, c):
    if not (0 <= x < im.width and 0 <= y < im.height): return
    r, g, b, a = c
    br, bg, bb, ba = im.getpixel((x, y))
    fa = a / 255
    oa = fa + ba / 255 * (1 - fa)
    if oa == 0: return
    m = lambda s, d: int((s * fa + d * ba / 255 * (1 - fa)) / oa)
    im.putpixel((x, y), (m(r, br), m(g, bg), m(b, bb), int(oa * 255)))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1): blend(im, x, y, c)


def outline(im, x0, y0, x1, y1, c, cut=0):
    for x in range(x0 + cut, x1 - cut):
        blend(im, x, y0, c); blend(im, x, y1 - 1, c)
    for y in range(y0 + cut, y1 - cut):
        blend(im, x0, y, c); blend(im, x1 - 1, y, c)


def rounded_mask(w, h, r):
    def inside(x, y):
        cx = min(max(x, r), w - 1 - r); cy = min(max(y, r), h - 1 - r)
        return (x - cx) ** 2 + (y - cy) ** 2 <= r * r + r * 0.6
    return inside


def sg_panel(w, h, r=4, alpha=150, seed=1, accent_top=True):
    """Frosted translucent glass: smoky body, soft frost grain, white rim, light blue top accent."""
    im = img(w, h); rnd = random.Random(seed)
    inside = rounded_mask(w, h, r)
    for y in range(h):
        for x in range(w):
            if not inside(x, y): continue
            t = y / max(1, h - 1)
            frost = rnd.randint(-6, 6)
            lift = int(18 * (1 - t))
            c = tuple(max(0, min(255, v + lift + frost)) for v in SG_GLASS)
            im.putpixel((x, y), (*c, alpha))
    for y in range(h):
        for x in range(w):
            if not inside(x, y): continue
            edge = any(not (0 <= x + dx < w and 0 <= y + dy < h and inside(x + dx, y + dy)) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                a = 120 if (y < h / 2 and x < w / 2) else 70
                blend(im, x, y, (*SG_RIM, a))
    if accent_top and w > 2 * r + 4:
        for x in range(r + 2, w - r - 2):
            blend(im, x, 1, (*SG_ACC, 150))
    return im


def sg_slot(im, x, y, size=18, alpha=70):
    rect(im, x + 1, y + 1, x + size - 1, y + size - 1, (0, 0, 0, alpha))
    outline(im, x, y, x + size, y + size, (*SG_RIM, 46), cut=1)
    for i in range(1, size - 1): blend(im, x + i, y + size - 1, (*SG_ACC, 40))


def fg_panel(w, h, seed=7, poke=3, sparks=True, light_rim=True):
    """Forged dark metal: brushed/hammered body inset by [poke], riveted corner tabs poking out, orange sparks."""
    im = img(w, h); rnd = random.Random(seed)
    x0, y0, x1, y1 = poke, poke, w - poke, h - poke
    for y in range(y0, y1):
        streak = rnd.randint(-3, 3)
        for x in range(x0, x1):
            n = rnd.randint(-4, 4) + streak
            d = min(x - x0, x1 - 1 - x, y - y0, y1 - 1 - y)
            dark = max(0, 6 - d) * 3
            heat = max(0, 4 - d) * 6 if d >= 1 else 0
            c = (FG_BODY[0] + n - dark + heat, FG_BODY[1] + n - dark + heat // 3, FG_BODY[2] + n - dark)
            im.putpixel((x, y), (*[max(0, min(255, v)) for v in c], 255))
    outline(im, x0, y0, x1, y1, (*FG_EDGE, 255))
    if light_rim:
        for x in range(x0 + 1, x1 - 1): blend(im, x, y0 + 1, (*FG_RIM, 200))
        for y in range(y0 + 1, y1 - 1): blend(im, x0 + 1, y, (*FG_RIM, 140))
    for _ in range(max(1, w * h // 900)):
        cx, cy = rnd.randint(x0 + 6, max(x0 + 6, x1 - 7)), rnd.randint(y0 + 6, max(y0 + 6, y1 - 7))
        blend(im, cx, cy, (0, 0, 0, 50)); blend(im, cx + 1, cy, (255, 255, 255, 18))
    if sparks and w > 24 and h > 24:
        for _ in range(max(3, min(40, w * h // 1800))):
            sx, sy = rnd.randint(x0 + 6, x1 - 7), rnd.randint(y0 + 6, y1 - 7)
            a = rnd.randint(0x40, 0x80); arm = rnd.randint(0, 1)
            col = FG_ORANGE if rnd.random() < 0.75 else FG_GOLD
            blend(im, sx, sy, (*col, min(255, a + 60)))
            for k in range(1, arm + 2):
                for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)): blend(im, sx + dx, sy + dy, (*col, a // (k + 1)))
    t = 7
    for cx, cy in ((0, 0), (w - t, 0), (0, h - t), (w - t, h - t)):
        rect(im, cx, cy, cx + t, cy + t, (58, 44, 34, 255))
        outline(im, cx, cy, cx + t, cy + t, (*FG_EDGE, 255))
        for i in range(1, t - 1): blend(im, cx + i, cy + 1, (120, 92, 70, 255))
        put(im, cx + 3, cy + 3, (200, 150, 100, 255)); put(im, cx + 4, cy + 4, (30, 22, 16, 255))
    return im


def fg_slot(im, x, y, size=18):
    rect(im, x + 1, y + 1, x + size - 1, y + size - 1, (14, 10, 8, 255))
    for i in range(size):
        blend(im, x + i, y, (8, 5, 4, 255)); blend(im, x, y + i, (8, 5, 4, 255))
        blend(im, x + i, y + size - 1, (92, 66, 46, 255)); blend(im, x + size - 1, y + i, (92, 66, 46, 255))


def storage_set(style):
    S = sg_panel if style == 'skyglass' else (lambda w, h, **k: fg_panel(w, h, seed=k.get('seed', 3)))
    slot = sg_slot if style == 'skyglass' else fg_slot
    out = {}
    out['upper_background'] = S(176, 222, seed=11)
    out['storage_controls'] = S(91, 184, seed=12)
    pi = S(184, 91, seed=13)
    for r in range(3):
        for c in range(9): slot(pi, 11 + c * 18, 8 + r * 18)
    for c in range(9): slot(pi, 11 + c * 18, 66)
    out['player_inventory'] = pi
    row = img(162, 18)
    for c in range(9): slot(row, c * 18, 0)
    out['storage_row'] = row
    if style == 'skyglass':
        sb = sg_panel(17, 18, r=2, alpha=90, seed=14, accent_top=False)
        kn = img(8, 16); rect(kn, 1, 0, 7, 16, (*SG_ACC, 200)); outline(kn, 1, 0, 7, 16, (*SG_RIM, 160), cut=1)
        for y in (6, 8, 10): rect(kn, 3, y, 5, y + 1, (255, 255, 255, 170))
    else:
        sb = img(17, 18); rect(sb, 0, 0, 17, 18, (14, 10, 8, 255)); outline(sb, 0, 0, 17, 18, (60, 44, 32, 255))
        kn = img(8, 16); rect(kn, 0, 0, 8, 16, (70, 52, 38, 255)); outline(kn, 0, 0, 8, 16, (*FG_EDGE, 255))
        for y in (5, 8, 11): rect(kn, 2, y, 6, y + 1, (*FG_ORANGE, 220))
    out['scroll_bar_background'] = sb; out['scroll_bar_knob'] = kn
    return out


def inventory(style):
    im = img(256, 256)
    body = sg_panel(176, 166, r=4, alpha=170, seed=21) if style == 'skyglass' else fg_panel(176, 166, seed=22, poke=0)
    im.alpha_composite(body, (0, 0))
    slot = sg_slot if style == 'skyglass' else fg_slot
    for i in range(4): slot(im, 7, 7 + i * 18)
    slot(im, 76, 61)
    for r in range(2):
        for c in range(2): slot(im, 97 + c * 18, 17 + r * 18)
    if style == 'skyglass':
        rect(im, 148, 22, 174, 48, (0, 0, 0, 60)); outline(im, 148, 22, 174, 48, (*SG_ACC, 140), cut=1)
    else:
        rect(im, 148, 22, 174, 48, (14, 10, 8, 255)); outline(im, 148, 22, 174, 48, (*FG_ORANGE, 200))
    ac = SG_ACC if style == 'skyglass' else FG_ORANGE
    for x in range(135, 146): blend(im, x, 36, (*ac, 200))
    for k in range(4): blend(im, 145 - k, 36 - k, (*ac, 200)); blend(im, 145 - k, 36 + k, (*ac, 200))
    for r in range(3):
        for c in range(9): slot(im, 7 + c * 18, 83 + r * 18)
    for c in range(9): slot(im, 7 + c * 18, 141)
    if style == 'skyglass':
        rect(im, 26, 8, 75, 78, (0, 0, 0, 80)); outline(im, 25, 7, 76, 79, (*SG_RIM, 60), cut=1)
    else:
        rect(im, 26, 8, 75, 78, (8, 6, 4, 255)); outline(im, 25, 7, 76, 79, (92, 66, 46, 255))
    return im


def small_sprites():
    o = {}
    o[SPR + 'inventory_button_background.png'] = sg_panel(18, 18, r=3, alpha=150, seed=31)
    o[SPR + 'generic_vanilla_widget.png'] = sg_panel(24, 41, r=4, alpha=170, seed=32)
    def badge(colour, seed, shape):
        im = img(16, 16)
        rect(im, 1, 1, 15, 15, (*colour, 70)); outline(im, 0, 0, 16, 16, (*colour, 200), cut=1)
        if shape == 'diag':
            for i in range(2, 14, 3): blend(im, i, 2, (*colour, 120)); blend(im, 2, i, (*colour, 120))
        return im
    o[SPR + 'completed_commission_background.png'] = badge((90, 230, 140), 1, 'diag')
    o[SPR + 'selected_pet_background.png'] = badge(SG_ACC, 2, 'diag')
    o[SPR + 'hotm_perk_preset.png'] = badge(SG_ACC2, 3, 'plain')
    def lock(colour, uuid):
        im = img(16, 16)
        for y in range(2, 7): put(im, 5, y, (*colour, 255)); put(im, 10, y, (*colour, 255))
        for x in range(6, 10): put(im, x, 1, (*colour, 255))
        rect(im, 3, 7, 13, 14, (*colour, 255)); outline(im, 3, 7, 13, 14, (20, 30, 40, 255))
        if uuid:
            for x in (5, 7, 9): put(im, x, 10, (20, 30, 40, 255)); put(im, x + 1, 11, (20, 30, 40, 255))
        else:
            put(im, 7, 9, (20, 30, 40, 255)); put(im, 8, 9, (20, 30, 40, 255)); put(im, 7, 10, (20, 30, 40, 255)); put(im, 8, 11, (20, 30, 40, 255))
        return im
    o[SPR + 'slot_locked.png'] = lock((206, 226, 246), False)
    o[SPR + 'uuid_locked.png'] = lock(SG_ACC, True)
    def checkbox(on):
        im = img(16, 16); rect(im, 1, 1, 15, 15, (22, 32, 44, 200)); outline(im, 0, 0, 16, 16, (255, 255, 255, 150), cut=1)
        if on:
            rect(im, 2, 2, 14, 14, (*SG_ACC, 210))
            for i, (x, y) in enumerate([(4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)]):
                put(im, x, y, (11, 34, 56, 255)); put(im, x, y + 1, (11, 34, 56, 255))
        return im
    o[SPR + 'widget/checkbox_checked.png'] = checkbox(True)
    o[SPR + 'widget/checkbox_unchecked.png'] = checkbox(False)
    ba = img(200, 20); rect(ba, 1, 1, 199, 19, (*SG_ACC, 205)); outline(ba, 0, 0, 200, 20, (11, 34, 56, 255), cut=1)
    for x in range(2, 198): blend(ba, x, 1, (255, 255, 255, 120)); blend(ba, x, 18, (0, 0, 0, 60))
    o[SPR + 'widget/button_active.png'] = ba
    bar = img(64, 64)
    for x in range(0, 12):
        for y in range(1, 7):
            edge = (x == 0 or x == 11) and y in (1, 6)
            if edge: continue
            v = 255 if y in (2, 3) else 220
            if x in (0, 11) or y in (1, 6): v = 170
            put(bar, x, y, (v, v, v, 255))
    for y in range(1, 7): put(bar, 13, y, (0, 0, 0, 110))
    o[GUI + 'bar.png'] = bar
    c = img(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if d <= 15.5: put(c, x, y, (255, 255, 255, int(255 * max(0.0, min(1.0, 15.5 - d + 0.5)))))
    o[GUI + 'circle.png'] = c
    def waypoint(selected):
        im = img(16, 16); col = SG_ACC if selected else (180, 190, 200)
        for y in range(16):
            for x in range(16):
                if abs(x - 7.5) + abs(y - 7.5) <= 6.5: put(im, x, y, (*col, 230 if selected else 150))
        if selected:
            for y in range(16):
                for x in range(16):
                    if 6.5 < abs(x - 7.5) + abs(y - 7.5) <= 7.5: put(im, x, y, (255, 255, 255, 255))
        put(im, 7, 7, (255, 255, 255, 255)); put(im, 8, 8, (255, 255, 255, 255))
        return im
    o[GUI + 'waypoint_selected.png'] = waypoint(True)
    o[GUI + 'waypoint_unselected.png'] = waypoint(False)
    return o


def all_outputs():
    o = {}
    for name, im in storage_set('skyglass').items(): o[SPR + 'storageoverlay/' + name + '.png'] = im
    for name, im in storage_set('forge').items(): o[SPR + 'storageoverlay/forge/' + name + '.png'] = im
    o[GUI + 'apex/inventory_skyglass.png'] = inventory('skyglass')
    o[GUI + 'apex/inventory_forge.png'] = inventory('forge')
    o.update(small_sprites())
    return o


if __name__ == '__main__':
    for rel, im in all_outputs().items():
        p = os.path.join(ROOT, rel); os.makedirs(os.path.dirname(p), exist_ok=True); im.save(p)
        print(rel, im.size)
