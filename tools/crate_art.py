"""Redraws the fruit inside Alterra Kitchen's crate textures (kitchen/.../textures/block/<name>_crate_top.png and _side.png).
Only the fruit area is touched: the 12x12 inside of the top, and the three 12x2 gaps between the slats on the sides.
The wooden container is left as it is. Fruit is drawn as small shaded sprites (highlight top left, shade bottom right,
a dark gap between them) so they read like the crates in Farmer's Delight instead of noise.
Needs Pillow. Run: python3 tools/crate_art.py [name ...]   (no names = all crates)"""
import random, sys
from pathlib import Path
from PIL import Image

TEX = Path(__file__).resolve().parent.parent / "kitchen/src/main/resources/assets/alterra_kitchen/textures/block"
GAP = (58, 40, 25, 255)            # the dark inside of the crate, between the fruit
WINDOWS = (2, 6, 10)               # first row of each gap between slats on the side texture
LO, HI = 2, 13                     # fruit area on the top texture (and the columns on the side)


def rgb(h): return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def blob(w, h, p, rng, kind):
    """A w x h shaded sprite as {(x, y): color}. p = dict of shade/base/light/glint/(accent) colors."""
    out, best = {}, None
    for y in range(h):
        for x in range(w):
            nx, ny = (x + .5 - w / 2) / (w / 2), (y + .5 - h / 2) / (h / 2)
            if nx * nx + ny * ny > 1.0: continue
            l = -.6 * nx - .8 * ny
            out[(x, y)] = p["light"] if l > .5 else p["shade"] if l < -.2 else p["base"]
            if best is None or l > best[0]: best = (l, (x, y))
    if best: out[best[1]] = p["glint"]
    cx = w // 2
    if kind == "stem":
        out[(cx, 0) if (cx, 0) in out else (cx, 1)] = p["accent"]
    elif kind == "dimple" and (cx, 1) in out: out[(cx, 1)] = p["shade"]
    elif kind == "crease":
        for y in range(1, h - 1):
            if (cx, y) in out: out[(cx, y)] = p["shade"]
    elif kind == "crown":
        for x in range(cx - 1, cx + 1):
            if (x, 0) in out: out[(x, 0)] = p["accent"]
        if (cx, 1) in out: out[(cx, 1)] = p["accent"]
    elif kind == "eyes":
        for q in ((1, 1), (2, 1), (1, 2)):
            if q in out: out[q] = p["accent"]
    elif kind == "husk":                        # hairy coconut: fibres, then three eyes and a shine
        for q, c in list(out.items()):
            r = rng.random()
            if r < .07: out[q] = p["fibre"] if c != p["shade"] else p["accent"]
            elif r < .12 and c != p["shade"]: out[q] = p["light"]
        for q in ((1, 1), (2, 1), (1, 2)):
            if q in out: out[q] = p["accent"]
        out[best[1]] = p["glint"]
    elif kind == "wrinkle":                     # date: a dark crease along the fruit and a stem nub at one end
        for x in range(1, w - 1):
            if (x, h // 2) in out and (x + 1) % 2: out[(x, h // 2)] = p["shade"]
        if (0, h // 2) in out: out[(0, h // 2)] = p["accent"]
    elif kind == "tip":
        if (w - 1, h - 1) in out: out[(w - 1, h - 1)] = p["accent"]
        elif (w - 2, h - 1) in out: out[(w - 2, h - 1)] = p["accent"]
    elif kind == "stalk":
        out[(w - 1, 0)] = p["accent"]
    elif kind == "ember":
        inner = [q for q in out if 0 < q[0] < w - 1 or 0 < q[1] < h - 1]
        for q in rng.sample(inner, min(len(inner), 2)): out[q] = p["spark"]
        if inner and rng.random() < .6: out[rng.choice(inner)] = p["glint"]
    elif kind == "frost":
        inner = [q for q in out if out[q] in (p["light"], p["base"])]
        if inner and rng.random() < .3: out[rng.choice(inner)] = p["spark"]
    elif kind == "spines":
        inner = [q for q in out if out[q] == p["base"]]
        for q in rng.sample(inner, min(len(inner), 2)): out[q] = p["light"]
    elif kind == "bloom":
        if (cx, 0) in out: out[(cx, 0)] = p["accent"]
    return out


# name: (sprite w, h, pitch w, h, decoration, colors). Colors are hex: shade, base, light, glint, then optional accent/spark.
def pal(shade, base, light, glint, accent=None, spark=None, glow=None, gap=None, fibre=None):
    p = dict(shade=rgb(shade), base=rgb(base), light=rgb(light), glint=rgb(glint))
    if accent: p["accent"] = rgb(accent)
    if spark: p["spark"] = rgb(spark)
    if glow: p["glow"] = rgb(glow)
    if fibre: p["fibre"] = rgb(fibre)
    p["gap"] = rgb(gap) if gap else GAP
    return p


CRATES = {
    "banana":       (6, 2, 6, 3, "tip",    pal("c49218", "ecc22a", "f6dc58", "fff6b0", "5c3e0a")),
    "baobab_fruit": (5, 4, 5, 4, "crease", pal("6d742c", "9ba03c", "b0bc47", "e2e1a3")),
    "cherry":       (3, 3, 3, 3, "stalk",  pal("7a0612", "c8102a", "e8505c", "ffb0b8", "5e6e2a")),
    "coconut":      (5, 5, 5, 5, "husk",   pal("5e3c1e", "9a7040", "bc9258", "e2c48c", "3a2410", gap="2a1a0e", fibre="7a5428")),
    "date":         (5, 3, 5, 3, "wrinkle", pal("5a2c14", "93491f", "b8683a", "e8b888", "3a1c0c")),
    "fig":          (4, 4, 4, 4, "bloom",  pal("320f3e", "593466", "7a4c8a", "c5c89d", "969c4d")),
    "grapes":       (3, 3, 3, 3, None,     pal("1c0850", "43217e", "6a4aa8", "9c87b8")),
    "white_grapes": (3, 3, 3, 3, None,     pal("9a9760", "c8c58f", "e2e0c7", "f6f5e6")),
    "mango":        (5, 4, 5, 4, "stem",   pal("b3500a", "e8780a", "f59a2e", "ffe0a0", "a4bc0b")),
    "orange":       (5, 5, 5, 5, "dimple", pal("c45a00", "f08000", "f8a52a", "ffd78a")),
    "peach":        (4, 4, 4, 4, "crease", pal("c65647", "f28077", "f2b9a1", "f7d8cb")),
    "plum":         (4, 4, 4, 4, "crease", pal("3a143a", "6b3a73", "8f6396", "c9b5c9")),
    "pomegranate":  (5, 5, 5, 5, "crown",  pal("730302", "c62f26", "ed4c43", "f59c97", "8a0a08")),
    "prickly_pear": (4, 5, 4, 5, "spines", pal("8a1040", "c4285c", "ec5c8e", "f4a5c0")),
    "sour_apple":   (4, 4, 4, 4, "stem",   pal("167502", "6fb413", "9dff1c", "c9ff82", "3a2819")),
    "sun_apple":    (4, 4, 4, 4, "stem",   pal("a84d10", "e8863d", "f0b04a", "f3f5a2", "3a2819")),
    "ember_apple":  (4, 4, 4, 4, "ember",  pal("45130b", "7a2210", "9a3414", "ffc446", None, "ffde99")),
    "ember_pear":   (4, 5, 4, 5, "ember",  pal("3b110a", "6e1c0d", "8a2810", "ffc446", None, "ffde99")),
    "ember_plum":   (4, 4, 4, 4, "ember",  pal("2e0c12", "5b180c", "7a2414", "ffc446", None, "ffde99")),
    "frost_apple":  (4, 4, 4, 4, "frost",  pal("467ba7", "66a2c9", "8acdee", "bee3f5", None, "ffffff")),
    "frost_pear":   (4, 5, 4, 5, "frost",  pal("2c5c8c", "6eacd2", "9fd4ee", "b8dcf0", None, "ffffff")),
    "frost_plum":   (4, 4, 4, 4, "frost",  pal("2c5c8c", "5f9ac2", "a2d8f2", "cbe9f7", None, "ffffff")),
}

def tinted(p, d):
    """The same fruit a bit lighter or darker (ripeness), so no two look identical. Accents and glints stay."""
    q = dict(p)
    for k in ("shade", "base", "light"):
        r, g, b, _ = p[k]
        q[k] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255)
    return q


def scatter(name, spec, rng, area_w, area_h, one_row=False):
    """Fruit as a loose heap: uneven spacing and rows, sizes and tones that vary, gaps where one is missing.
    Returns [(x, y, sprite)] back to front, with x and y counted from the top left of the fruit area (they can be negative)."""
    w, h, pw, ph, kind, p = spec
    out, y = [], -rng.randint(0, max(0, (ph - 2) if ph < 5 else ph // 3))
    while y < area_h:
        x = -rng.randint(0, pw - 1 if pw < 5 else pw // 3)
        while x < area_w:
            if w >= 5 or rng.random() > .04:
                jw, jh = w, h
                if rng.random() < .3: jw, jh = (w + rng.choice((-1, 1)), h) if rng.random() < .5 else (w, h + rng.choice((-1, 1)))
                jw, jh = max(3 if w > 2 else 2, jw), max(2, jh)
                if name == "banana": jh = 2
                sp = blob(jw, jh, tinted(p, rng.randint(-11, 11)), rng, kind)
                if name in ("banana", "date") and rng.random() < .5:
                    sp = {(jw - 1 - sx, sy): col for (sx, sy), col in sp.items()}
                dy = rng.choice((0, 0, 0, 1, -1))
                out.append((x, y + dy, sp))
            x += pw + (rng.choice((-1, 0, 0, 0, 1)) if pw >= 4 else rng.choice((0, 0, 0, 1)))
        if one_row: break
        y += ph + (rng.choice((-1, 0, 0, 0, 1)) if ph >= 4 else rng.choice((0, 0, 0, 1)))
    out.sort(key=lambda t: t[1])
    return out


def draw(name):
    spec = CRATES[name]
    p = spec[5]
    n = HI - LO + 1
    # --- top: a heap of fruit, with the crate's walls shading its edges
    rng = random.Random(name + "top")
    top = Image.open(TEX / f"{name}_crate_top.png").convert("RGBA"); px = top.load()
    for y in range(LO, HI + 1):
        for x in range(LO, HI + 1): px[x, y] = p["gap"]
    for x0, y0, sp in scatter(name, spec, rng, n, n):
        for (sx, sy), col in sp.items():
            x, y = LO + x0 + sx, LO + y0 + sy
            if LO <= x <= HI and LO <= y <= HI: px[x, y] = col
    for y in range(LO, HI + 1):
        for x in range(LO, HI + 1):
            k = .72 if x == LO or y == LO else .86 if x == HI or y == HI else 1
            if k < 1 and px[x, y] != p["gap"]:
                r, g, b, _ = px[x, y]; px[x, y] = (int(r * k), int(g * k), int(b * k), 255)
    top.save(TEX / f"{name}_crate_top.png")
    # --- side: the upper part of the fruit shows between the slats, a different heap in each gap
    side = Image.open(TEX / f"{name}_crate_side.png").convert("RGBA"); sx_ = side.load()
    for k, wy in enumerate(WINDOWS):
        rng = random.Random(f"{name}side{k}")
        for y in (wy, wy + 1):
            for x in range(LO, HI + 1): sx_[x, y] = p["gap"]
        for x0, y0, sp in scatter(name, spec, rng, n, 2, one_row=True):
            for (sx, sy), col in sp.items():
                x, y = LO + x0 + sx, wy + y0 + sy
                if LO <= x <= HI and wy <= y <= wy + 1: sx_[x, y] = col
    side.save(TEX / f"{name}_crate_side.png")


if __name__ == "__main__":
    names = sys.argv[1:] or list(CRATES)
    for n in names: draw(n)
    print("redrew", len(names), "crates")
