#!/usr/bin/env python3
"""Draws the animated hub logos of Illuminate, Atmos and Kitchen in the style of Alterra's own icon: a 16x16 pixel-art orb with a shaded
dark outline and hard pixel edges. Each logo is a tall strip of 16x16 frames (Prism Lib plays it at about 9 fps).

    python3 tools/make_hub_logos.py            (needs Pillow)"""
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
N = 8   # frames

ORB = [  # the silhouette of Alterra's icon
    "......XXXX......", "....XXXXXXXX....", "..XXXXXXXXXXXX..", "..XXXXXXXXXXXX..",
    ".XXXXXXXXXXXXXX.", ".XXXXXXXXXXXXXX.", "XXXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX",
    "XXXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX", ".XXXXXXXXXXXXXX.", ".XXXXXXXXXXXXXX.",
    "..XXXXXXXXXXXX..", "..XXXXXXXXXXXX..", "....XXXXXXXX....", "......XXXX......"]
MASK = {(x, y) for y, row in enumerate(ORB) for x, c in enumerate(row) if c == "X"}
LIGHT_EDGE, DARK_EDGE = (0x46, 0x54, 0x66), (0x26, 0x2e, 0x3a)   # Alterra's outline: lighter top left, darker bottom right

def hexc(s): return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))
def band(y, stops):
    """hard bands: stops is a list of (first row, colour)"""
    c = stops[0][1]
    for row, col in stops:
        if y >= row: c = col
    return hexc(c)

EDGE = {(x, y): LIGHT_EDGE if x + y < 15 else DARK_EDGE for (x, y) in MASK
        if any((x + dx, y + dy) not in MASK for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}

# ---------------------------------------------------------------- Illuminate: a low sun with turning rays over dark hills
SKY = [(0, "#4a5aa8"), (3, "#7a62a8"), (5, "#c8709a"), (7, "#ee8c82"), (9, "#f6aa68"), (11, "#f8c878")]
def illuminate(x, y, t):
    cx, cy = 7.5, 7.0
    d = math.hypot(x - cx, y - cy)
    ridge = 11 if 5 <= x <= 10 else 12
    if y >= ridge: return hexc("#34385a") if y == ridge else hexc("#262a48")
    if d <= 2.3: return hexc("#fffbe0") if d <= 1.4 else hexc("#ffe898")
    if d <= 3.2: return hexc("#fbd070")
    ang = math.atan2(y - cy, x - cx)
    for k in range(8):
        a = k * math.pi / 4 + t * math.pi / 4 / N
        diff = abs((ang - a + math.pi) % (2 * math.pi) - math.pi)
        if 4.2 <= d <= 6.2 and diff * d < 0.5: return hexc("#fff0b8")
    return band(y, SKY)

# ---------------------------------------------------------------- Atmos: a cloud with rain falling out of it and a flash of lightning
CLOUD = ["....WWWW....", "..WWWWWWWW..", ".WWWWWWWWWW.", "WWWWWWWWWWWW", ".SSSSSSSSSS."]
BOLT = {(8, 9), (7, 10), (8, 10), (7, 11), (8, 12), (7, 13)}
def atmos(x, y, t):
    c = hexc("#3f5f9c") if y < 7 else hexc("#5578b4") if y < 11 else hexc("#6a90c8")
    flash = t in (5, 6)
    if flash: c = hexc("#8aa8dc")
    row, col = y - 4, x - 2
    if 0 <= row < len(CLOUD) and 0 <= col < 12:
        ch = CLOUD[row][col]
        if ch == "W": c = hexc("#f2f6fc") if row < 3 else hexc("#dbe5f2")
        if ch == "S": c = hexc("#9aa8c0")
    if y >= 10 and not flash:
        for dx, off in ((4, 0), (7, 3), (10, 1), (13, 4)):
            yy = 9 + (t + off) % 5
            if x == dx and yy <= y <= yy + 1: c = hexc("#cfe0f8") if y == yy else hexc("#9bb9e6")
    if flash and (x, y) in BOLT: c = hexc("#ffee80")
    return c

# ---------------------------------------------------------------- Kitchen: a glass of juice with bubbles rising and a straw
def kitchen(x, y, t):
    c = band(y, [(0, "#c8683a"), (5, "#d98046"), (10, "#e8a058")])
    narrow = 1 if y >= 12 else 0
    left, right = 5 + narrow, 10 - narrow
    if 5 <= y <= 13 and left < x < right:
        c = hexc("#f6b02c") if y >= 7 else hexc("#fbe4b8")
        if y == 7: c = hexc("#fde08a")
        if x == left + 1 and 8 <= y <= 11: c = hexc("#fcd060")
        for bx, off in ((7, 0), (8, 3)):
            if x == bx and y == 12 - (t + off) % 5 and y >= 8: c = hexc("#fff0b8")
    if 5 <= y <= 13 and (x in (left, right) or (y == 13 and left <= x <= right)): c = hexc("#e8f2fc")
    if (x, y) in {(9, 1), (9, 2), (9, 3), (9, 4), (10, 0), (10, 1)}: c = hexc("#d8382c")
    return c

def strip(draw):
    img = Image.new("RGBA", (16, 16 * N), (0, 0, 0, 0))
    for t in range(N):
        for (x, y) in MASK:
            img.putpixel((x, t * 16 + y), (*EDGE.get((x, y), draw(x, y, t)), 255))
    return img

for mod, mod_id, draw in (("illuminate", "alterra_illuminate", illuminate), ("weather", "alterra_weather", atmos), ("kitchen", "alterra_kitchen", kitchen)):
    out = ROOT / mod / "src/main/resources/assets" / mod_id / "textures/gui/hub_logo.png"
    strip(draw).save(out); print("wrote", out.relative_to(ROOT))
    (out.parent / "hub_logo.png.mcmeta").unlink(missing_ok=True)   # pixel art: no blur
