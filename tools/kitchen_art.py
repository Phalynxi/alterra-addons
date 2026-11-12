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


def planks():
    """Four horizontal boards with staggered joints, grain streaks and a nail at each end."""
    r = random.Random(11)
    base, light, dark, seam = c(124, 86, 50), c(146, 104, 62), c(98, 66, 38), c(54, 34, 18)
    im = Image.new("RGBA", (16, 16)); px = im.load()
    for i in range(4):
        joint = 5 if i % 2 == 0 else 11
        tone = r.choice((base, light, dark)) if False else base
        for y in range(4 * i, 4 * i + 4):
            for x in range(16):
                px[x, y] = seam if y == 4 * i + 3 else (light if y == 4 * i else base)
        for _ in range(7):                                    # grain
            y, x0, n = 4 * i + r.randint(0, 2), r.randint(0, 13), r.randint(2, 5)
            for x in range(x0, min(16, x0 + n)): px[x, y] = r.choice((dark, dark, light))
        for y in range(4 * i, 4 * i + 3): px[joint, y] = seam
        for x in (1, 14): px[x, 4 * i + 1] = c(150, 150, 156)  # nails
    im.save(OUT / "press_planks.png")


def beam():
    """Dark upright beam, grain running up and down, with two iron straps and rivets."""
    r = random.Random(12)
    base, light, dark = c(94, 63, 36), c(114, 79, 46), c(68, 44, 24)
    iron, ihi, ilo = c(78, 80, 88), c(132, 136, 146), c(46, 48, 54)
    im = Image.new("RGBA", (16, 16)); px = im.load()
    for x in range(16):
        col = r.choice((base, base, light, dark))
        for y in range(16): px[x, y] = jitter(col, r, 5)
    for y in range(16):                                        # a few darker knots/cracks along the grain
        if r.random() < .25: px[r.randint(0, 15), y] = dark
    for y0 in (3, 11):
        for x in range(16):
            px[x, y0], px[x, y0 + 1], px[x, y0 + 2] = ihi, iron, ilo
        for x in (1, 7, 14): px[x, y0 + 1] = ihi
    im.save(OUT / "press_beam.png")


def screw():
    """The screw: dark iron with a thread running round it (a soft bright row and a dark row every four rows)."""
    r = random.Random(13)
    iron, hi, lo = c(92, 94, 102), c(132, 136, 146), c(60, 62, 70)
    im = Image.new("RGBA", (16, 16)); px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = hi if y % 4 == 0 else lo if y % 4 == 2 else jitter(iron, r, 3)
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
    """Round berries in 2x2 steps (every other row shifted one pixel): a shine pixel top left, shade bottom right.
    The trellis clusters are only a couple of pixels wide, so this is mostly about colour and a hint of roundness."""
    r = random.Random(seed)
    im = Image.new("RGBA", (16, 16)); px = im.load()
    for by in range(8):
        off = (by % 2)
        for bx in range(-1, 9):
            x0, y0 = bx * 2 + off, by * 2
            b = jitter(base, r, 10)
            cells = {(0, 0): light, (1, 0): b, (0, 1): b, (1, 1): shade}
            for (dx, dy), col in cells.items():
                x, y = (x0 + dx) % 16, y0 + dy
                px[x, y] = col
    im.save(OUT / f"{name}.png")


LEAF = ["...vv...",    # one grape leaf, 8 wide, pointed lobes: v = vein and stalk, l = lit edge, b = body, d = shaded edge
        ".b.vb.b.",
        "bbbvbbbl",
        "lbbvbbb.",
        ".bbvbbd.",
        "dbbvbbb.",
        ".dbvbd..",
        "..dvd...",
        "...v...."]


def leaves():
    r = random.Random(16)
    pal = {"v": c(34, 74, 26), "l": c(112, 178, 66), "b": c(66, 132, 44), "d": c(44, 98, 32)}
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load()
    vine = c(52, 84, 30)
    for y in range(16): px[7 + (1 if y % 6 < 3 else 0), y] = vine     # the vine, winding a little
    spots = [(0, 0, False), (8, 3, True), (0, 8, True), (8, 11, False)]
    for ox, oy, flip in spots:
        for y, row in enumerate(LEAF):
            for x, ch in enumerate(row):
                if ch == ".": continue
                fx = (7 - x) if flip else x
                X, Y = ox + fx, oy + y
                if 0 <= X < 16 and 0 <= Y < 16:
                    col = pal[ch]
                    px[X, Y] = jitter(col, r, 6) if ch in "bl" else col
    im.save(OUT / "vine_leaves.png")


if __name__ == "__main__":
    planks(); beam(); screw(); pulp()
    grapes("grape_purple", c(38, 14, 82), c(88, 44, 146), c(158, 118, 208), 21)
    grapes("grape_white", c(112, 132, 52), c(184, 204, 104), c(236, 242, 178), 22)
    leaves()
    print("press and trellis textures written to", OUT)
