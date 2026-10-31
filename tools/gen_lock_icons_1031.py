# SPDX-License-Identifier: GPL-3.0-or-later
from PIL import Image
SPR = 'src/main/resources/assets/apex/textures/gui/sprites/'
PAL = {
    'skyglass': {'o': (30, 60, 96, 255), 'h': (248, 252, 255, 255), 'b': (196, 226, 244, 255), 's': (126, 178, 214, 255), 'a': (92, 196, 255, 255)},
    'forge':    {'o': (20, 16, 14, 255), 'h': (255, 226, 140, 255), 'b': (200, 170, 140, 255), 's': (150, 120, 96, 255), 'a': (255, 140, 30, 255)},
}
CHAIN = ["ohho..",
         "h..so.",
         "h.assb",
         "osa..b",
         "..s..b",
         "..obbo"]
STAR = ["..ha..",
        "..ha..",
        "hhhass",
        ".hhas.",
        ".ho.s.",
        "h....s"]
def draw(rows, p):
    im = Image.new('RGBA', (6, 6), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c in p: im.putpixel((x, y), p[c])
    return im
if __name__ == '__main__':
    for style, p in PAL.items():
        draw(CHAIN, p).save(f'{SPR}slot_locked_{style}.png')
        draw(STAR, p).save(f'{SPR}uuid_locked_{style}.png')
