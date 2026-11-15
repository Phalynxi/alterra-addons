"""Original pixel art for Alterra Kitchen's wild plants and what they drop (16x16): the fiddlehead fern, duskberry, wild parsley, meadow cap and rosebay (all poisonous, and made to look good to eat).
Every shape is built from a ribbon (a leaf blade that curves, tapers and has saw-toothed edges), a shaded ball (berries) or a shaded ellipse (caps), and shaded the same way throughout:
five levels per colour (0 dark edge, 1 dark, 2 mid, 3 light, 4 highlight), light from the top left, a lit top-left rim and a shadowed bottom-right rim. Run: python3 tools/plant_art.py [--ascii]"""
import math, random, sys
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "kitchen/src/main/resources/assets/alterra_kitchen/textures"
def ramp(*cols): return tuple(cols)   # level 0..4
RAMPS = {
    "poison": ramp((26, 12, 42), (58, 28, 88), (102, 54, 142), (150, 92, 198), (204, 156, 244)),
    "sun": ramp((52, 62, 20), (96, 124, 36), (160, 186, 52), (226, 220, 84), (255, 246, 160)),
    "leaf": ramp((18, 42, 26), (30, 74, 42), (52, 114, 60), (90, 162, 80), (150, 210, 120)),
    "pale": ramp((52, 74, 54), (84, 120, 84), (132, 172, 106), (184, 214, 142), (232, 246, 200)),
    "berry": ramp((18, 8, 30), (44, 16, 70), (80, 40, 122), (128, 84, 176), (240, 228, 252)),
    "flesh": ramp((92, 88, 70), (150, 146, 118), (206, 202, 170), (236, 232, 206), (252, 250, 238)),
    "cap": ramp((46, 54, 20), (92, 106, 38), (146, 160, 62), (196, 208, 100), (240, 246, 190)),
    "pink": ramp((96, 20, 62), (176, 50, 112), (232, 100, 164), (250, 156, 200), (255, 220, 236)),
    "stem": ramp((40, 30, 24), (72, 58, 40), (110, 92, 60), (150, 130, 84), (190, 170, 120)),
    "blot": ramp((60, 10, 30), (110, 24, 52), (150, 48, 82), (190, 84, 112), (230, 130, 150)),
    "gold": ramp((90, 62, 10), (170, 120, 20), (226, 176, 36), (250, 214, 90), (255, 244, 180)),
    "magenta": ramp((40, 10, 56), (84, 22, 92), (134, 38, 124), (182, 64, 156), (232, 128, 200)),
    "steel": ramp((24, 36, 58), (46, 72, 104), (80, 120, 148), (126, 168, 190), (186, 218, 232)),
    "ember": ramp((100, 42, 10), (172, 78, 18), (224, 126, 38), (248, 172, 78), (255, 224, 148)),
    "moss": ramp((24, 44, 22), (44, 80, 34), (74, 120, 48), (118, 164, 70), (178, 210, 112)),
    "spring": ramp((22, 56, 18), (44, 102, 30), (82, 154, 44), (138, 200, 70), (206, 238, 140)),
    "blueb": ramp((18, 22, 62), (38, 52, 122), (66, 92, 178), (112, 146, 222), (214, 232, 255)),
    "parsley": ramp((16, 50, 20), (30, 92, 32), (56, 140, 50), (100, 186, 74), (170, 226, 128)),
    "cream": ramp((96, 76, 56), (156, 128, 98), (210, 186, 152), (236, 220, 192), (252, 246, 230)),
    "tan": ramp((72, 48, 28), (120, 84, 50), (166, 122, 76), (202, 160, 108), (232, 204, 160)),
    "tip": ramp((100, 14, 40), (170, 30, 64), (222, 60, 100), (246, 110, 140), (255, 170, 190)),
}
LIGHT = (-0.70, -0.70)   # towards the top left

class Sprite:
    def __init__(self): self.g = [[None] * 16 for _ in range(16)]
    clipped = 0
    def put(self, x, y, r, lv):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < 16 and 0 <= y < 16: self.g[y][x] = (r, max(0, min(4, lv)))
        else: self.clipped += 1   # something drawn off the sprite: a shape that would be cut off
    def at(self, x, y): return self.g[y][x] if 0 <= x < 16 and 0 <= y < 16 else None
    def rim(self):
        """A lit edge on the top left and a shadowed edge on the bottom right, so every shape has a light side."""
        new = [row[:] for row in self.g]
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if not c or c[1] == 0: continue
                if (self.at(x + 1, y) is None or self.at(x, y + 1) is None) and c[1] >= 2: new[y][x] = (c[0], c[1] - 1)
                elif (self.at(x - 1, y) is None or self.at(x, y - 1) is None) and 1 < c[1] < 4: new[y][x] = (c[0], c[1] + 1)
        self.g = new
    def outline(self, r="leaf", dark=0):
        edge = [(x, y) for y in range(16) for x in range(16) if self.g[y][x] is None and any(self.at(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for x, y in edge:
            near = [self.at(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if self.at(x + dx, y + dy)]
            self.g[y][x] = (near[0][0], 0)   # the edge takes the darkest tone of the colour next to it
    def image(self):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); p = im.load()
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if c: p[x, y] = tuple(int(v * (0.74 if c[1] == 4 else 0.8)) for v in RAMPS[c[0]][c[1]]) + (255,)
        return im
    def ascii(self):
        key = {n: "PSLBFCNKDGTMabcdefg"[i] for i, n in enumerate(RAMPS)}   # a letter per colour, its level as the digit; 0 dark edge ... 4 highlight
        return "\n".join("".join(" . " if not c else f" {key[c[0]]}{c[1]}" for c in row) for row in self.g)

# ---------------------------------------------------------------- the leaf ribbon: a blade that curves, tapers and has saw-toothed edges, lit from the top left
def ribbon(s, x, y, ang, length, hw, r, curve=16, saw=True, vein=1, tip=None, shade_r=None, end_r=None, vein_r=None):
    side = 1 if ang >= 0 else -1; px, py = x, y; pts = []
    steps = int(length * 2)
    for i in range(steps):
        t = i / steps; a = math.radians(ang + side * curve * t ** 1.5)
        px += math.sin(a) * 0.5; py -= math.cos(a) * 0.5; pts.append((px, py, a, t))
    for i, (cx, cy, a, t) in enumerate(pts):
        w = hw * math.sin(math.pi * min(1, t * 0.92 + 0.1)) ** 0.75
        nx, ny = math.cos(a), math.sin(a)   # the normal of the heading (sin a, -cos a)
        j = -w
        while j <= w + 0.01:
            if saw and abs(j) > w - 0.9 and (i // 3) % 2 == 0 and w > 1.2: j += 0.5; continue   # saw teeth along the edge
            qx, qy = cx + nx * j, cy + ny * j
            lit = (nx * j * LIGHT[0] + ny * j * LIGHT[1]) > 0.05
            if abs(j) < 0.35 and vein: s.put(qx, qy, vein_r or r, 2 if vein_r else 1); j += 0.5; continue
            base = (r if lit else (shade_r or r))
            if end_r and t > 0.68: base = end_r   # the young end of a frond is another colour
            s.put(qx, qy, base, 3 if lit else 2); j += 0.5
    if tip and pts: s.put(pts[-1][0], pts[-1][1], tip, 2); s.put(pts[-1][0], pts[-1][1] + 0.6, tip, 1)
    return pts

def ball(s, cx, cy, rad, r, gloss=True):
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy; d = math.hypot(dx, dy)
            if d <= rad:
                lit = (dx * LIGHT[0] + dy * LIGHT[1]) / max(d, 0.001) * (d / rad)
                s.put(x, y, r, 3 if lit > 0.45 else (2 if lit > -0.2 else 1))
    if gloss: s.put(cx - rad * 0.35, cy - rad * 0.4, r, 4)

def blade_ellipse(s, cx, cy, rx, ry, ang, r):
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy; u, v = dx * ca + dy * sa, -dx * sa + dy * ca
            if (u / rx) ** 2 + (v / ry) ** 2 <= 1:
                lit = (dx * LIGHT[0] + dy * LIGHT[1]) > 0.1
                s.put(x, y, r, 1 if abs(v) < 0.5 else (3 if lit else 2))


# ---------------------------------------------------------------- the fern frond: a spine with leaflets in pairs that slant up and out (a fishbone), longest low down, shorter towards the tip, a gap between pairs
def fishbone(s, x, y, lean, rows, maxlen, r, tipr=None, spine_r=None, thick=True, gap=2):
    sx = x
    for i in range(rows):
        yy = y - i; sx = x + lean * i
        s.put(sx, yy, spine_r or r, 1)
        u = i / (rows - 1)
        if i >= 2 and i % gap == 0 and u < 0.97:
            ln = max(1, round(maxlen * (1.15 - u) * 0.95))
            for sd in (-1, 1):
                for k in range(1, ln + 1):
                    px_, py_ = sx + sd * k, yy - round(k * 0.55)
                    lit = sd < 0
                    s.put(px_, py_, r, 3 if lit else 2)                      # the leaflet
                    if thick and k < ln: s.put(px_, py_ + 1, r, 2 if lit else 1)   # and its shadowed underside
    if tipr == "curl":   # a fiddlehead: the young tip rolled up in a tight coil
        for dx, dy, lv in ((0, 0, 3), (1, 0, 3), (1, 1, 2), (0, 1, 2), (-1, 1, 1), (-1, 0, 3), (-1, -1, 4), (0, -1, 4), (1, -1, 3)): s.put(sx + dx, y - rows - 1 + dy, r, lv)
    elif tipr: s.put(sx, y - rows, tipr, 3); s.put(sx, y - rows + 1, tipr, 2)

# ---------------------------------------------------------------- the plants (blocks)
def frond_only(r, vein_r, k=1.0):
    """One frond on its own sheet (for a rosette of tilted planes)."""
    s = Sprite(); fishbone(s, 8, 15, 0.0, int(13 * k), 4, r, "curl", vein_r); s.rim(); return s
def fern(r, tipr, seed, k=1.0, vein_r=None):
    s = Sprite()
    fishbone(s, 8, 15, 0.0, int(12 * k), 4, r, tipr, vein_r, gap=3)      # the tall middle frond
    fishbone(s, 7, 15, -0.6, int(9 * k), 3, r, tipr, vein_r, gap=3)      # one arching left
    fishbone(s, 9, 15, 0.6, int(9 * k), 3, r, tipr, vein_r, gap=3)       # and one right
    s.rim(); return s
def nightshade():
    s = Sprite()
    for x0, y0, a, ln in ((7, 15, -48, 8), (9, 15, 48, 8), (8, 15, 0, 9)): ribbon(s, x0, y0, a, ln, 1.5, "stem", curve=8, saw=False, vein=0)   # twigs
    for cx, cy, a in ((4, 10, -35), (12, 9, 30), (8, 6, 85), (5, 5, -60), (11, 4, 60), (8, 12, 10)): blade_ellipse(s, cx, cy, 3.4, 1.8, a, "leaf")
    for x, y in ((3.5, 12.5), (7, 10.5), (11.5, 12), (12.5, 6.5), (5.5, 7.5)):
        ball(s, x, y, 1.9, "blueb"); s.put(x, y - 2, "leaf", 3); s.put(x + 0.4, y + 0.6, "blueb", 0)
    s.rim(); return s
def hemlock():
    s = Sprite()
    fishbone(s, 7, 15, -0.45, 7, 2, "parsley", None, "parsley"); fishbone(s, 9, 15, 0.45, 7, 2, "parsley", None, "parsley")      # lacy leaves low down
    for y in range(15, 6, -1): s.put(8, y, "parsley", 2); s.put(7, y, "parsley", 3) if y > 8 else None                     # the stem
    for ang, ln in ((-72, 6), (-40, 7), (0, 8), (40, 7), (72, 6)):   # the umbel: spokes opening like an umbrella, a lacy flower head on each
        ex, ey = 8 + math.sin(math.radians(ang)) * ln * 0.9, 6.5 - math.cos(math.radians(ang)) * ln * 0.5
        for t in range(1, 5): s.put(8 + (ex - 8) * t / 5, 6.5 + (ey - 6.5) * t / 5, "parsley", 3)
        for dx, dy, lv in ((0, 0, 4), (-1, 0, 4), (1, 0, 3), (0, -1, 4), (-1, -1, 3), (1, -1, 4), (0, 1, 3), (-1, 1, 2), (1, 1, 2), (-2, 0, 3), (2, 0, 2), (0, -2, 3)):   # tiny flowers in a lacy cluster, with green showing between
            s.put(ex + dx, ey + dy - 0.5, "flesh", lv)
    s.rim(); return s
def mushroom(s, cx, top, cw, ch, sh):
    # stem, ring, cup, then the cap with gills under it
    for y in range(top + ch, top + ch + sh):
        for dx in (0, 1): s.put(cx - 1 + dx, y, "flesh", 3 if dx == 0 else 2)
    s.put(cx - 1, top + ch + 2, "flesh", 1); s.put(cx, top + ch + 2, "flesh", 1)
    for dx in (-2, -1, 0, 1): s.put(cx + dx, top + ch + sh, "flesh", 4 if dx < 0 else 3)
    s.put(cx - 2, top + ch + sh - 1, "flesh", 3); s.put(cx + 1, top + ch + sh - 1, "flesh", 2)
    for x in range(cx - cw // 2, cx + cw // 2 + 1): s.put(x, top + ch, "flesh", 1 if (x + cx) % 2 else 2)   # the gills
    for y in range(ch):
        w = cw / 2 * math.sqrt(max(0, 1 - ((y - ch + 0.5) / ch) ** 2)) + 0.4
        for x in range(int(cx - w), int(cx + w) + 1):
            lit = (x - cx) * LIGHT[0] + (y - ch / 2) * LIGHT[1] > 0.3
            rimzone = y >= ch - 1 or abs(x - cx) > w - 0.8   # the edge of the cap is yellowed
            s.put(x, top + y, "tan" if rimzone and not lit else "cream", 2 if rimzone and not lit else (3 if lit else (2 if (x - cx) < w * 0.45 else 1)))
    for x in range(cx - 2, cx + 2): s.put(x, top + ch + sh, "stem", 2 if x > cx - 1 else 3)   # brown soil round the cup
    s.put(cx - 3, top + ch + sh, "moss", 3); s.put(cx + 2, top + ch + sh, "moss", 2)
    s.put(cx - 1, top + 1, "cream", 4); s.put(cx - 2, top + 2, "cream", 4)
def deathcap():
    s = Sprite(); mushroom(s, 7, 3, 9, 4, 7); mushroom(s, 12, 8, 5, 3, 4); mushroom(s, 2, 10, 3, 2, 3)

    s.rim(); return s
def oleander(k=1.0):
    s = Sprite()
    for ang, ln, hw in ((-66, 8, 1.4), (-42, 11, 1.7), (-18, 13, 1.8), (16, 13, 1.8), (42, 11, 1.7), (66, 8, 1.4)): ribbon(s, 8 + ang / 45, 14.4, ang, ln * k, hw, "leaf", curve=10, saw=False)
    for cx, cy in ((4.5, 5.5), (10.5, 4.5), (7.5, 8.5), (12.5, 9)):   # five-petalled flowers
        for dx, dy in ((0, -1.4), (1.4, -0.4), (0.9, 1.2), (-0.9, 1.2), (-1.4, -0.4)): ball(s, cx + dx, cy + dy, 1.1, "pink", gloss=False)
        s.put(cx, cy, "gold", 3)
    s.rim(); return s

# ---------------------------------------------------------------- what they drop (items): bigger, simpler, a dark edge round each
def item_fronds(r, tipr, shade_r=None, end_r=None, vein_r=None, k=1.0):
    s = Sprite(); fishbone(s, 7, 14.4, 0.25, int(12 * k), 4, r, tipr, vein_r, gap=3); s.rim(); s.outline(); return s
def item_berries():
    s = Sprite(); ribbon(s, 8, 14, 0, 9, 0.5, "stem", curve=0, saw=False, vein=0); ribbon(s, 8, 10, -50, 5, 0.5, "stem", curve=0, saw=False, vein=0); ribbon(s, 8, 9, 50, 5, 0.5, "stem", curve=0, saw=False, vein=0)
    blade_ellipse(s, 5, 3.5, 3, 1.7, -25, "leaf"); blade_ellipse(s, 11, 3.5, 3, 1.7, 25, "moss")
    for x, y in ((4.5, 9), (8, 12), (11.5, 8.5), (8, 7.5)): ball(s, x, y, 2.0, "blueb"); s.put(x, y - 2.2, "moss", 3); s.put(x + 0.4, y + 0.6, "blueb", 0)
    s.rim(); s.outline(); return s
def item_hemlock(k=1.0):
    s = Sprite(); fishbone(s, 8, 14, 0.0, int(13 * k), 4, "parsley", None, "spring"); fishbone(s, 6, 14, -0.55, int(10 * k), 4, "parsley", None, "spring"); fishbone(s, 10, 14, 0.55, int(10 * k), 4, "parsley", None, "spring"); s.rim(); s.outline(); return s
def item_deathcap():
    s = Sprite(); mushroom(s, 8, 2, 11, 5, 7); s.rim(); s.outline(); return s
def item_oleander(k=1.0):
    s = Sprite()
    for ang, x0, ln in ((-30, 6, 11), (14, 8, 12), (46, 10, 10)): ribbon(s, x0, 14, ang, ln * k, 1.8, "parsley", curve=8, saw=False, shade_r="leaf", vein_r="pale")
    for dx, dy in ((0, -1.3), (1.3, -0.4), (0.8, 1.1), (-0.8, 1.1), (-1.3, -0.4)): ball(s, 12 + dx, 3.5 + dy, 1.1, "pink", gloss=False)
    s.put(12, 3.5, "gold", 3); s.rim(); s.outline(); return s

def fit(fn, *a, **kw):
    """Draws a shape at full size and, if any of it falls off the 16x16 sprite, again a little smaller until all of it fits."""
    for k in [1.0 - 0.05 * i for i in range(12)]:
        sp = fn(*a, k=k, **kw)
        if not sp.clipped: return sp
    return sp
BLOCKS = {"poison_fern": lambda: fit(fern, "spring", "curl", 3, vein_r="parsley"), "nightshade": nightshade, "hemlock": hemlock, "deathcap": deathcap, "oleander": lambda: fit(oleander)}
ITEMS = {"poison_fern_fronds": lambda: fit(item_fronds, "spring", "curl", vein_r="parsley"), "nightshade_berries": item_berries, "hemlock_leaves": lambda: fit(item_hemlock), "deathcap": item_deathcap, "oleander_leaves": lambda: fit(item_oleander)}
def write_all():
    (OUT / "block").mkdir(parents=True, exist_ok=True); (OUT / "item").mkdir(parents=True, exist_ok=True)
    for n, f in BLOCKS.items(): f().image().save(OUT / f"block/{n}.png")
    for n, f in ITEMS.items(): f().image().save(OUT / f"item/{n}.png")
    frond_only("spring", "parsley").image().save(OUT / "block/poison_fern_frond.png")
if __name__ == "__main__":
    write_all()
    for n, f in {**BLOCKS, **ITEMS}.items():
        sp = f(); print(f"{n}: {sp.clipped} pixels off the edge" if sp.clipped else f"{n}: fits")
    if "--ascii" in sys.argv:
        for n in sys.argv[sys.argv.index("--ascii") + 1:]:
            f = {**BLOCKS, **ITEMS}[n]; print("==", n); print(f().ascii())
