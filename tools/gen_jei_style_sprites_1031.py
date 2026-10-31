#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
import json, os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/apex/textures/gui/sprites/jei")
S, B = 16, 4
PAL = {
    "skyglass":    dict(frame=(225,240,255,150), fill=(205,225,245,56), raised=(235,245,255,84), accent=(150,200,240,210), slot=(20,32,48,64), dark=(200,225,250,110)),
    "forge_dark":  dict(frame=(20,21,24,255), fill=(42,44,48,245), raised=(58,61,66,255), accent=(232,134,42,255), slot=(30,31,34,255), dark=(12,12,14,255)),
    "forge_light": dict(frame=(110,114,120,255), fill=(201,204,209,245), raised=(226,228,232,255), accent=(217,118,28,255), slot=(176,180,186,255), dark=(90,94,100,255)),
}
KIND = {
    "gui_background": ("fill", "frame", True), "ingredient_list_background": ("fill", "frame", True),
    "bookmark_list_background": ("fill", "frame", True), "search_background": ("slot", "dark", False),
    "button_enabled": ("raised", "frame", False), "button_disabled": ("fill", "frame", False),
    "button_highlight": ("raised", "accent", False), "button_pressed": ("slot", "accent", False),
    "button_pressed_highlighted": ("raised", "accent", True), "ingredient_list_slot_background": ("slot", "dark", False),
    "bookmark_list_slot_background": ("slot", "dark", False), "recipe_catalyst_slot_background": ("slot", "dark", False),
    "scrollbar_background": ("slot", "dark", False), "scrollbar_marker": ("raised", "accent", False),
    "catalyst_tab": ("fill", "frame", False), "recipe_options_tab": ("fill", "frame", False),
    "tab_selected": ("raised", "accent", True), "tab_unselected": ("fill", "frame", False),
    "single_recipe_background": ("slot", "dark", False), "recipe_preview_background": ("slot", "dark", False),
    "interactive_ingredient_tooltip_background": ("fill", "frame", False),
}

def sprite(p, fill, edge, accent):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = im.load()
    for y in range(S):
        for x in range(S):
            corner = (x in (0, S-1)) and (y in (0, S-1))
            if corner: continue
            border = x in (0, S-1) or y in (0, S-1)
            px[x, y] = p[edge] if border else p[fill]
    if accent:
        for x in range(2, S-2): px[x, 1] = p["accent"]
    return im

FORGE_ACCENT = {"button_enabled", "button_highlight", "tab_unselected", "recipe_options_tab", "catalyst_tab"}

def main():
    for v, p in PAL.items():
        d = os.path.join(OUT, v); os.makedirs(d, exist_ok=True)
        for name, (f, e, a) in KIND.items():
            sprite(p, f, e, a or (v.startswith("forge") and name in FORGE_ACCENT)).save(os.path.join(d, name + ".png"))
            with open(os.path.join(d, name + ".png.mcmeta"), "w") as fh:
                json.dump({"gui": {"scaling": {"type": "nine_slice", "width": S, "height": S, "border": B}}}, fh, indent=2)
    print("wrote", len(PAL) * len(KIND), "sprites to", os.path.normpath(OUT))

if __name__ == "__main__":
    main()
