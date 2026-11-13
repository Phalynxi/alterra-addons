"""Draws Alterra Kitchen's own press and trellis textures into kitchen/src/main/resources/assets/alterra_kitchen/textures/block:
 - fruit press: press_planks (frame and walls), press_beam (the posts, with iron straps), press_iron (the screw), press_pulp (the mash)
 - trellis: grape_purple and grape_white (berries), vine_leaves (leaves and a vine, with see-through gaps)
Models pick these up by name. Needs Pillow. Run: python3 tools/kitchen_art.py"""
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "kitchen/src/main/resources/assets/alterra_kitchen/textures/block"


def c(r, g, b, a=255): return (r, g, b, a)


def jitter(col, r, amt=8):
    d = r.randint(-amt, amt)
    return (max(0, min(255, col[0] + d)), max(0, min(255, col[1] + d)), max(0, min(255, col[2] + d)), col[3])


def knot(px, r, x, y, dark, mid):
    """A small knot: a dark middle with a ring round it."""
    for dx, dy in ((0, 0), (1, 0)): px[x + dx, y + dy] = dark
    for dx, dy in ((-1, 0), (2, 0), (0, -1), (1, -1), (0, 1), (1, 1)):
        if 0 <= x + dx < 16 and 0 <= y + dy < 16: px[x + dx, y + dy] = mid


def planks():
    """Four boards, each its own shade and length, with their own joints, grain, nails and now and then a knot."""
    r = random.Random(11)
    tones = [(c(124, 86, 50), c(146, 104, 62), c(98, 66, 38)), (c(112, 76, 44), c(134, 94, 56), c(88, 58, 32)),
             (c(134, 94, 54), c(156, 114, 68), c(106, 72, 42)), (c(118, 82, 48), c(140, 100, 60), c(94, 62, 36))]
    r.shuffle(tones)
    seam = c(54, 34, 18)
    im = Image.new("RGBA", (16, 16)); px = im.load()
    for i in range(4):
        base, light, dark = tones[i]
        joints = sorted({r.randint(3, 12) for _ in range(r.choice((1, 1, 2)))})
        for y in range(4 * i, 4 * i + 4):
            for x in range(16):
                px[x, y] = seam if y == 4 * i + 3 else (light if y == 4 * i else base)
        for _ in range(r.randint(7, 11)):                      # grain streaks of different lengths
            y, x0, n = 4 * i + r.randint(0, 2), r.randint(0, 14), r.randint(2, 7)
            for x in range(x0, min(16, x0 + n)): px[x, y] = r.choice((dark, dark, light))
        if r.random() < .5:
            kx = r.randint(2, 12)
            if all(abs(kx - j) > 2 for j in joints): knot(px, r, kx, 4 * i + 1, dark, c(*dark[:3]) if False else dark)
        for j in joints:
            for y in range(4 * i, 4 * i + 3): px[j, y] = seam
            for nx in (j - 2, j + 2):                          # a nail each side of a joint
                if 0 <= nx < 16: px[nx, 4 * i + 1] = c(150, 150, 156)
        if not joints: px[1, 4 * i + 1] = px[14, 4 * i + 1] = c(150, 150, 156)
    im.save(OUT / "press_planks.png")


def beam():
    """Dark upright beam: grain running up and down in uneven streaks, a knot and a crack, and two iron straps with rivets."""
    r = random.Random(12)
    base, light, dark = c(94, 63, 36), c(114, 79, 46), c(68, 44, 24)
    iron, ihi, ilo = c(78, 80, 88), c(132, 136, 146), c(46, 48, 54)
    im = Image.new("RGBA", (16, 16)); px = im.load()
    x = 0
    while x < 16:                                              # streaks 1-3 pixels wide, each its own shade
        w, col = r.choice((1, 1, 2, 3)), r.choice((base, base, light, dark))
        for xx in range(x, min(16, x + w)):
            for y in range(16): px[xx, y] = jitter(col, r, 5)
        x += w
    for _ in range(5):                                         # short dark cracks along the grain
        x, y0 = r.randint(0, 15), r.randint(0, 12)
        for y in range(y0, y0 + r.randint(2, 4)): px[x, y] = dark
    knot(px, r, r.randint(3, 11), 7, dark, base)
    for y0 in (3, 11):
        for x in range(16):
            px[x, y0], px[x, y0 + 1], px[x, y0 + 2] = ihi, iron, ilo
        for x in sorted(r.sample(range(1, 15), 3)): px[x, y0 + 1] = ihi
    im.save(OUT / "press_beam.png")


def screw():
    """The screw: dark iron with a thread running round it (a soft bright row and a dark row every four rows)."""
    r = random.Random(13)
    iron, hi, lo = c(92, 94, 102), c(132, 136, 146), c(60, 62, 70)
    im = Image.new("RGBA", (16, 16)); px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = hi if y % 4 == 0 else lo if y % 4 == 2 else jitter(iron, r, 3)
            if r.random() < .12: px[x, y] = lo if px[x, y] == hi else jitter(iron, r, 12)   # wear and grease
    im.save(OUT / "press_iron.png")


def pulp():
    """Wet mashed fruit: blotches of juice and flesh, with seeds, flecks of skin and a few shiny spots."""
    r = random.Random(14)
    deep, base, wet, shine = c(150, 52, 40), c(196, 92, 52), c(226, 132, 78), c(255, 226, 196)
    seed, skins = c(238, 220, 158), (c(104, 28, 36), c(88, 38, 78), c(104, 142, 46), c(110, 60, 28))
    # smooth-ish blotches: coarse noise on an 4x4 grid, looked up with wrap-around so the pattern tiles
    grid = [[r.random() for _ in range(4)] for _ in range(4)]
    im = Image.new("RGBA", (16, 16)); px = im.load()
    for y in range(16):
        for x in range(16):
            gx, gy = x / 4, y / 4
            x0, y0, fx, fy = int(gx) % 4, int(gy) % 4, gx % 1, gy % 1
            x1, y1 = (x0 + 1) % 4, (y0 + 1) % 4
            v = (grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx) * (1 - fy) + (grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx) * fy
            v += r.uniform(-.12, .12)
            px[x, y] = deep if v < .33 else base if v < .62 else wet
    for _ in range(9):
        x, y = r.randint(0, 15), r.randint(0, 15)
        px[x, y] = seed
        if r.random() < .5: px[(x + 1) % 16, y] = seed          # a seed is one or two pixels
    for _ in range(7): px[r.randint(0, 15), r.randint(0, 15)] = r.choice(skins)
    for _ in range(4): px[r.randint(0, 15), r.randint(0, 15)] = shine
    im.save(OUT / "press_pulp.png")


def grapes(name, shade, base, light, seed):
    """Berries of mixed sizes at uneven spots (2x2 mostly, now and then 3x3), each its own shade, shine top left and shade bottom right,
    on a dark underlay. The trellis clusters are only a couple of pixels wide, so this is mostly colour and a hint of roundness."""
    r = random.Random(seed)
    im = Image.new("RGBA", (16, 16), shade); px = im.load()
    berries = []
    for gy in range(-1, 9):
        for gx in range(-1, 9):
            big = r.random() < .3
            berries.append((gx * 2 + r.choice((-1, 0, 0, 1)) + (gy % 2), gy * 2 + r.choice((-1, 0, 0, 1)), 3 if big else 2, jitter(base, r, 12)))
    berries.sort(key=lambda t: t[1])
    for x0, y0, n, b in berries:
        for dy in range(n):
            for dx in range(n):
                if n == 3 and dx in (0, 2) and dy in (0, 2): continue          # a 3x3 berry has round corners
                col = light if (dx, dy) == (0, 0) or (n == 3 and (dx, dy) == (1, 0)) else shade if (dx, dy) == (n - 1, n - 1) else b
                px[(x0 + dx) % 16, (y0 + dy) % 16] = col
    im.save(OUT / f"{name}.png")


LEAVES = {
    "L": ["...vv...", ".b.vb.b.", "bbbvbbbl", "lbbvbbb.", ".bbvbbd.", "dbbvbbb.", ".dbvbd..", "..dvd...", "...v...."],
    "M": ["..vv..", ".bvbb.", "bbvbbl", "lbvbb.", ".bvbd.", "..vd.."],
    "S": ["..v..", ".bvb.", "bbvbl", ".bvb.", "..v.."],
}


def leaves():
    """Three leaf shapes (big, middle, small), each a different green, set at different heights along a winding vine, with a tendril."""
    r = random.Random(16)
    pal = {"v": c(34, 74, 26), "l": c(112, 178, 66), "b": c(66, 132, 44), "d": c(44, 98, 32)}
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load()
    vine = c(52, 84, 30)
    for y in range(16): px[7 + (1 if y % 6 < 3 else 0), y] = vine
    for y, x in ((1, 11), (2, 12), (2, 13), (3, 13)): px[x, y] = vine             # a tendril curling off
    spots = [("L", 0, 0, False), ("M", 9, 4, True), ("S", 1, 9, True), ("L", 7, 8, True), ("S", 11, 0, False)]
    for kind, ox, oy, flip in spots:
        shift = r.randint(-14, 14)
        bmp = LEAVES[kind]
        for y, row in enumerate(bmp):
            for x, ch in enumerate(row):
                if ch == ".": continue
                X, Y = ox + ((len(row) - 1 - x) if flip else x), oy + y
                if 0 <= X < 16 and 0 <= Y < 16:
                    col = pal[ch]
                    if ch in "blv" and ch != "v": col = (max(0, min(255, col[0] + shift // 2)), max(0, min(255, col[1] + shift)), max(0, min(255, col[2] + shift // 2)), 255)
                    px[X, Y] = jitter(col, r, 5) if ch in "bl" else col
    im.save(OUT / "vine_leaves.png")


if __name__ == "__main__":
    planks(); beam(); screw(); pulp()
    grapes("grape_purple", c(38, 14, 82), c(88, 44, 146), c(158, 118, 208), 21)
    grapes("grape_white", c(112, 132, 52), c(184, 204, 104), c(236, 242, 178), 22)
    leaves()
    print("press and trellis textures written to", OUT)
