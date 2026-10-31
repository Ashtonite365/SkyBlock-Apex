# SPDX-License-Identifier: LGPL-3.0-or-later
import json, os, sys
from PIL import Image

src = sys.argv[1]
out_root = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/apex/textures/gui/sprites/storageoverlay")

def nine(img, w, h, b):
	sw, sh = img.size
	o = Image.new("RGBA", (w, h))
	cols = [(0, b, 0, b), (b, sw - b, b, w - b), (sw - b, sw, w - b, w)]
	rows = [(0, b, 0, b), (b, sh - b, b, h - b), (sh - b, sh, h - b, h)]
	for sx0, sx1, dx0, dx1 in cols:
		for sy0, sy1, dy0, dy1 in rows:
			if dx1 > dx0 and dy1 > dy0:
				o.paste(img.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
	return o

def save(style, name, img, meta=None):
	d = os.path.join(out_root, style)
	os.makedirs(d, exist_ok=True)
	img.save(os.path.join(d, name + ".png"))
	if meta:
		with open(os.path.join(d, name + ".png.mcmeta"), "w") as f:
			json.dump({"gui": {"scaling": meta}}, f, indent=4)
			f.write("\n")

for i in range(4):
	im = Image.open(os.path.join(src, f"storage_gui_{i}.png")).convert("RGBA")
	style = f"core_{i}"
	panel = im.crop((0, 0, 540, 114))
	panel.paste(im.crop((400, 194, 401, 200)).resize((528, 6), Image.NEAREST), (6, 108))
	panel.paste(im.crop((512, 194, 518, 200)), (534, 108))
	panel.paste(im.crop((534, 0, 540, 6)).transpose(Image.TRANSPOSE), (0, 108))
	panel.paste(panel.crop((518, 6, 519, 108)).resize((15, 102), Image.NEAREST), (519, 6))
	slot = im.crop((0, 265, 18, 283))
	save(style, "upper_background", nine(panel, 176, 222, 6), {"type": "nine_slice", "width": 176, "height": 222, "border": 6})
	save(style, "storage_controls", nine(panel, 91, 184, 6), {"type": "nine_slice", "width": 91, "height": 184, "border": 6})
	inv = nine(panel, 184, 91, 6)
	for r in range(3):
		for c in range(9):
			inv.paste(slot, (11 + c * 18, 8 + r * 18))
	for c in range(9):
		inv.paste(slot, (11 + c * 18, 66))
	save(style, "player_inventory", inv)
	save(style, "storage_row", im.crop((0, 265, 162, 283)), {"type": "tile", "width": 162, "height": 18})
	save(style, "scroll_bar_background", nine(im.crop((519, 7, 533, 107)), 17, 18, 2), {"type": "nine_slice", "width": 17, "height": 18, "border": 2})
	save(style, "scroll_bar_knob", im.crop((0, 250, 12, 265)))
