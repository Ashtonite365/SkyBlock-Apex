#!/usr/bin/env python3
# SPDX-License-Identifier: CC-BY-4.0
# SPDX-FileCopyrightText: 2026 Ashtonite365
"""Redraws the Lotus Atoll region of fast_travel_map.png with an original, procedurally drawn lotus island.

Clean-room: the old map is read only to (a) take the background colour sampled OUTSIDE the region and
(b) keep every pixel outside REGION untouched. No pixel inside REGION is read or kept; the region is first
filled with the sea colour and everything inside is drawn from the shapes below.
Usage: python3 gen_atoll_patch.py <in.png> <out.png>
"""
import math, random, sys
from PIL import Image, ImageDraw

REGION = (130, 643, 212, 720)
SEED = 1031
OUTLINE = (12, 12, 16)
GRASS = [(74, 128, 52), (88, 146, 60), (60, 108, 44), (102, 160, 70)]
STONE = [(196, 196, 186), (172, 172, 164), (150, 150, 144), (214, 212, 200)]
SHADE = (112, 112, 108)
WATER = [(70, 160, 190), (88, 182, 206)]
PAD = (52, 120, 60)
PETAL = [(240, 150, 196), (250, 186, 216), (214, 110, 168)]
CORE = (250, 220, 90)


def island(d, rnd, cx, cy, rx, ry, depth, lagoon=True, lotus=True):
    for k in range(depth, -1, -1):
        f = 1 - 0.55 * (k / depth) ** 1.3
        jit = rnd.randint(-1, 1)
        box = (cx - rx * f + jit, cy + k - ry * f, cx + rx * f + jit, cy + k + ry * f)
        col = SHADE if k == depth else STONE[rnd.randrange(len(STONE))]
        d.ellipse(box, fill=col)
        if 0 < k < depth:
            for _ in range(int(rx * f * 0.6)):
                sx = cx + (rnd.random() * 2 - 1) * rx * f * 0.95
                sy = cy + k + ry * f * (0.55 + 0.4 * rnd.random())
                d.rectangle((sx, sy, sx + rnd.randint(0, 1), sy + rnd.randint(0, 2)), fill=STONE[rnd.randrange(len(STONE))] if rnd.random() < .6 else SHADE)
    d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=GRASS[0])
    for _ in range(int(rx * ry * 0.9)):
        a, r = rnd.random() * 2 * math.pi, math.sqrt(rnd.random())
        x, y = cx + math.cos(a) * r * (rx - 1), cy + math.sin(a) * r * (ry - 1)
        d.point((x, y), fill=GRASS[rnd.randrange(len(GRASS))])
    if lagoon:
        lx, ly = rx * 0.55, ry * 0.5
        d.ellipse((cx - lx, cy - ly, cx + lx, cy + ly), fill=WATER[0])
        for _ in range(int(lx * ly)):
            a, r = rnd.random() * 2 * math.pi, math.sqrt(rnd.random())
            d.point((cx + math.cos(a) * r * lx, cy + math.sin(a) * r * ly), fill=WATER[rnd.randrange(2)])
        for _ in range(6):
            a, r = rnd.random() * 2 * math.pi, 0.4 + 0.5 * rnd.random()
            px, py = cx + math.cos(a) * r * (lx - 2), cy + math.sin(a) * r * (ly - 1)
            d.ellipse((px - 1.5, py - 1, px + 1.5, py + 1), fill=PAD)
    if lotus:
        flower(d, cx, cy - 1, 1.0)


def flower(d, cx, cy, s):
    for i in range(8):
        a = i * math.pi / 4
        px, py = cx + math.cos(a) * 3.2 * s, cy + math.sin(a) * 2.0 * s
        d.ellipse((px - 2 * s, py - 1.4 * s, px + 2 * s, py + 1.4 * s), fill=PETAL[i % 3], outline=None)
    d.ellipse((cx - 1.6 * s, cy - 1.2 * s, cx + 1.6 * s, cy + 1.2 * s), fill=PETAL[1])
    d.rectangle((cx - 0.5, cy - 0.5, cx + 0.5, cy + 0.5), fill=CORE)


def outline(img, bg, box):
    x0, y0, x1, y1 = box
    px = img.load()
    src = img.copy().load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if src[x, y] == bg and any(
                    x0 <= x + dx < x1 and y0 <= y + dy < y1 and src[x + dx, y + dy] not in (bg, OUTLINE)
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUTLINE


def main(src, dst):
    im = Image.open(src).convert("RGB")
    x0, y0, x1, y1 = REGION
    bg = im.getpixel((x0 - 6, y1 - 4))
    patch = Image.new("RGB", (x1 - x0, y1 - y0), bg)
    d = ImageDraw.Draw(patch)
    rnd = random.Random(SEED)
    ox, oy = -x0, -y0
    island(d, rnd, 172 + ox, 681 + oy, 24, 12, 20)
    island(d, rnd, 145 + ox, 659 + oy, 6, 3.5, 6, lagoon=False)
    island(d, rnd, 199 + ox, 662 + oy, 5, 3, 5, lagoon=False, lotus=False)
    flower(d, 199 + ox, 661 + oy, 0.6)
    for (fx, fy, s) in ((158, 677, 0.55), (186, 690, 0.6), (164, 692, 0.5)):
        flower(d, fx + ox, fy + oy, s)
    outline(patch, bg, (0, 0, x1 - x0, y1 - y0))
    im.paste(patch, (x0, y0))
    px = im.load()
    for x in range(x0, x1):
        for y in (y0, y1 - 1):
            px[x, y] = bg
    for y in range(y0, y1):
        for x in (x0, x1 - 1):
            px[x, y] = bg
    im.save(dst)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
