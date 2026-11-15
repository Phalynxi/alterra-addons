"""Makes Alterra Kitchen's foods: the list (alterra_kitchen_foods.json), a texture and a model for each, the recipes and the item tags.
Run: python3 tools/food_art.py   (after the core's pipeline has written the juices and press lists; needs Pillow)

Per fruit: jam, pie, muffin, dried fruit, bar, sorbet, slices, compote (eight foods), plus a handful of meals that take any fruit.
Everything here is hand-written output (the pipeline's split step does not touch these file names), and re-running replaces it.
Recipes use Farmer's Delight's cooking pot (jam, compote) and cutting board (slices) when that mod is loaded and the compat option is on;
every food also has a plain crafting or furnace recipe so nothing needs Farmer's Delight."""
import json, random, colorsys, sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "kitchen/src/main/resources"
ASSETS = ROOT / "assets/alterra_kitchen"
DATA = ROOT / "data/alterra_kitchen"
MOD = "alterra_kitchen"
(ASSETS / "textures/item").mkdir(parents=True, exist_ok=True); (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
import shutil
shutil.rmtree(DATA / "recipe/food", ignore_errors=True)
(DATA / "recipe/food").mkdir(parents=True, exist_ok=True); (DATA / "tags/item").mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------- colours
def rgb(n): return ((n >> 16) & 255, (n >> 8) & 255, n & 255)
def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def shade(c, f):   # f < 1 darker, > 1 lighter
    return tuple(max(0, min(255, int(v * f))) if f <= 1 else min(255, int(v + (255 - v) * (f - 1))) for v in c)
def punchy(c, s=1.15):   # fruit colours from the juice list are a little washed: bring the colour up
    h, l, sat = colorsys.rgb_to_hls(*(v / 255 for v in c))
    r, g, b = colorsys.hls_to_rgb(h, min(0.62, max(0.3, l)), min(1.0, sat * s))
    return (int(r * 255), int(g * 255), int(b * 255))
CRUST = (205, 150, 78); CRUST_D = (150, 98, 46); CRUST_L = (232, 186, 112)
OUTLINE = (58, 40, 30)

press = json.loads((ROOT / "alterra_kitchen_press.json").read_text())
juices = {j["id"]: j for j in json.loads((ROOT / "alterra_kitchen_juices.json").read_text())}
FRUITS = []   # (item id, name, colour)
for fruit, (juice, _n) in press.items():
    path = fruit.split(":")[1]
    if "golden" in path: continue
    key = juice.split(":")[1]
    col = punchy(rgb(juices[key]["color"])) if key in juices else (200, 80, 80)
    name = path.replace("baobab_pulp", "baobab").replace("_", " ").title()
    FRUITS.append((fruit, path.replace("baobab_pulp", "baobab"), name, col))

# ---------------------------------------------------------------- drawing
def canvas(): return Image.new("RGBA", (16, 16), (0, 0, 0, 0)), None
def put(px, x, y, col):
    if 0 <= x < 16 and 0 <= y < 16: px[x, y] = col + (255,) if len(col) == 3 else col

def outline(im, col=OUTLINE):
    """A dark edge round everything drawn (drawn into the transparent pixels next to it)."""
    px = im.load(); edge = []
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 0: edge.append((x, y)); break
    for x, y in edge: px[x, y] = col + (255,)
    return im

def ellipse(px, cx, cy, rx, ry, col, light=None, dark=None):
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            if d <= 1:
                c = col
                if light and d < 0.45 and (x + 0.5 - cx) + (y + 0.5 - cy) < 0: c = light
                if dark and (x + 0.5 - cx) + (y + 0.5 - cy) > rx * 0.5 and d > 0.5: c = dark
                put(px, x, y, c)

def rect(px, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1): put(px, x, y, col)

def seeded(name): return random.Random(sum(ord(ch) * (i + 3) for i, ch in enumerate(name)))

def jam(col, name):
    im, _ = canvas(); px = im.load(); r = seeded(name)
    glass = (186, 214, 224, 255); glass_l = (234, 246, 250, 255)
    lid = (170, 60, 52) if col[0] < 200 else (60, 110, 170)
    rect(px, 5, 1, 10, 3, lid); rect(px, 5, 1, 10, 1, shade(lid, 1.25)); rect(px, 5, 3, 10, 3, shade(lid, 0.75))
    for y in range(4, 15):
        for x in range(3, 13):
            wall = x in (3, 12) or y == 14
            put(px, x, y, glass if wall else (shade(col, 0.82) if x >= 10 else col))
    for y in range(5, 13): put(px, 5, y, shade(col, 1.3));
    for _ in range(4): put(px, r.randint(6, 9), r.randint(6, 12), shade(col, 0.7))
    put(px, 4, 5, glass_l); put(px, 4, 6, glass_l)
    rect(px, 4, 8, 11, 10, (240, 230, 200)); put(px, 7, 9, shade(col, 0.6)); put(px, 8, 9, shade(col, 0.6))   # a little paper label
    return outline(im)

def pie(col, name):
    im, _ = canvas(); px = im.load()
    ellipse(px, 8, 9, 7.2, 5.2, CRUST_D, CRUST_L, CRUST_D)   # the tin and rim
    ellipse(px, 8, 8.5, 6.2, 4.3, CRUST, CRUST_L, CRUST)
    ellipse(px, 8, 8.3, 4.6, 3.1, col, shade(col, 1.3), shade(col, 0.78))
    for i in range(-2, 4, 2):   # lattice strips on top
        for k in range(-4, 5):
            put(px, 8 + k, 8 + (k + i) // 2, CRUST_L) if abs(k) < 5 else None
    for x, y in ((6, 7), (10, 9), (8, 6)): put(px, x, y, shade(col, 0.7))
    return outline(im)

def muffin(col, name):
    im, _ = canvas(); px = im.load(); r = seeded(name)
    paper = (232, 214, 170); paper_d = (190, 166, 116)
    for y in range(10, 15):
        for x in range(4 + (y - 10) // 2 * -0, 12):
            inset = 1 if y >= 13 else 0
            if inset <= x - 4 <= 7 - inset: put(px, x, y, paper_d if (x % 2 == 0) else paper)
    ellipse(px, 8, 7, 6.2, 5.0, CRUST, CRUST_L, CRUST_D)
    rect(px, 3, 9, 12, 9, CRUST_D)
    for _ in range(6):
        x, y = r.randint(4, 11), r.randint(3, 8)
        put(px, x, y, col); put(px, x + 1, y, shade(col, 0.8))
    return outline(im)

def dried(col, name):
    im, _ = canvas(); px = im.load(); d = mix(shade(col, 0.55), (92, 58, 36), 0.45); l = shade(d, 1.35)
    for cx, cy, rx, ry in ((5, 10, 3.0, 2.4), (11, 9, 3.2, 2.6), (8, 5, 2.8, 2.2)):
        ellipse(px, cx, cy, rx, ry, d, l, shade(d, 0.7))
        put(px, int(cx), int(cy), shade(d, 0.7)); put(px, int(cx) - 1, int(cy) + 1, shade(d, 0.75))   # wrinkles
    return outline(im)

def bar(col, name):
    im, _ = canvas(); px = im.load(); r = seeded(name)
    oat = (214, 178, 112); oat_d = (170, 134, 78); oat_l = (236, 208, 150)
    for y in range(5, 12):
        for x in range(2, 14):
            px[x, y] = oat_l + (255,) if y == 5 else (oat_d + (255,) if y == 11 else oat + (255,))
    rect(px, 2, 7, 13, 8, shade(col, 0.95)); rect(px, 2, 7, 13, 7, shade(col, 1.2))   # a stripe of fruit glaze
    for _ in range(8): put(px, r.randint(2, 13), r.choice((6, 9, 10)), r.choice((oat_d, shade(col, 0.8), oat_l)))
    return outline(im)

def sorbet(col, name):
    im, _ = canvas(); px = im.load(); bowl = (214, 196, 166); bowl_d = (160, 140, 110)
    for y in range(10, 15):
        for x in range(3, 14):
            if (x - 3) >= max(0, y - 12) and (13 - x) >= max(0, y - 12): put(px, x, y, bowl_d if y == 14 or x in (3, 13) else bowl)
    pale = mix(col, (255, 255, 255), 0.45)
    ellipse(px, 6, 8, 3.4, 3.2, pale, shade(pale, 1.25), shade(pale, 0.85))
    ellipse(px, 10.5, 8, 3.4, 3.2, mix(col, (255, 255, 255), 0.25), shade(pale, 1.2), shade(col, 0.8))
    rect(px, 3, 10, 13, 10, bowl)
    put(px, 8, 4, (110, 170, 80)); put(px, 9, 3, (110, 170, 80)); put(px, 8, 3, (80, 130, 60))   # a mint leaf
    return outline(im)

def slices(col, name):
    im, _ = canvas(); px = im.load(); inner = mix(col, (255, 255, 235), 0.5)
    for cx, cy in ((6, 6), (10, 10)):
        ellipse(px, cx, cy, 4.4, 4.4, shade(col, 0.72))
        ellipse(px, cx, cy, 3.5, 3.5, inner, shade(inner, 1.2))
        for dx, dy in ((0, -2), (2, 1), (-2, 1)): put(px, int(cx) + dx, int(cy) + dy, shade(col, 0.95))   # segments
        put(px, int(cx), int(cy), shade(col, 1.2))
    return outline(im)

def compote(col, name):
    im, _ = canvas(); px = im.load(); r = seeded(name); bowl = (150, 112, 74); bowl_d = (104, 74, 46)
    for y in range(6, 14):
        for x in range(2, 14):
            if (x - 2) >= max(0, y - 10) and (13 - x) >= max(0, y - 10): put(px, x, y, bowl_d if y == 13 or x in (2, 13) else bowl)
    rect(px, 3, 6, 12, 8, shade(col, 0.85)); rect(px, 3, 6, 12, 6, shade(col, 1.15))
    for _ in range(5): put(px, r.randint(3, 12), r.randint(6, 8), shade(col, 1.35))
    rect(px, 2, 6, 2, 6, bowl); rect(px, 13, 6, 13, 6, bowl)
    return outline(im)

KINDS = [
    ("jam", "{n} Jam", jam, dict(nutrition=4, saturation=0.4, eat=1.6, returns="minecraft:glass_bottle", stack=16)),
    ("pie", "{n} Pie", pie, dict(nutrition=8, saturation=0.5, eat=2.0, stack=16)),
    ("muffin", "{n} Muffin", muffin, dict(nutrition=5, saturation=0.4, eat=1.2, stack=64)),
    ("dried", "Dried {n}", dried, dict(nutrition=3, saturation=0.35, eat=0.8, stack=64)),
    ("bar", "{n} Bar", bar, dict(nutrition=4, saturation=0.6, eat=1.0, stack=64)),
    ("sorbet", "{n} Sorbet", sorbet, dict(nutrition=5, saturation=0.45, eat=1.6, returns="minecraft:bowl", stack=16)),
    ("slices", "{n} Slices", slices, dict(nutrition=2, saturation=0.25, eat=0.8, stack=64)),
    ("compote", "{n} Compote", compote, dict(nutrition=6, saturation=0.55, eat=1.6, returns="minecraft:bowl", stack=16)),
]

# ---------------------------------------------------------------- meals (any fruit)
def bowl_base(px, inside, rim=(214, 196, 166)):
    d = shade(rim, 0.75)
    for y in range(7, 14):
        for x in range(2, 14):
            if (x - 2) >= max(0, y - 11) and (13 - x) >= max(0, y - 11): put(px, x, y, d if y == 13 or x in (2, 13) else rim)
    rect(px, 3, 7, 12, 8, inside)

def trail_mix():
    im, _ = canvas(); px = im.load(); r = seeded("trail"); bowl_base(px, (140, 100, 60))
    for _ in range(14): put(px, r.randint(3, 12), r.randint(5, 8), r.choice([(120, 60, 50), (232, 190, 80), (110, 70, 40), (190, 80, 120), (150, 170, 70), (230, 220, 190)]))
    return outline(im)
def orchard_salad():
    im, _ = canvas(); px = im.load(); r = seeded("salad"); bowl_base(px, (236, 226, 200))
    for _ in range(18): put(px, r.randint(3, 12), r.randint(4, 8), r.choice([(214, 70, 60), (240, 170, 60), (120, 170, 70), (150, 60, 120), (250, 220, 120), (240, 130, 80)]))
    return outline(im)
def fruit_porridge():
    im, _ = canvas(); px = im.load(); r = seeded("porridge"); bowl_base(px, (226, 204, 160), (170, 128, 84))
    for _ in range(5): put(px, r.randint(3, 12), r.randint(6, 8), (186, 70, 90));
    rect(px, 7, 5, 8, 6, (214, 70, 60)); put(px, 8, 4, (90, 150, 70))
    return outline(im)
def fruit_bread():
    im, _ = canvas(); px = im.load(); r = seeded("bread")
    for y in range(5, 13):
        for x in range(2, 14):
            c = CRUST_L if y == 5 else (CRUST_D if y == 12 or x in (2, 13) else CRUST)
            if y == 5 and x in (2, 13): continue
            put(px, x, y, c)
    for x in (5, 8, 11): put(px, x, 6, CRUST_D); put(px, x + 1, 7, CRUST_D)
    for _ in range(7): put(px, r.randint(3, 12), r.randint(7, 11), r.choice([(150, 50, 90), (210, 70, 50), (90, 50, 110)]))
    return outline(im)
def jam_toast():
    im, _ = canvas(); px = im.load()
    for y in range(3, 14):
        for x in range(3, 13):
            if (y == 3 and x in (3, 4, 11, 12)) or (y == 4 and x in (3, 12)): continue
            put(px, x, y, (226, 186, 120) if 4 <= x <= 11 and 4 <= y <= 12 else (180, 126, 66))
    ellipse(px, 8, 8.5, 3.6, 3.8, (190, 40, 70), (226, 90, 110), (140, 28, 56))
    return outline(im)
def berry_cookies():
    im, _ = canvas(); px = im.load(); r = seeded("cookie")
    for cx, cy in ((6, 6), (10, 10), (5, 11)):
        ellipse(px, cx, cy, 3.6, 3.4, (196, 140, 76), (224, 176, 108), (150, 100, 52))
        for _ in range(3): put(px, int(cx) + r.randint(-2, 1), int(cy) + r.randint(-2, 1), (140, 40, 90))
    return outline(im)

MEALS = [
    ("trail_mix", "Trail Mix", trail_mix, dict(nutrition=6, saturation=0.6, eat=1.2, returns="minecraft:bowl", stack=16)),
    ("orchard_salad", "Orchard Salad", orchard_salad, dict(nutrition=8, saturation=0.7, eat=1.6, returns="minecraft:bowl", stack=16)),
    ("fruit_porridge", "Fruit Porridge", fruit_porridge, dict(nutrition=9, saturation=0.8, eat=1.8, returns="minecraft:bowl", stack=16)),
    ("fruit_bread", "Fruit Bread", fruit_bread, dict(nutrition=7, saturation=0.6, eat=1.6, stack=64)),
    ("jam_toast", "Jam Toast", jam_toast, dict(nutrition=7, saturation=0.65, eat=1.4, stack=64)),
    ("berry_cookies", "Berry Cookies", berry_cookies, dict(nutrition=3, saturation=0.3, eat=0.8, stack=64)),
]

# ---------------------------------------------------------------- dishes from Alex's Mobs animals (recipes only when that mod is loaded)
MEAT = (150, 74, 48); MEAT_D = (104, 48, 32); MEAT_L = (196, 112, 76); BONE = (238, 230, 208)
def glazed_ribs():
    im, _ = canvas(); px = im.load()
    for y in range(4, 12):
        for x in range(3, 13):
            if (x in (3, 12) and y in (4, 11)): continue
            put(px, x, y, MEAT_L if y == 4 else (MEAT_D if y == 11 else MEAT))
    for x in (5, 8, 11): rect(px, x, 4, x, 11, MEAT_D)
    for x in range(4, 12, 2): put(px, x, 6, (200, 60, 90)); put(px, x + 1, 8, (200, 60, 90))   # fruit glaze
    for x in (3, 12): rect(px, x, 12, x, 14, BONE); put(px, x, 14, shade(BONE, 0.8))   # bone ends
    rect(px, 7, 12, 8, 14, BONE)
    return outline(im)
def skewer(meat=MEAT, fruit=(236, 170, 60)):
    im, _ = canvas(); px = im.load()
    for i in range(13): put(px, 2 + i, 13 - i, (150, 108, 62))
    for k, (cx, cy, col) in enumerate(((5, 10, meat), (8, 7, fruit), (11, 4, meat))):
        ellipse(px, cx, cy, 2.6, 2.6, col, shade(col, 1.25), shade(col, 0.75))
    return outline(im)
def stew(chunk, broth, name):
    im, _ = canvas(); px = im.load(); r = seeded(name); bowl_base(px, broth, (150, 112, 74))
    for _ in range(6): x, y = r.randint(3, 11), r.randint(6, 8); put(px, x, y, chunk); put(px, x + 1, y, shade(chunk, 0.8))
    for _ in range(3): put(px, r.randint(3, 12), r.randint(6, 8), (226, 140, 50))
    return outline(im)
def lobster():
    im, _ = canvas(); px = im.load(); red = (200, 62, 40); red_d = (146, 40, 28); red_l = (236, 110, 76)
    for i, y in enumerate(range(3, 13)):
        w = 5 - abs(i - 4) // 2
        for x in range(8 - w, 8 + w + 1): put(px, x, y, red_d if i % 3 == 2 else (red_l if x == 8 - w + 1 else red))
    rect(px, 5, 13, 10, 13, red_d); put(px, 4, 14, red); put(px, 11, 14, red)
    ellipse(px, 12, 11.5, 2.4, 2.4, (250, 190, 70), (255, 220, 120), (226, 150, 40))   # an orange slice
    return outline(im)
def catfish_dish():
    im, _ = canvas(); px = im.load(); fish = (176, 140, 98); sauce = (116, 40, 100)
    ellipse(px, 8, 9, 6.4, 3.4, fish, shade(fish, 1.25), shade(fish, 0.75))
    for x in range(12, 15): rect(px, x, 6 + (x - 12) * -1, x, 12 + (x - 12), fish)
    for x in range(4, 10, 2): put(px, x, 8, sauce); put(px, x + 1, 10, sauce); put(px, x, 9, shade(sauce, 1.3))
    put(px, 4, 8, (30, 30, 30)) 
    return outline(im)
def egg_pie():
    im, _ = canvas(); px = im.load()
    ellipse(px, 8, 9, 7.2, 5.2, CRUST_D, CRUST_L, CRUST_D); ellipse(px, 8, 8.5, 6.2, 4.3, CRUST, CRUST_L, CRUST)
    ellipse(px, 8, 8.3, 4.6, 3.1, (236, 218, 150), (250, 240, 190), (206, 178, 108))
    ellipse(px, 8, 8, 1.8, 1.6, (232, 150, 40), (250, 190, 80), (200, 110, 30))   # a yolk
    return outline(im)
def fried_fish():
    im, _ = canvas(); px = im.load(); g = (222, 168, 84)
    ellipse(px, 7, 8, 5.2, 3.0, g, shade(g, 1.25), shade(g, 0.72))
    for i in range(4): rect(px, 11 + i, 6 - i // 2 + (1 if i else 0) * 0, 11 + i, 10 + i // 2, shade(g, 0.9))
    rect(px, 2, 5, 3, 6, shade(g, 1.2)); put(px, 4, 7, (50, 30, 20))
    for x in (6, 8): put(px, x, 8, shade(g, 0.6))
    return outline(im)
def platter():
    im, _ = canvas(); px = im.load()
    ellipse(px, 8, 9, 7.6, 5.4, (214, 196, 166), (240, 226, 196), (170, 150, 120))
    ellipse(px, 5.5, 8, 2.4, 2.2, MEAT, MEAT_L, MEAT_D); ellipse(px, 10.5, 7.5, 2.4, 2.2, (186, 90, 52), (220, 130, 86), (130, 56, 34))
    ellipse(px, 8, 11, 2.2, 1.6, (200, 62, 40), (236, 110, 76), (146, 40, 28))
    for x, y in ((8, 7), (4, 11), (12, 10)): put(px, x, y, (120, 170, 70))
    return outline(im)
def lobster_salad():
    im, _ = canvas(); px = im.load(); r = seeded("lobsal"); bowl_base(px, (236, 226, 200))
    for _ in range(10): put(px, r.randint(3, 12), r.randint(4, 8), r.choice([(214, 70, 60), (240, 170, 60), (120, 170, 70)]))
    rect(px, 5, 3, 10, 5, (200, 62, 40)); rect(px, 5, 3, 10, 3, (236, 110, 76)); rect(px, 6, 5, 9, 5, (146, 40, 28))
    return outline(im)

# ---- hand-drawn sprites (rows of 16 characters; the legend maps a character to a colour)
def art(rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im, _ = canvas(); px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".": px[x, y] = pal[ch] + (255,)
    return im

def glazed_ribs():
    pal = {"o": OUTLINE, "m": (160, 78, 50), "M": (112, 52, 34), "L": (204, 120, 80), "g": (214, 62, 96), "G": (244, 120, 144), "b": (240, 232, 210), "B": (196, 186, 160)}
    return art([
        "................",
        "..oooooooooooo..",
        ".oLLLLLLLLLLLLo.",
        ".oLGgggLLGggLLo.",
        ".omggggmmggggmo.",
        ".omgmmmmmmmmmmo.",
        ".oMMomMMMomMMMo.",
        ".oMmomMmMomMmMo.",
        ".oMMomMMMomMMMo.",
        ".ooMoooMoooMooo.",
        "..obbo.obbo.obo.",
        "..obBo.obBo.oBo.",
        "..obBo.obBo.oBo.",
        "...oo...oo...o..",
        "................",
        "................"], pal)

def lobster():
    pal = {"o": OUTLINE, "r": (204, 64, 40), "R": (240, 118, 84), "d": (146, 40, 28), "w": (250, 226, 190), "y": (250, 190, 70), "Y": (226, 140, 40)}
    return art([
        "..oo........oo..",
        ".orro......orro.",
        ".orRro....orRro.",
        ".orrrro..orrrro.",
        "..orrrrooorrro..",
        "...orrrrrrrro...",
        "....orRrrRro....",
        "....odrrrrdo....",
        "....orRrrRro....",
        ".....odrrdo.....",
        ".....orrrro.....",
        "....odrRRrdo....",
        "...oRrrddrrRo...",
        "..orrRro.orRro..",
        "..oooo.....oooo.",
        "................"], pal)

def catfish_dish():
    pal = {"o": OUTLINE, "Y": (212, 160, 96), "L": (240, 200, 136), "D": (156, 108, 62), "e": (28, 24, 24), "k": (120, 86, 56), "s": (128, 44, 112), "S": (176, 86, 150)}
    return art([
        "................",
        "................",
        ".....oooooo.....",
        "...ooLLLLLLoo.oo",
        "..oLLLLLLLLLooYo",
        "okoLeLLLLLLYYYYo",
        "okoYYYYYYYYYYYoo",
        ".okoYYsYYsYYYYo.",
        "okooDYSYYSYYYDoo",
        "..ooDDYYYYYDDoYo",
        "....ooDDDDDDoooo",
        "......oooooo....",
        "................",
        "................",
        "................",
        "................"], pal)

def fried_fish():
    pal = {"o": OUTLINE, "g": (226, 170, 84), "L": (250, 214, 130), "d": (170, 112, 52), "D": (124, 76, 36), "e": (30, 24, 20)}
    return art([
        "................",
        "......oo........",
        ".....oLLoo......",
        "....oLLggLoo....",
        "...oLggggggLoo..",
        "..oLLLLLLLLLLooo",
        ".oLeLggggggggddo",
        "oLLLLggddgggddoo",
        ".ooLgggggggddoo.",
        "...ooDddddddoo..",
        ".....oDDDDDoo...",
        "......oooooo....",
        "................",
        "................",
        "................",
        "................"], pal)

# ---------------------------------------------------------------- pasta and noodles
PASTA = (238, 214, 140); PASTA_D = (196, 166, 92); PASTA_L = (252, 238, 184)
def raw_noodles():
    pal = {"o": OUTLINE, "p": PASTA, "d": PASTA_D, "l": PASTA_L, "t": (168, 60, 52)}
    return art([
        "................",
        "...oo.oo.oo.....",
        "..oppoppoppo....",
        "..oplopplopo....",
        ".oppoppoppoppo..",
        ".oplpppplpppdo..",
        ".opppoppopppdo..",
        ".otttttttttttto.",
        ".opppoppopppdo..",
        ".oplpoppoplpdo..",
        ".opppoppopppdo..",
        "..oppoppopppo...",
        "..ooooooooooo...",
        "................",
        "................",
        "................"], pal)
def raw_spaghetti():
    pal = {"o": OUTLINE, "p": PASTA, "d": PASTA_D, "l": PASTA_L, "t": (80, 112, 170)}
    return art([
        "......oo........",
        ".....oplo.......",
        ".....opdo.oo....",
        "....oplo.oplo...",
        "....opdo.opdo...",
        "...oplo.oplo....",
        "...opdooopdo....",
        "..otttttttto....",
        "..oplooploopdo..",
        "..opdooopdo.opo.",
        ".oplo.oplo..opdo",
        ".opdo.opdo..oppo",
        ".oplo.oplo..oooo",
        ".oooo.oooo......",
        "................",
        "................"], pal)
def cooked_pile(shine):
    def f():
        pal = {"o": OUTLINE, "p": PASTA, "d": PASTA_D, "l": PASTA_L}
        return art([
            "................",
            "................",
            "....oooooooo....",
            "..ooplpppplpoo..",
            ".oplppdpppppplo.",
            ".opppplpppdpppo.",
            "oppdppppplpppppo".replace("oppdppppplpppppo", "oplppdpppplppppo"),
            "opppplppdppppdpo",
            "oppdpppplpppppdo",
            ".oppplpppppdppo.",
            ".ooppdpppplppoo.",
            "...ooppddppoo...",
            ".....oooooo.....",
            "................",
            "................",
            "................"], pal)
    return f()
def macaroni():
    pal = {"o": OUTLINE, "p": PASTA, "d": PASTA_D, "l": PASTA_L, "h": (120, 90, 50)}
    return art([
        "................",
        "................",
        "....oooo........",
        "...oplppo..oooo.",
        "...opdhpo.oplpo.",
        "....ohpo..ophdo.",
        ".oooo.oo.oopddo.",
        "oplppo.ooplpoo..",
        "opdhpo.oplhpo...",
        ".ophpoo.opdpo...",
        ".oopdpo..ooo..oo",
        "...oplpo..oooplo",
        "....oooo..oplhpo",
        "..........oopdpo",
        "............ooo.",
        "................"], pal)

def noodle_bowl(sauce, name, topping=None):
    im, _ = canvas(); px = im.load(); r = seeded(name)
    rim = (236, 226, 208); d = shade(rim, 0.72)
    for y in range(8, 14):
        for x in range(2, 14):
            if (x - 2) >= max(0, y - 11) and (13 - x) >= max(0, y - 11): put(px, x, y, d if y == 13 or x in (2, 13) else rim)
    for y in range(4, 8):   # a heap of noodles in the sauce above the rim
        for x in range(3 + (7 - y) // 2, 13 - (7 - y) // 2):
            put(px, x, y, PASTA if (x + y) % 3 else PASTA_L)
    for _ in range(9): x, y = r.randint(3, 12), r.randint(5, 8); put(px, x, y, shade(sauce, 0.95)); put(px, min(15, x + 1), y, shade(sauce, 1.12))
    rect(px, 3, 8, 12, 8, shade(sauce, 0.85))
    if topping:
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)): put(px, 7 + dx, 3 + dy, topping)
        put(px, 7, 3, shade(topping, 1.3))
    return outline(im)

ALEX = [
    ("glazed_moose_ribs", "Glazed Moose Ribs", glazed_ribs, dict(nutrition=10, saturation=0.85, eat=2.0, stack=16)),
    ("kangaroo_skewer", "Kangaroo Skewer", skewer, dict(nutrition=8, saturation=0.8, eat=1.6, stack=16)),
    ("moose_stew", "Moose Stew", lambda: stew((120, 60, 44), (142, 92, 52), "moose"), dict(nutrition=11, saturation=0.85, eat=2.0, returns="minecraft:bowl", stack=16)),
    ("kangaroo_stew", "Kangaroo and Fruit Stew", lambda: stew((150, 70, 60), (170, 100, 56), "roo"), dict(nutrition=11, saturation=0.85, eat=2.0, returns="minecraft:bowl", stack=16)),
    ("citrus_lobster", "Citrus Lobster", lobster, dict(nutrition=10, saturation=0.8, eat=1.8, stack=16)),
    ("catfish_with_plum_sauce", "Catfish with Plum Sauce", catfish_dish, dict(nutrition=9, saturation=0.8, eat=1.8, stack=16)),
    ("emu_egg_pie", "Emu Egg Pie", egg_pie, dict(nutrition=10, saturation=0.7, eat=2.0, stack=16)),
    ("fried_flying_fish", "Fried Flying Fish", fried_fish, dict(nutrition=6, saturation=0.6, eat=1.2, stack=64)),
    ("hunters_platter", "Hunter's Platter", platter, dict(nutrition=14, saturation=0.9, eat=2.4, stack=16)),
    ("lobster_orchard_salad", "Lobster Orchard Salad", lobster_salad, dict(nutrition=12, saturation=0.85, eat=2.0, returns="minecraft:bowl", stack=16)),
]

# ---------------------------------------------------------------- write everything
def save(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(obj, indent=2))
FDC = [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}, {"type": "alterra:config", "option": "fdCooking"}]
FDB = [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}, {"type": "alterra:config", "option": "fdCutting"}]
def item(i): return {"item": i}
def result(i, n=1): return {"id": i, "count": n}

foods = []; jams = []; driedl = []; fruitl = ["minecraft:apple", "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:melon_slice"]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import treat_art
TREATS = treat_art.overrides(FRUITS)
TASTE_KIND = {"ice_cream": 84, "popsicle": 72, "jam": 62, "pie": 88, "muffin": 74, "dried": 55, "bar": 66, "sorbet": 80, "slices": 50, "compote": 76}
TASTE_ID = {"honey_ice_cream": 86, "chocolate_ice_cream": 88, "ice_cream_sandwich": 80, "sundae": 94, "trail_mix": 60, "orchard_salad": 72, "fruit_porridge": 70, "fruit_bread": 68, "jam_toast": 66, "berry_cookies": 72, "flatbread": 58, "bun": 52, "tortilla": 50,
            "cooked_noodles": 55, "cooked_spaghetti": 55, "cooked_macaroni": 55, "fried_flying_fish": 70, "glazed_moose_ribs": 90, "kangaroo_skewer": 82, "moose_stew": 88, "kangaroo_stew": 86,
            "citrus_lobster": 92, "catfish_with_plum_sauce": 84, "emu_egg_pie": 86, "hunters_platter": 98, "lobster_orchard_salad": 90}
SEASONING_IDS = {"salt", "smoked_salt", "ember_flakes", "frost_crystals", "sun_zest", "herb_salt", "kelp_salt", "cocoa_sugar", "autumn_spice", "tart_zest", "onion_salt", "glow_spice"}
BAKING_IDS = {"berry_cookies", "fruit_bread", "jam_toast", "flatbread", "bun", "tortilla"}
def group_of(fid, ingredient):
    """Which creative tab section a food belongs to."""
    if fid.endswith(("_jam", "_compote", "_dried", "_slices")): return "preserves"
    if fid.endswith(("_fruit_pie", "_muffin", "_bar")) or fid in BAKING_IDS: return "baking"
    if fid.endswith(("_ice_cream", "_soft_serve", "_popsicle", "_sorbet")) or fid in ("sundae", "ice_cream_sandwich"): return "frozen"
    if fid.endswith("_dust") or fid in SEASONING_IDS: return "seasonings"
    if ingredient or fid.startswith("cooked_"): return "pantry"
    return "meals"
def edge_dark(img):
    """A sprite that touches the edge of its 16x16 with a bright pixel looks cut off: draw those pixels in a dark outline tone instead."""
    img = img.copy(); px = img.load()
    for x, y in [(x, 0) for x in range(16)] + [(x, 15) for x in range(16)] + [(0, y) for y in range(16)] + [(15, y) for y in range(16)]:
        r, g, b, a = px[x, y]
        if a > 0 and (r + g + b) / 3 > 95: px[x, y] = (int(r * 0.32 + 8), int(g * 0.28 + 6), int(b * 0.34 + 14), a)
    return img
def add_food(fid, name, img, props, needs=None):
    img = TREATS.get(fid, img)   # the hand-built sprites of tools/treat_art.py take the place of the simpler ones
    if img is None: raise SystemExit(f'no sprite for {fid}')
    assert fid not in juices, f"food id {fid} is already a drink id (alterra_kitchen_juices.json): the game would refuse to start"
    img = edge_dark(img)
    img.save(ASSETS / f"textures/item/{fid}.png")
    save(ASSETS / f"models/item/{fid}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{fid}"}})
    if "nutrition" in props and "taste" not in props:
        props = dict(props, taste=TASTE_ID.get(fid, next((v for k, v in TASTE_KIND.items() if fid.endswith("_" + k) or fid.startswith(k + "_")), 55)))
    foods.append({"id": fid, "name": name, "group": group_of(fid, bool(props.get("ingredient"))), **props, **({"needs": needs} if needs else {})})
def recipe(rid, obj): save(DATA / f"recipe/food/{rid}.json", obj)

for fruit, path, name, col in FRUITS:
    fruitl.append(fruit)
    for kind, title, draw, props in KINDS:
        fid = f"{path}_fruit_pie" if kind == "pie" else f"{path}_{kind}"
        add_food(fid, title.format(n=name), draw(col, fid), props)
        if kind == "jam": jams.append(f"{MOD}:{fid}")
        if kind == "dried": driedl.append(f"{MOD}:{fid}")
    F = item(fruit); me = lambda k: f"{MOD}:{path}_{k}"
    recipe(f"{path}_jam", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [F, F, F, item("minecraft:sugar"), item("minecraft:glass_bottle")], "result": result(me("jam"))})
    recipe(f"{path}_jam_pot", {"neoforge:conditions": FDC, "type": "farmersdelight:cooking", "experience": 0.35, "container": {"id": "minecraft:glass_bottle"}, "recipe_book_tab": "misc",
                               "ingredients": [F, F, F, item("minecraft:sugar")], "result": result(me("jam"))})
    recipe(f"{path}_pie", {"type": "minecraft:crafting_shaped", "category": "food", "pattern": ["FFF", "SES", "WWW"],
                           "key": {"F": F, "S": item("minecraft:sugar"), "E": item("minecraft:egg"), "W": item("minecraft:wheat")}, "result": result(me("fruit_pie"))})
    recipe(f"{path}_muffin", {"type": "minecraft:crafting_shaped", "category": "food", "pattern": ["WFW", "SES"],
                              "key": {"F": F, "S": item("minecraft:sugar"), "E": item("minecraft:egg"), "W": item("minecraft:wheat")}, "result": result(me("muffin"), 2)})
    for kind_, t_, cook in (("smelting", "minecraft:smelting", 200), ("smoking", "minecraft:smoking", 100), ("campfire", "minecraft:campfire_cooking", 400)):
        recipe(f"{path}_dried_{kind_}", {"type": t_, "category": "food", "ingredient": F, "result": result(f"{MOD}:{path}_dried"), "experience": 0.1, "cookingtime": cook})
    recipe(f"{path}_bar", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [F, item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:honey_bottle")], "result": result(me("bar"), 3)})
    recipe(f"{path}_sorbet", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [F, F, item("minecraft:snowball"), item("minecraft:snowball"), item("minecraft:sugar"), item("minecraft:bowl")], "result": result(me("sorbet"))})
    recipe(f"{path}_slices", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [F], "result": result(me("slices"), 2)})
    recipe(f"{path}_slices_board", {"neoforge:conditions": FDB, "type": "farmersdelight:cutting", "ingredients": [F], "result": [{"item": result(me("slices"), 3)}],
                                    "tool": [{"type": "farmersdelight:item_ability", "action": "knife_dig"}, {"tag": "c:tools/knife"}]})
    recipe(f"{path}_compote", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [F, F, F, item("minecraft:sugar"), item("minecraft:bowl")], "result": result(me("compote"))})
    recipe(f"{path}_compote_pot", {"neoforge:conditions": FDC, "type": "farmersdelight:cooking", "experience": 0.4, "container": {"id": "minecraft:bowl"}, "recipe_book_tab": "meals",
                                   "ingredients": [F, F, F, item("minecraft:sugar")], "result": result(me("compote"))})

for fid, name, draw, props in MEALS: add_food(fid, name, draw(), props)
PROPS_BOWL = dict(nutrition=8, saturation=0.7, eat=1.8, returns="minecraft:bowl", stack=16)
add_food("noodles", "Noodles", raw_noodles(), dict(ingredient=True, stack=64)); add_food("spaghetti", "Spaghetti", raw_spaghetti(), dict(ingredient=True, stack=64)); add_food("macaroni", "Macaroni", macaroni(), dict(ingredient=True, stack=64))
# ---- cooked pasta, one picture each (they were the same oval), and a pie crust with a crimped rim
import math as _pm
def pasta_noodles():
    """A pile of long flat noodles: thin wavy strands with the dark gaps between them showing."""
    im, _ = canvas(); px = im.load(); light, mid = (252, 238, 172), (226, 192, 100)
    for k in range(6):
        y0 = 3 + 2 * k; ph = k * 1.9
        for x in range(1, 15):
            y = y0 + round(1.3 * _pm.sin(x * 0.75 + ph))
            put(px, x, y, light if (x + k) % 5 else mid)
    return outline(im)
def pasta_spaghetti():
    """A nest of spaghetti seen from above: one strand wound in a spiral, light and darker on alternate turns."""
    im, _ = canvas(); px = im.load(); cols = ((252, 238, 172), (230, 198, 108))
    t = 0.0
    while t < 6.5 * 2 * _pm.pi:
        r = 0.5 + t * 0.165; x, y = 8 + _pm.cos(t) * r, 8.5 + _pm.sin(t) * r * 0.8
        put(px, x, y, cols[int(t / (2 * _pm.pi)) % 2]); t += 0.2
    return outline(im)
def pasta_macaroni():
    """Elbow macaroni: five bent tubes lying in different directions, each with a dark hole at one end."""
    im, _ = canvas(); px = im.load(); light, mid, dark = (252, 236, 160), (230, 196, 96), (150, 112, 44)
    def elbow(cx, cy, rot):
        for a in range(0, 91, 6):
            ang = _pm.radians(a + rot)
            for r, col in ((1.0, mid), (1.8, light), (2.6, mid)): put(px, cx + _pm.cos(ang) * r, cy - _pm.sin(ang) * r, col)
        e = _pm.radians(rot); put(px, cx + _pm.cos(e) * 1.8, cy - _pm.sin(e) * 1.8, dark)
    for cx, cy, rot in ((3.5, 5, 0), (12.5, 4.5, 90), (3.5, 12.5, 270), (12.5, 12, 180), (8, 8.5, 45)): elbow(cx, cy, rot)
    return outline(im)
def pie_crust_sprite():
    im, _ = canvas(); px = im.load()
    ellipse(px, 8, 9, 7.4, 5.0, CRUST_D, CRUST_L, CRUST_D)   # the rim
    ellipse(px, 8, 8.8, 6.0, 3.8, (236, 198, 128), (250, 224, 164), (214, 168, 98))   # the flat middle
    for i, a in enumerate(range(0, 360, 20)):   # the crimps
        x, y = 8 + _pm.cos(_pm.radians(a)) * 7.0, 9 + _pm.sin(_pm.radians(a)) * 4.6
        put(px, x, y, CRUST_L if i % 2 else (176, 120, 56))
    for x, y in ((6, 8), (9, 10), (11, 8)): put(px, x, y, (206, 158, 90))   # a couple of pricked holes
    return outline(im)

add_food("cooked_noodles", "Cooked Noodles", pasta_noodles(), dict(nutrition=4, saturation=0.4, eat=1.2, stack=64)); add_food("cooked_spaghetti", "Cooked Spaghetti", pasta_spaghetti(), dict(nutrition=4, saturation=0.4, eat=1.2, stack=64))
add_food("cooked_macaroni", "Cooked Macaroni", pasta_macaroni(), dict(nutrition=4, saturation=0.4, eat=1.2, stack=64))

for fid, name, draw, props in ALEX: add_food(fid, name, draw(), props, "alexsmobs")

T = lambda t: {"tag": t}
FR, JAM, DR = T(f"{MOD}:fruits"), T(f"{MOD}:jams"), T(f"{MOD}:dried_fruits")
recipe("trail_mix", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [DR, DR, DR, item("minecraft:pumpkin_seeds"), item("minecraft:bowl")], "result": result(f"{MOD}:trail_mix")})
recipe("orchard_salad", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FR, FR, FR, FR, item("minecraft:bowl")], "result": result(f"{MOD}:orchard_salad")})
recipe("fruit_porridge", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FR, FR, item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:milk_bucket"), item("minecraft:bowl")], "result": result(f"{MOD}:fruit_porridge")})
recipe("fruit_bread", {"type": "minecraft:crafting_shaped", "category": "food", "pattern": ["WFW", "WSW"], "key": {"W": item("minecraft:wheat"), "F": FR, "S": item("minecraft:sugar")}, "result": result(f"{MOD}:fruit_bread")})
recipe("jam_toast", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:bread"), JAM], "result": result(f"{MOD}:jam_toast")})
recipe("berry_cookies", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:sugar"), T(f"{MOD}:berries")], "result": result(f"{MOD}:berry_cookies", 6)})


# recipes for the dishes: they need Alex's Mobs, so every one is conditional on it (the pot versions also on Farmer's Delight)
AM = [{"type": "neoforge:mod_loaded", "modid": "alexsmobs"}]
am = lambda i: item("alexsmobs:" + i)
def arecipe(rid, obj, pot=False): recipe(rid, {"neoforge:conditions": AM + (FDC if pot else []), **obj})
SH = lambda ings, out, n=1: {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": ings, "result": result(f"{MOD}:{out}", n)}
BOWL = item("minecraft:bowl")
arecipe("glazed_moose_ribs", SH([am("cooked_moose_ribs"), JAM], "glazed_moose_ribs"))
arecipe("kangaroo_skewer", SH([am("cooked_kangaroo_meat"), DR, item("minecraft:stick")], "kangaroo_skewer"))
arecipe("moose_stew", SH([am("cooked_moose_ribs"), item("minecraft:carrot"), item("minecraft:baked_potato"), BOWL], "moose_stew"))
arecipe("moose_stew_pot", {"type": "farmersdelight:cooking", "experience": 0.8, "container": {"id": "minecraft:bowl"}, "recipe_book_tab": "meals",
                           "ingredients": [am("moose_ribs"), item("minecraft:carrot"), item("minecraft:potato")], "result": result(f"{MOD}:moose_stew")}, pot=True)
arecipe("kangaroo_stew", SH([am("cooked_kangaroo_meat"), FR, item("minecraft:baked_potato"), BOWL], "kangaroo_stew"))
arecipe("kangaroo_stew_pot", {"type": "farmersdelight:cooking", "experience": 0.8, "container": {"id": "minecraft:bowl"}, "recipe_book_tab": "meals",
                              "ingredients": [am("kangaroo_meat"), FR, item("minecraft:potato")], "result": result(f"{MOD}:kangaroo_stew")}, pot=True)
arecipe("citrus_lobster", SH([am("cooked_lobster_tail"), item(f"{MOD}:orange_slices"), item("minecraft:sugar")], "citrus_lobster"))
arecipe("catfish_with_plum_sauce", SH([am("cooked_catfish"), item(f"{MOD}:plum_jam")], "catfish_with_plum_sauce"))
arecipe("emu_egg_pie", {"type": "minecraft:crafting_shaped", "category": "food", "pattern": ["FFF", "SES", "WWW"],
                        "key": {"F": FR, "S": item("minecraft:sugar"), "E": am("emu_egg"), "W": item("minecraft:wheat")}, "result": result(f"{MOD}:emu_egg_pie")})
for kind_, t_, cook in (("smelting", "minecraft:smelting", 200), ("smoking", "minecraft:smoking", 100), ("campfire", "minecraft:campfire_cooking", 400)):
    arecipe(f"fried_flying_fish_{kind_}", {"type": t_, "category": "food", "ingredient": am("flying_fish"), "result": result(f"{MOD}:fried_flying_fish"), "experience": 0.35, "cookingtime": cook})
arecipe("hunters_platter", SH([am("cooked_moose_ribs"), am("cooked_kangaroo_meat"), am("cooked_catfish"), am("cooked_lobster_tail"), item(f"{MOD}:orchard_salad")], "hunters_platter"))
arecipe("lobster_orchard_salad", SH([am("cooked_lobster_tail"), item(f"{MOD}:orchard_salad")], "lobster_orchard_salad"))

# ---- pasta: dough from Create or Farmer's Delight (or flour and an egg), cut or pressed, then cooked
CR = [{"type": "neoforge:mod_loaded", "modid": "create"}]; FDM = [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}]
def crec(rid, conds, obj): recipe(rid, {"neoforge:conditions": conds, **obj})
recipe("noodles_plain", SH2 := {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:egg")], "result": result(f"{MOD}:noodles", 4)})
recipe("spaghetti_plain", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:egg"), item("minecraft:stick")], "result": result(f"{MOD}:spaghetti", 4)})
recipe("macaroni_plain", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:egg"), item("minecraft:sugar")], "result": result(f"{MOD}:macaroni", 4)})
for dough, conds, tag in (("create:dough", CR, "create"), ("farmersdelight:wheat_dough", FDM, "fd")):
    crec(f"spaghetti_{tag}_dough", conds, {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(dough)], "result": result(f"{MOD}:spaghetti", 3)})
    crec(f"noodles_{tag}_dough", conds, {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(dough), item(dough)], "result": result(f"{MOD}:noodles", 8)})
crec("noodles_saw", CR, {"type": "create:cutting", "ingredients": [item("create:dough")], "processing_time": 50, "results": [{"id": f"{MOD}:noodles", "count": 4}]})
crec("macaroni_press", CR, {"type": "create:pressing", "ingredients": [item("create:dough")], "results": [{"id": f"{MOD}:macaroni", "count": 3}]})
for raw_, cooked_ in (("noodles", "cooked_noodles"), ("spaghetti", "cooked_spaghetti"), ("macaroni", "cooked_macaroni")):
    for kind_, t_, cook in (("smelting", "minecraft:smelting", 200), ("smoking", "minecraft:smoking", 100), ("campfire", "minecraft:campfire_cooking", 400)):
        recipe(f"{cooked_}_{kind_}", {"type": t_, "category": "food", "ingredient": item(f"{MOD}:{raw_}"), "result": result(f"{MOD}:{cooked_}"), "experience": 0.15, "cookingtime": cook})
    crec(f"{cooked_}_pot", FDC, {"type": "farmersdelight:cooking", "experience": 0.2, "recipe_book_tab": "meals", "ingredients": [item(f"{MOD}:{raw_}")], "result": result(f"{MOD}:{cooked_}")})


# ---------------------------------------------------------------- stale files from earlier versions of this script
def _stale():
    old = [f"{p}_noodles" for _f, p, _n, _c in FRUITS] + ["spaghetti_pomodoro", "berry_spaghetti", "creamy_macaroni", "jam_noodles", "orchard_noodle_soup", "spaghetti_and_ribs", "kangaroo_noodles", "lobster_noodles", "catfish_noodle_soup", "emu_egg_noodles"]
    for o in old:
        for p in (ASSETS / f"textures/item/{o}.png", ASSETS / f"models/item/{o}.json"): p.unlink(missing_ok=True)
_stale()

# ---------------------------------------------------------------- made-up dishes: a base, up to four fillings and a seasoning. The item is drawn in three layers (base, sauce and chunks tinted by the fillings, flecks tinted by the seasoning).
def gray(v): return (v, v, v)
def plate(px):
    ellipse(px, 8, 11, 7.6, 4.2, (226, 226, 220), (246, 246, 240), (178, 178, 172)); ellipse(px, 8, 10.6, 6.0, 3.2, (238, 238, 232))
def strands(px, x0, x1, y0, y1, seed, style):
    r = seeded(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if px[x, y][3] and (x + y * (2 if style else 1)) % 3 == 0: px[x, y] = shade(PASTA, 0.82) + (255,)
            elif px[x, y][3] and r.random() < 0.08: px[x, y] = PASTA_L + (255,)
def d_noodles(kind):
    base, _ = canvas(); bp = base.load(); over, _ = canvas(); op = over.load(); fl, _ = canvas(); fp = fl.load(); r = seeded("d" + kind)
    plate(bp)
    if kind == "macaroni":
        for cx, cy in ((5, 9), (9, 8), (12, 10), (7, 11), (10, 11), (4, 11)):
            for dx, dy in ((0, 0), (1, 0), (1, 1), (0, 1)): put(bp, cx + dx, cy + dy, PASTA_L if (dx + dy) % 2 == 0 else PASTA)
            put(bp, cx + 1, cy, shade(PASTA_D, 0.8))
        for cx, cy in ((6, 8), (10, 10), (8, 10), (4, 10)): ellipse(op, cx, cy, 1.8, 1.2, gray(225), gray(255), gray(185))
    else:
        ellipse(bp, 8, 8.5, 5.8 if kind == "noodles" else 5.0, 3.8 if kind == "noodles" else 3.4, PASTA, PASTA_L, PASTA_D)
        strands(bp, 2, 14, 5, 12, kind, kind == "spaghetti")
        ellipse(op, 8, 7.4, 3.8, 2.1, gray(228), gray(255), gray(180))
        if kind == "spaghetti":
            for cx, cy in ((6, 6), (10, 7)): ellipse(op, cx, cy, 1.5, 1.4, gray(205), gray(255), gray(150))
    for _ in range(5): put(fp, r.randint(4, 12), r.randint(5, 10), (255, 255, 255))
    for x in range(16):   # the flecks only where there is food
        for y in range(16):
            if fp[x, y][3] and not (bp[x, y][3] and bp[x, y][:3] != (226, 226, 220)): fp[x, y] = (0, 0, 0, 0)
    return outline(base), over, fl
def d_flatbread():
    base, _ = canvas(); bp = base.load(); over, _ = canvas(); op = over.load(); fl, _ = canvas(); fp = fl.load(); r = seeded("flat")
    ellipse(bp, 8, 9.5, 7.4, 5.0, CRUST_D, CRUST, CRUST_D); ellipse(bp, 8, 9.2, 6.2, 4.0, (236, 198, 132), (248, 220, 160), (216, 170, 100))
    for _ in range(7): put(bp, r.randint(3, 12), r.randint(6, 12), CRUST)
    ellipse(op, 8, 9, 5.2, 3.2, gray(225), gray(250), gray(185))
    for cx, cy in ((6, 8), (10, 9), (8, 11), (9, 7), (5, 10)): rect(op, cx, cy, cx + 1, cy, gray(255)); put(op, cx, cy + 1, gray(160))
    for _ in range(6): put(fp, r.randint(4, 12), r.randint(7, 11), (255, 255, 255))
    for x in range(16):
        for y in range(16):
            if fp[x, y][3] and not op[x, y][3]: fp[x, y] = (0, 0, 0, 0)
    return outline(base), over, fl
def d_sandwich():
    base, _ = canvas(); bp = base.load(); over, _ = canvas(); op = over.load(); fl, _ = canvas(); fp = fl.load(); r = seeded("sand")
    rect(bp, 2, 11, 13, 13, CRUST); rect(bp, 2, 13, 13, 13, CRUST_D); rect(bp, 2, 11, 13, 11, CRUST_L)
    ellipse(bp, 8, 5, 6.2, 3.0, CRUST_L, (244, 210, 150), CRUST); rect(bp, 2, 5, 13, 6, CRUST_L); rect(bp, 2, 6, 13, 6, CRUST_D)
    rect(bp, 3, 7, 12, 10, (222, 190, 130))
    for x in range(2, 14):
        o = 0 if x % 3 else 1
        for y in range(7 + o, 11): put(op, x, y, gray(236 if y < 10 else 176))
    for x in range(2, 14): put(op, x, 7 if x % 2 else 8, gray(255))
    for _ in range(4): put(fp, r.randint(4, 11), r.randint(2, 5), (255, 255, 255))
    return outline(base), over, fl
def d_potato():
    base, _ = canvas(); bp = base.load(); over, _ = canvas(); op = over.load(); fl, _ = canvas(); fp = fl.load(); r = seeded("pot")
    plate(bp)
    ellipse(bp, 8, 8.8, 6.6, 4.4, (150, 104, 60), (176, 130, 80), (110, 74, 40)); ellipse(bp, 8, 8.2, 5.0, 2.8, (246, 236, 196), (255, 250, 220), (222, 208, 160))
    ellipse(op, 8, 7.5, 3.8, 2.0, gray(230), gray(255), gray(180))
    for cx, cy in ((6, 7), (10, 8)): put(op, cx, cy, gray(255)); put(op, cx + 1, cy, gray(200))
    for _ in range(5): put(fp, r.randint(4, 12), r.randint(5, 9), (255, 255, 255))
    return outline(base), over, fl


# ---------------------------------------------------------------- the burger: a bun, one band per filling (each tinted by that filling), the top bun, flecks of seasoning
# The bun halves are adapted from Farmer's Delight's hamburger texture (MIT License, see CREDITS.md): cut into a top and a bottom half, with sesame seeds added.
BUN, BUN_L, BUN_D = (188, 137, 39), (216, 168, 64), (140, 102, 30)
FD_PAL = {"a": (101, 75, 23), "b": (188, 137, 39), "c": (162, 121, 36), "d": (140, 102, 30), "e": (63, 46, 14), "s": (246, 226, 170)}
TOP = ["......aaaa......",
       "....aabsbcdaa....".replace("....aabsbcdaa....", "....aabbcdaa...."),
       "...acbcsbbcda...",
       "..acsbbbdccdaa..",
       "..edcbbbccdaae..",
       "...eaddddaaae..."]
TOP[1] = "....aabsbcdaa..."
TOP[2] = "...acbcbsbcda..."
TOP[3] = "..acsbbsdccdaa.."
BOTTOM = ["...eeadddaaee...",
          ".....eeeeee....."]
# The burger: Farmer's Delight's hamburger sprite (MIT License, see CREDITS.md), cut into parts that the game tints separately:
# base (buns and dark edges), lettuce, tomato, patty and the slice under the bun, plus flecks of seasoning on the top bun.
FD_ROWS = ["................",
           "................",
           "......aaaa......",
           "....aabbcdaa....",
           "...acbcbbbcda...",
           "..acbbbbdccdaa..",
           "..edcbbbccdaae..",
           "..feaddddaaaef..",
           ".fghieeeeeefjgf.",
           ".ffihkkigjlgfff.",
           "..mgiiinglgopm..",
           "..aqorrpffpmqa..",
           "..eapqqqqqqpae..",
           "...eeadddaaee...",
           ".....eeeeee.....",
           "................"]
FD_COL = {"a": (101, 75, 23), "b": (188, 137, 39), "c": (162, 121, 36), "d": (140, 102, 30), "e": (63, 46, 14), "f": (0, 80, 0), "g": (60, 113, 62), "h": (148, 30, 51), "i": (99, 32, 58),
          "j": (77, 140, 59), "k": (192, 52, 31), "l": (122, 178, 68), "m": (78, 39, 25), "n": (142, 82, 60), "o": (103, 55, 40), "p": (63, 33, 22), "q": (42, 22, 13), "r": (82, 47, 31)}
PARTS = {"lettuce": "fgjl", "tomato": "hik", "patty": "mnopqr"}   # which colours of their sprite belong to which part
DEFAULT = {"lettuce": (122, 178, 68), "tomato": (192, 52, 31), "patty": (142, 82, 60), "slice": (63, 46, 14)}   # the colour that gives back their picture
def luma(c): return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
def burger_assets():
    for i, row in enumerate(FD_ROWS): assert len(row) == 16, (i, row)
    base, _ = canvas(); bp = base.load(); layers = {k: canvas()[0] for k in ("lettuce", "tomato", "patty", "slice")}; lp = {k: v.load() for k, v in layers.items()}
    for y, row in enumerate(FD_ROWS):
        for x, ch in enumerate(row):
            if ch == ".": continue
            part = next((k for k, cs in PARTS.items() if ch in cs), None)
            if y == 8 and 5 <= x <= 10: part = "slice"   # the dark strip under the top bun: a slice of cheese or sauce when something fits
            if ch in "bcd" and y == 7: part = None
            if part is None:
                if ch == "f" and y == 7: part = "lettuce"   # the leaf tips at the edge of the bun
                else: bp[x, y] = FD_COL[ch] + (255,); continue
            ref = DEFAULT[part] if part != "slice" else (255, 255, 255)
            top = max((FD_COL[c] for c in PARTS.get(part, "e")), key=luma) if part != "slice" else (63, 46, 14)
            g = max(0, min(255, int(255 * luma(FD_COL[ch]) / luma(top)))) if part != "slice" else 255
            lp[part][x, y] = (g, g, g, 255)
    base.save(ASSETS / "textures/item/dish_burger.png")
    for k, im in layers.items(): im.save(ASSETS / f"textures/item/dish_burger_{k}.png")
    fl, _ = canvas(); fp = fl.load(); r = seeded("burger flecks")
    for _ in range(6): put(fp, r.randint(5, 10), r.randint(3, 6), (255, 255, 255))
    fl.save(ASSETS / "textures/item/dish_burger_flecks.png")
def bun_item():
    im, _ = canvas(); px = im.load(); r = seeded("bunitem")
    ellipse(px, 8, 9, 7.0, 5.0, BUN_D, BUN_L, BUN_D); ellipse(px, 8, 8.2, 6.0, 3.8, BUN, BUN_L, BUN)
    for _ in range(7): put(px, r.randint(4, 11), r.randint(5, 10), (252, 240, 206))
    return outline(im)


# ---- dishes adapted from Farmer's Delight's textures (MIT License, see CREDITS.md): their sprite is cut into colour zones; the game tints each zone with one of the fillings
ZONE_SRC = {
 "wrap": (["................", "................", ".........aaaa...", ".......aabcdca..", ".....aabcdddbca.", "...aabacdbddcbe.", "..abdcbbdcccbbe.", ".abfgacacdcbbae.", ".aghiijadcbbae..", ".efihkhadabae...", ".lmnkjoecbae....", "..lfopecaae.....", "...lleaaee......", ".....lee........", "................", "................"],
          {"a": (140, 102, 30), "b": (179, 147, 108), "c": (224, 190, 125), "d": (246, 219, 168), "e": (101, 75, 23), "f": (77, 140, 59), "g": (60, 113, 62), "h": (124, 64, 47), "i": (82, 47, 31), "j": (107, 58, 42), "k": (163, 112, 90), "l": (0, 80, 0), "m": (122, 178, 68), "n": (136, 79, 64), "o": (150, 134, 116), "p": (183, 171, 155)},
          {"lettuce": "fglm", "meat": "hijkn", "sauce": "op"}),
 "rice_bowl": (["................", "................", "................", ".......aa.......", "......bcba......", ".....adedfa.....", "...gchcbfijkg...", "..lakhmcikjman..", "..logjbfabhgon..", "..lpqooooooqqn..", "...rpppppppqn...", "..icnnqppqnn....", "...g.icgnn......", ".....gg.........", "................", "................"],
          {"a": (183, 171, 155), "b": (207, 198, 187), "c": (233, 226, 217), "d": (122, 178, 68), "e": (248, 241, 233), "f": (77, 140, 59), "g": (100, 90, 78), "h": (255, 142, 9), "i": (164, 164, 164), "j": (233, 177, 21), "k": (241, 210, 67), "l": (63, 43, 11), "m": (211, 106, 13), "n": (31, 21, 2), "o": (58, 39, 6), "p": (113, 79, 15), "q": (83, 57, 9), "r": (48, 33, 9)},
          {"green": "df", "warm": "hjkm"}),
 "pie": (["................", "................", "................", "................", ".....aaaaaa.....", "...aabbbbccaa...", "..acbdefecedca..", ".acefcdcdecedca.", ".acfddbefebdecg.", ".gcehhcdcdecfbg.", ".gijkhjceffbbag.", ".gakjgacbbbiaag.", "..ggg.acicicag..", "......acciagg...", ".......gggg.....", "................"],
          {"a": (171, 119, 92), "b": (254, 204, 126), "c": (234, 181, 99), "d": (216, 139, 80), "e": (193, 72, 54), "f": (148, 30, 51), "g": (119, 86, 31), "h": (185, 105, 19), "i": (213, 159, 75), "j": (197, 133, 62), "k": (224, 175, 121)},
          {"filling": "efh"}),
 "skewer": (["................", ".............ab.", "..........cccdb.", ".........cefec..", "........ghijikc.", ".......glmhikcc.", ".......gnlnhhh..", ".....oopgggq....", "....orsroqq.....", "..tuuvsso.......", "..wwvxtrro......", "...ywvwoo.......", "..adww..........", ".adb............", ".bb.............", "................"],
          {"a": (73, 54, 21), "b": (40, 30, 11), "c": (78, 39, 25), "d": (137, 103, 39), "e": (142, 82, 60), "f": (103, 55, 40), "g": (148, 30, 51), "h": (42, 22, 13), "i": (82, 47, 31), "j": (152, 92, 67), "k": (113, 63, 45), "l": (255, 128, 115), "m": (207, 77, 59), "n": (192, 52, 31), "o": (139, 68, 32), "p": (172, 93, 49), "q": (99, 32, 58), "r": (222, 157, 123), "s": (230, 190, 164), "t": (121, 104, 84), "u": (233, 195, 150), "v": (150, 134, 116), "w": (100, 90, 78), "x": (183, 171, 155), "y": (113, 79, 15)},
          {"meat": "cefijkhopq", "red": "glmn", "pale": "rstuvwx"}),
}
def _hsv(c): return colorsys.rgb_to_hsv(*(v / 255 for v in c))
def auto_zones(pal):
    """Sorts the colours of a sprite into zones by hue: green, red, brown, gold get tinted by fillings; pale, grey and dark colours (starch, plates, edges) stay as they are."""
    zones = {"green": "", "red": "", "brown": "", "gold": ""}
    for ch, c in pal.items():
        h, sat, v = _hsv(c); h *= 360
        if sat < 0.2 or v < 0.2: continue
        if 60 <= h <= 170 and sat >= 0.25: zones["green"] += ch
        elif (h < 15 or h > 330) and sat >= 0.45 and v >= 0.35: zones["red"] += ch
        elif 10 <= h <= 50 and v < 0.62: zones["brown"] += ch
        elif 15 <= h <= 65 and v >= 0.62 and sat >= 0.35: zones["gold"] += ch
    return {k: v for k, v in zones.items() if v}
_fd = json.loads((Path(__file__).resolve().parent / "fd_dish_sprites.json").read_text())
AUTO = {"sushi": "kelp_roll", "dumplings": "dumplings", "rolls": "cabbage_rolls", "spaghetti": "pasta_with_meatballs", "noodles": "vegetable_noodles", "macaroni": "pasta_with_mutton_chop", "stuffed_potato": "stuffed_potato"}
for kind_, src_ in AUTO.items():
    pal_ = {k: tuple(v) for k, v in _fd[src_]["pal"].items()}
    ZONE_SRC[kind_] = (_fd[src_]["rows"], pal_, auto_zones(pal_))

def zone_assets(kind):
    """Writes dish_<kind> (what is not tinted), dish_<kind>_<zone> (grey shapes the game tints) and dish_<kind>_flecks, and the model. Returns the zone names in layer order."""
    rows, pal, zones = ZONE_SRC[kind]
    for r_ in rows: assert len(r_) == 16, (kind, r_)
    base, _ = canvas(); bp = base.load(); layers = {z: canvas()[0] for z in zones}; lp = {z: v.load() for z, v in layers.items()}
    tops = {z: max((pal[c] for c in cs), key=luma) for z, cs in zones.items()}
    opaque = []
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".": continue
            opaque.append((x, y))
            z = next((z for z, cs in zones.items() if ch in cs), None)
            if z is None: bp[x, y] = pal[ch] + (255,)
            else:
                g = max(0, min(255, int(255 * luma(pal[ch]) / luma(tops[z])))); lp[z][x, y] = (g, g, g, 255)
    base.save(ASSETS / f"textures/item/dish_{kind}.png")
    tex = {"layer0": f"{MOD}:item/dish_{kind}"}
    for i, (z, im) in enumerate(layers.items()):
        im.save(ASSETS / f"textures/item/dish_{kind}_{z}.png"); tex[f"layer{i + 1}"] = f"{MOD}:item/dish_{kind}_{z}"
    fl, _ = canvas(); fp = fl.load(); r = seeded(kind + "fl")
    top = sorted(opaque, key=lambda t: t[1])[: max(8, len(opaque) // 2)]
    for _ in range(6): x, y = r.choice(top); fp[x, y] = (255, 255, 255, 255)
    fl.save(ASSETS / f"textures/item/dish_{kind}_flecks.png"); tex[f"layer{len(layers) + 1}"] = f"{MOD}:item/dish_{kind}_flecks"
    return list(layers), [tops[z] for z in layers]
def builtin_model(kind):
    """The dishes are drawn by our own renderer from a texture made when the dish is first shown (see DishLook), so the model only gives the item's size and place in hand, in the gui and on the ground."""
    save(ASSETS / f"models/item/dish_{kind}.json", {"parent": "minecraft:builtin/entity", "gui_light": "front", "textures": {"particle": f"{MOD}:item/dish_{kind}"}, "display": {
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}, "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]}, "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}, "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]}}})

DISHES = [("noodles", "Noodles", 4, None), ("spaghetti", "Spaghetti", 4, None), ("macaroni", "Macaroni", 4, None), ("flatbread", "Flatbread", 4, d_flatbread), ("burger", "Burger", 6, None),
          ("stuffed_potato", "Stuffed Potato", 5, None), ("wrap", "Wrap", 5, None), ("rice_bowl", "Rice Bowl", 6, None), ("pie", "Pie", 7, None), ("skewer", "Skewer", 4, None),
          ("sushi", "Sushi Roll", 5, None), ("dumplings", "Dumplings", 5, None), ("rolls", "Cabbage Rolls", 5, None)]
dish_info = []
for kind, title, nut, draw in DISHES:
    if kind == "burger":
        burger_assets(); builtin_model("burger")
        dish_info.append({"id": "dish_burger", "name": title, "nutrition": nut, "zones": 4, "zoneNames": ["lettuce", "tomato", "patty", "slice"], "defaults": [(122 << 16) | (178 << 8) | 68, (192 << 16) | (52 << 8) | 31, (142 << 16) | (82 << 8) | 60, (63 << 16) | (46 << 8) | 14]}); continue
    if kind in ZONE_SRC:
        zs, tops_ = zone_assets(kind); builtin_model(kind)
        dish_info.append({"id": f"dish_{kind}", "name": title, "nutrition": nut, "zones": len(zs), "zoneNames": zs, "defaults": [(t[0] << 16) | (t[1] << 8) | t[2] for t in tops_]}); continue
    b, o, f = draw()
    b.save(ASSETS / f"textures/item/dish_{kind}.png"); o.save(ASSETS / f"textures/item/dish_{kind}_sauce.png"); f.save(ASSETS / f"textures/item/dish_{kind}_flecks.png")
    save(ASSETS / f"models/item/dish_{kind}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/dish_{kind}", "layer1": f"{MOD}:item/dish_{kind}_sauce", "layer2": f"{MOD}:item/dish_{kind}_flecks"}})
    builtin_model(kind)
    dish_info.append({"id": f"dish_{kind}", "name": title, "nutrition": nut, "zones": 1, "zoneNames": ["sauce"], "defaults": [(230 << 16) | (190 << 8) | 120]})
save(ROOT / "alterra_kitchen_dishes.json", dish_info)

# the flatbread itself (baked dough), and the seasonings
def flat_item():
    im, _ = canvas(); px = im.load(); r = seeded("fb")
    ellipse(px, 8, 9, 7.2, 5.0, CRUST_D, CRUST, CRUST_D); ellipse(px, 8, 8.8, 6.0, 3.9, (240, 204, 140), (252, 228, 168), (220, 176, 108))
    for _ in range(6): put(px, r.randint(3, 12), r.randint(6, 11), (190, 126, 66))
    return outline(im)
def heap(col, seed, crystals=False):
    im, _ = canvas(); px = im.load(); r = seeded(seed)
    ellipse(px, 8, 11, 6.8, 3.0, shade(col, 0.72), None, shade(col, 0.6))
    for y in range(4, 13):
        w = max(0, (y - 3) * 0.75)
        for x in range(int(8 - w), int(8 + w) + 1):
            if 0 <= x < 16 and y < 13: put(px, x, y, shade(col, 1.0 + 0.1 * ((x + y) % 3 == 0) - 0.12 * (x > 8)))
    for _ in range(9): put(px, r.randint(4, 11), r.randint(6, 12), shade(col, 1.35))
    if crystals:
        for x, y in ((6, 8), (9, 6), (8, 10), (10, 9)): put(px, x, y, (255, 255, 255))
    return outline(im)
SEASON = [("salt", "Salt", (236, 236, 232), True), ("smoked_salt", "Smoked Salt", (168, 134, 108), True), ("ember_flakes", "Ember Flakes", (200, 56, 36), False),
          ("frost_crystals", "Frost Crystals", (150, 214, 238), True), ("sun_zest", "Sun Zest", (244, 196, 64), False), ("herb_salt", "Herb Salt", (150, 180, 130), True),
          ("kelp_salt", "Kelp Salt", (96, 140, 110), True), ("cocoa_sugar", "Cocoa Sugar", (122, 78, 52), False), ("berry_dust", "Berry Dust", (170, 60, 110), False),
          ("autumn_spice", "Autumn Spice", (190, 110, 50), False), ("tart_zest", "Tart Zest", (160, 212, 80), False), ("onion_salt", "Onion Salt", (222, 196, 160), True), ("glow_spice", "Glow Spice", (250, 210, 100), True)]
add_food("bun", "Bun", bun_item(), dict(nutrition=3, saturation=0.4, eat=1.0, stack=64))
def tortilla_item():
    im, _ = canvas(); px = im.load(); r = seeded("tort")
    ellipse(px, 8, 9, 7.2, 4.6, (214, 170, 100), (240, 206, 140), (176, 128, 64)); ellipse(px, 8, 8.8, 6.0, 3.6, (236, 204, 142), (250, 228, 170), (216, 180, 116))
    for _ in range(5): put(px, r.randint(3, 12), r.randint(7, 11), (190, 140, 76))
    return outline(im)
def crust_item():
    im, _ = canvas(); px = im.load()
    ellipse(px, 8, 9, 7.2, 4.8, CRUST_D, CRUST_L, CRUST_D); ellipse(px, 8, 8.8, 6.0, 3.6, CRUST, CRUST_L, CRUST)
    for x in range(2, 14): put(px, x, 6, CRUST_L) if x % 2 == 0 else None
    return outline(im)
add_food("tortilla", "Tortilla", tortilla_item(), dict(nutrition=3, saturation=0.35, eat=1.0, stack=64))
add_food("pastry_crust", "Pie Crust", pie_crust_sprite(), dict(ingredient=True, stack=64))
add_food("flatbread", "Flatbread", flat_item(), dict(nutrition=4, saturation=0.45, eat=1.2, stack=64))
for _f, _p, _n, _c in FRUITS: SEASON.append((f"{_p}_dust", f"{_n} Dust", mix(_c, (255, 255, 255), 0.15), False))
for fid, title, col, cr in SEASON: add_food(fid, title, heap(col, fid, cr), dict(ingredient=True, stack=64))

# rock salt: stone with crystals, found underground (the veins come from the worldgen files below)
def rock_salt():
    r = random.Random(41); im = Image.new("RGBA", (16, 16)); px = im.load()
    for y in range(16):
        for x in range(16):
            v = r.randint(-9, 9); px[x, y] = (122 + v, 120 + v, 118 + v, 255)
    for _ in range(6):
        cx, cy, rad = r.randint(1, 14), r.randint(1, 14), r.uniform(1.6, 3.2)
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 * 1.3 <= rad * rad:
                    t = r.random(); px[x, y] = (244, 238, 232, 255) if t < 0.6 else ((232, 196, 190, 255) if t < 0.85 else (206, 214, 226, 255))
    return im
(ASSETS / "textures/block").mkdir(parents=True, exist_ok=True); (ASSETS / "models/block").mkdir(parents=True, exist_ok=True); (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
rock_salt().save(ASSETS / "textures/block/rock_salt.png")
save(ASSETS / "blockstates/rock_salt.json", {"variants": {"": {"model": f"{MOD}:block/rock_salt"}}})
save(ASSETS / "models/block/rock_salt.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/rock_salt"}})
save(ASSETS / "models/item/rock_salt.json", {"parent": f"{MOD}:block/rock_salt"})
(DATA / "loot_table/blocks").mkdir(parents=True, exist_ok=True)
save(DATA / "loot_table/blocks/rock_salt.json", {"type": "minecraft:block", "random_sequence": f"{MOD}:blocks/rock_salt", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
    {"type": "minecraft:item", "name": f"{MOD}:rock_salt", "conditions": [{"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
    {"type": "minecraft:item", "name": f"{MOD}:salt", "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}}, {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}, {"function": "minecraft:explosion_decay"}]}]}]}]})
(DATA.parent / "minecraft/tags/block/mineable").mkdir(parents=True, exist_ok=True)
save(DATA.parent / "minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"{MOD}:rock_salt", f"{MOD}:stove", f"{MOD}:oven", f"{MOD}:ice_machine", {"id": f"{MOD}:powered_ice_machine", "required": False}]})
save(DATA.parent / "minecraft/tags/block/mineable/axe.json", {"replace": False, "values": [f"{MOD}:kitchen_counter", f"{MOD}:prep_table", f"{MOD}:ice_cream_stand"]})
for d_ in ("configured_feature", "placed_feature"): (DATA / "worldgen" / d_).mkdir(parents=True, exist_ok=True)
(DATA / "neoforge/biome_modifier").mkdir(parents=True, exist_ok=True)
state = {"Name": f"{MOD}:rock_salt"}
save(DATA / "worldgen/configured_feature/rock_salt.json", {"type": "minecraft:ore", "config": {"size": 14, "discard_chance_on_air_exposure": 0.0, "targets": [
    {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:base_stone_overworld"}, "state": state},
    {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:sandstone"}, "state": state},
    {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:red_sandstone"}, "state": state}]}})
save(DATA / "worldgen/placed_feature/rock_salt.json", {"feature": f"{MOD}:rock_salt", "placement": [{"type": "minecraft:count", "count": 6}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -16}, "max_inclusive": {"absolute": 72}}}, {"type": "minecraft:biome"}]})
save(DATA / "neoforge/biome_modifier/rock_salt.json", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": f"{MOD}:rock_salt", "step": "underground_ores"})

# recipes: flatbread from dough (Create, Farmer's Delight or plain), smoked salt, the flaked seasonings
recipe("flatbread_plain", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:water_bucket")], "result": result(f"{MOD}:flatbread", 2)})
for dough_, conds_, tag_ in (("create:dough", [{"type": "neoforge:mod_loaded", "modid": "create"}], "create"), ("farmersdelight:wheat_dough", [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}], "fd")):
    for kind_, t_, cook in (("smelting", "minecraft:smelting", 200), ("smoking", "minecraft:smoking", 100), ("campfire", "minecraft:campfire_cooking", 400)):
        recipe(f"flatbread_{tag_}_{kind_}", {"neoforge:conditions": conds_, "type": t_, "category": "food", "ingredient": item(dough_), "result": result(f"{MOD}:flatbread"), "experience": 0.2, "cookingtime": cook})
for kind_, t_, cook in (("smoking", "minecraft:smoking", 100), ("campfire", "minecraft:campfire_cooking", 300)):
    recipe(f"smoked_salt_{kind_}", {"type": t_, "category": "food", "ingredient": item(f"{MOD}:salt"), "result": result(f"{MOD}:smoked_salt"), "experience": 0.1, "cookingtime": cook})
recipe("ember_flakes", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [T(f"{MOD}:ember_fruits_dried")], "result": result(f"{MOD}:ember_flakes", 2)})
recipe("frost_crystals", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [T(f"{MOD}:frost_fruits_dried"), item(f"{MOD}:salt")], "result": result(f"{MOD}:frost_crystals", 2)})
recipe("sun_zest", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [T(f"{MOD}:sun_fruits_dried")], "result": result(f"{MOD}:sun_zest", 2)})
recipe("herb_salt", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:salt"), item("minecraft:fern"), item("minecraft:fern")], "result": result(f"{MOD}:herb_salt", 2)})
recipe("bun_plain", {"type": "minecraft:crafting_shaped", "category": "food", "pattern": ["WSW"], "key": {"W": item("minecraft:wheat"), "S": item("minecraft:sugar")}, "result": result(f"{MOD}:bun", 2)})
for dough_, conds_, tag_ in (("create:dough", [{"type": "neoforge:mod_loaded", "modid": "create"}], "create"), ("farmersdelight:wheat_dough", [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}], "fd")):
    for kind_, t_, cook in (("smelting", "minecraft:smelting", 200), ("smoking", "minecraft:smoking", 100), ("campfire", "minecraft:campfire_cooking", 400)):
        pass
    recipe(f"bun_{tag_}_dough", {"neoforge:conditions": conds_, "type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(dough_), item("minecraft:sugar")], "result": result(f"{MOD}:bun", 2)})
recipe("kelp_salt", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:salt"), item("minecraft:dried_kelp")], "result": result(f"{MOD}:kelp_salt", 2)})
recipe("cocoa_sugar", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:cocoa_beans"), item("minecraft:sugar")], "result": result(f"{MOD}:cocoa_sugar", 2)})
recipe("berry_dust", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [T(f"{MOD}:berries_dried"), item("minecraft:sugar")], "result": result(f"{MOD}:berry_dust", 2)})
recipe("autumn_spice", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:pumpkin_seeds"), item("minecraft:cocoa_beans"), item("minecraft:sugar")], "result": result(f"{MOD}:autumn_spice", 3)})
recipe("tart_zest", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [T(f"{MOD}:tart_fruits_dried")], "result": result(f"{MOD}:tart_zest", 2)})
recipe("onion_salt", {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}], "type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:salt"), item("farmersdelight:onion")], "result": result(f"{MOD}:onion_salt", 2)})
recipe("glow_spice", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:glow_berries"), item("minecraft:sugar"), item(f"{MOD}:salt")], "result": result(f"{MOD}:glow_spice", 2)})
recipe("tortilla_plain", {"type": "minecraft:crafting_shaped", "category": "food", "pattern": ["WW"], "key": {"W": item("minecraft:wheat")}, "result": result(f"{MOD}:tortilla", 2)})
recipe("pie_crust_plain", {"type": "minecraft:crafting_shaped", "category": "food", "pattern": ["WWW", "SES"], "key": {"W": item("minecraft:wheat"), "S": item("minecraft:sugar"), "E": item("minecraft:egg")}, "result": result(f"{MOD}:pastry_crust", 2)})
for dough_, conds_, tag_ in (("create:dough", [{"type": "neoforge:mod_loaded", "modid": "create"}], "create"), ("farmersdelight:wheat_dough", [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}], "fd")):
    recipe(f"tortilla_{tag_}_dough", {"neoforge:conditions": conds_, "type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(dough_)], "result": result(f"{MOD}:tortilla", 3)})
    recipe(f"pie_crust_{tag_}_dough", {"neoforge:conditions": conds_, "type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(dough_), item("minecraft:sugar")], "result": result(f"{MOD}:pastry_crust", 2)})
for _f, _p, _n, _c in FRUITS:   # a dust of every fruit: dried fruit ground with sugar
    recipe(f"{_p}_dust", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:{_p}_dried"), item("minecraft:sugar")], "result": result(f"{MOD}:{_p}_dust", 2)})
recipe("salt_from_block", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:rock_salt")], "result": result(f"{MOD}:salt", 4)})

def dried_of(*names): return [f"{MOD}:{n}_dried" for n in names if any(p == n for _f, p, _n, _c in FRUITS)]
save(DATA / "tags/item/ember_fruits_dried.json", {"values": dried_of("ember_apple", "ember_plum", "ember_pear")})
save(DATA / "tags/item/frost_fruits_dried.json", {"values": dried_of("frost_apple", "frost_plum", "frost_pear")})
save(DATA / "tags/item/berries_dried.json", {"values": dried_of("strawberry", "blueberry", "raspberry", "blackberry", "redcurrant")})
save(DATA / "tags/item/tart_fruits_dried.json", {"values": dried_of("sour_apple", "cherry", "pomegranate")})
save(DATA / "tags/item/sun_fruits_dried.json", {"values": dried_of("sun_apple", "orange", "mango")})
save(DATA / "tags/item/seasonings.json", {"values": [f"{MOD}:{f}" for f, _t, _c, _x in SEASON] + ["minecraft:sugar"]})
save(DATA / "tags/item/sauces.json", {"values": jams + ["minecraft:honey_bottle", "minecraft:milk_bucket", {"id": "farmersdelight:tomato_sauce", "required": False}, {"id": "farmersdelight:milk_bottle", "required": False}]})

save(DATA / "recipe/dish.json", {"type": f"{MOD}:dish", "category": "misc"})

# colours of foods for the tint of a made-up dish
VANILLA = {"minecraft:cooked_beef": (140, 82, 54), "minecraft:cooked_porkchop": (214, 150, 110), "minecraft:cooked_chicken": (226, 176, 112), "minecraft:cooked_mutton": (158, 96, 70), "minecraft:cooked_rabbit": (190, 130, 90),
           "minecraft:cooked_cod": (214, 170, 120), "minecraft:cooked_salmon": (232, 130, 100), "minecraft:carrot": (240, 140, 40), "minecraft:potato": (200, 160, 90), "minecraft:baked_potato": (214, 170, 100),
           "minecraft:beetroot": (150, 40, 60), "minecraft:apple": (210, 50, 44), "minecraft:melon_slice": (232, 70, 80), "minecraft:sweet_berries": (190, 40, 60), "minecraft:glow_berries": (240, 170, 60),
           "minecraft:mushroom_stew": (170, 130, 100), "minecraft:pumpkin_pie": (220, 130, 50), "minecraft:cookie": (190, 130, 70), "minecraft:dried_kelp": (60, 90, 50), "minecraft:chorus_fruit": (150, 110, 160),
           "minecraft:golden_carrot": (240, 190, 50), "minecraft:honey_bottle": (232, 160, 40), "minecraft:beetroot_soup": (140, 40, 60), "minecraft:rabbit_stew": (170, 110, 70),
           "alexsmobs:cooked_moose_ribs": (150, 70, 46), "alexsmobs:cooked_kangaroo_meat": (160, 84, 66), "alexsmobs:cooked_lobster_tail": (214, 80, 56), "alexsmobs:cooked_catfish": (190, 150, 100), "alexsmobs:boiled_emu_egg": (240, 232, 200)}
colors = {k: (v[0] << 16) | (v[1] << 8) | v[2] for k, v in VANILLA.items()}
for fruit, path, name, col in FRUITS: colors[fruit] = (col[0] << 16) | (col[1] << 8) | col[2]
for _f, _p, _n, _c in FRUITS: colors[f"{MOD}:{_p}_dust"] = (_c[0] << 16) | (_c[1] << 8) | _c[2]
save(ROOT / "alterra_kitchen_colors.json", colors)


# ---------------------------------------------------------------- the cooking stations: stove, oven and kitchen counter
def bt(): return Image.new("RGBA", (16, 16), (0, 0, 0, 255))
def fill(im, x0, y0, x1, y1, col):
    px = im.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1): px[x, y] = col + (255,)
def disc(im, cx, cy, r, col):
    px = im.load()
    for y in range(16):
        for x in range(16):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r: px[x, y] = col + (255,)
def noise(im, seed, amt=6):
    r = random.Random(seed); px = im.load()
    for y in range(16):
        for x in range(16):
            v = r.randint(-amt, amt); c = px[x, y]; px[x, y] = (max(0, min(255, c[0] + v)), max(0, min(255, c[1] + v)), max(0, min(255, c[2] + v)), 255)
def frame(im, col_light, col_dark):
    fill(im, 0, 0, 15, 0, col_light); fill(im, 0, 0, 0, 15, col_light); fill(im, 0, 15, 15, 15, col_dark); fill(im, 15, 0, 15, 15, col_dark)
def glow(im, x0, y0, x1, y1, on):
    px = im.load()
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, y1 - y0)
        for x in range(x0, x1 + 1):
            px[x, y] = ((250 - int(60 * t), 150 - int(70 * t), 40 - int(20 * t)) if on else (28 + int(10 * t), 30 + int(10 * t), 38 + int(12 * t))) + (255,)
            if not on and (x + y) % 5 == 0: px[x, y] = (60, 64, 74, 255)   # a glint on the glass
    if on:
        for x in range(x0, x1 + 1, 3): px[x, y1] = (255, 210, 90, 255)
def station_textures():
    TEX = ASSETS / "textures/block"; TEX.mkdir(parents=True, exist_ok=True)
    ENAMEL = (222, 224, 220); ENAMEL_D = (170, 172, 170); IRON = (132, 134, 140); IRON_D = (88, 90, 96); IRON_L = (176, 178, 184)
    im = bt(); fill(im, 0, 0, 15, 15, (44, 44, 50)); noise(im, 1, 3)
    for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)): disc(im, cx, cy, 3.2, (92, 92, 100)); disc(im, cx, cy, 2.4, (36, 36, 42)); disc(im, cx, cy, 1.0, (70, 70, 78))
    frame(im, (96, 96, 104), (30, 30, 34)); im.save(TEX / "stove_top.png")
    im = bt(); fill(im, 0, 0, 15, 15, ENAMEL); noise(im, 2, 4); frame(im, (240, 242, 238), ENAMEL_D); fill(im, 0, 13, 15, 15, (70, 70, 76)); fill(im, 1, 7, 14, 7, ENAMEL_D); im.save(TEX / "stove_side.png")
    for on in (False, True):
        im = bt(); fill(im, 0, 0, 15, 15, ENAMEL); noise(im, 3, 4); frame(im, (240, 242, 238), ENAMEL_D)
        for kx in (2, 5, 8, 11): disc(im, kx + 1.5, 2.5, 1.5, (50, 50, 56)); im.putpixel((kx + 1, 2), (200, 200, 205, 255))
        fill(im, 2, 5, 13, 5, IRON_L); fill(im, 2, 6, 13, 12, IRON_D); glow(im, 3, 7, 12, 11, on); fill(im, 0, 13, 15, 15, (70, 70, 76))
        im.save(TEX / ("stove_front_on.png" if on else "stove_front.png"))
    im = bt(); fill(im, 0, 0, 15, 15, IRON); noise(im, 4, 5); frame(im, IRON_L, IRON_D)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)): im.putpixel((x, y), IRON_L + (255,))
    im.save(TEX / "oven_top.png")
    im = bt(); fill(im, 0, 0, 15, 15, IRON); noise(im, 5, 5); frame(im, IRON_L, IRON_D)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)): im.putpixel((x, y), IRON_L + (255,))
    im.save(TEX / "oven_side.png")
    for on in (False, True):
        im = bt(); fill(im, 0, 0, 15, 15, IRON); noise(im, 6, 5); frame(im, IRON_L, IRON_D)
        fill(im, 2, 2, 13, 2, IRON_L); fill(im, 2, 3, 13, 3, IRON_D); fill(im, 2, 4, 13, 12, (60, 62, 68)); glow(im, 3, 5, 12, 11, on)
        for x in (5, 8, 11): fill(im, x, 5, x, 11, (40, 40, 46)) if not on else None
        for kx in (3, 6, 9, 12): disc(im, kx + 0.5, 14, 0.9, (50, 50, 56))
        im.save(TEX / ("oven_front_on.png" if on else "oven_front.png"))
    im = bt(); fill(im, 0, 0, 15, 15, (206, 178, 128)); noise(im, 7, 5)
    for y in (3, 7, 11): fill(im, 0, y, 15, y, (160, 130, 84))
    frame(im, (230, 206, 160), (150, 120, 76)); im.save(TEX / "counter_top.png")
    im = bt(); fill(im, 0, 0, 15, 15, (150, 110, 66)); noise(im, 8, 5); frame(im, (176, 134, 86), (104, 74, 42))
    fill(im, 7, 1, 8, 14, (104, 74, 42)); fill(im, 1, 2, 6, 12, (168, 126, 78)); fill(im, 9, 2, 14, 12, (168, 126, 78))
    for x0, x1 in ((1, 6), (9, 14)): fill(im, x0, 2, x1, 2, (190, 150, 100)); fill(im, x0, 12, x1, 12, (120, 88, 52))
    disc(im, 5.5, 7, 0.8, (230, 220, 190)); disc(im, 10.5, 7, 0.8, (230, 220, 190)); im.save(TEX / "counter_side.png")
    im = bt(); fill(im, 0, 0, 15, 15, (118, 84, 48)); noise(im, 9, 5); frame(im, (140, 104, 62), (84, 58, 32)); im.save(TEX / "counter_bottom.png")
    (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
    for name in ("stove", "oven"):
        for on in (False, True):
            save(ASSETS / f"models/block/{name}{'_on' if on else ''}.json", {"parent": "minecraft:block/orientable", "textures": {"top": f"{MOD}:block/{name}_top", "front": f"{MOD}:block/{name}_front{'_on' if on else ''}", "side": f"{MOD}:block/{name}_side"}})
        variants = {}
        for lit in (False, True):
            for i, face in enumerate(("north", "east", "south", "west")):
                variants[f"facing={face},lit={'true' if lit else 'false'}"] = {"model": f"{MOD}:block/{name}{'_on' if lit else ''}", **({"y": 90 * i} if i else {})}
        save(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        save(ASSETS / f"models/item/{name}.json", {"parent": f"{MOD}:block/{name}"})
    save(ASSETS / "models/block/kitchen_counter.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": f"{MOD}:block/counter_top", "bottom": f"{MOD}:block/counter_bottom", "side": f"{MOD}:block/counter_side"}})
    save(ASSETS / "blockstates/kitchen_counter.json", {"variants": {"": {"model": f"{MOD}:block/kitchen_counter"}}})
    save(ASSETS / "models/item/kitchen_counter.json", {"parent": f"{MOD}:block/kitchen_counter"})
    for b_ in ("stove", "oven", "kitchen_counter"):
        save(DATA / f"loot_table/blocks/{b_}.json", {"type": "minecraft:block", "random_sequence": f"{MOD}:blocks/{b_}", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b_}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    save(DATA / "recipe/stove.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["III", "IFI", "BBB"], "key": {"I": item("minecraft:iron_ingot"), "F": item("minecraft:furnace"), "B": item("minecraft:brick")}, "result": result(f"{MOD}:stove")})
    save(DATA / "recipe/oven.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["III", "IGI", "IFI"], "key": {"I": item("minecraft:iron_ingot"), "G": item("minecraft:glass"), "F": item("minecraft:furnace")}, "result": result(f"{MOD}:oven")})
    save(DATA / "recipe/kitchen_counter.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["SSS", "PTP"], "key": {"S": item("minecraft:smooth_stone_slab"), "P": T("minecraft:planks"), "T": item(f"{MOD}:prep_table")}, "result": result(f"{MOD}:kitchen_counter")})
station_textures()

def station2():
    """The prep table (an early workbench) and the ice cream machine."""
    TEX = ASSETS / "textures/block"
    OAK, OAK_L, OAK_D = (196, 160, 104), (222, 188, 132), (140, 104, 60)
    im = bt(); fill(im, 0, 0, 15, 15, OAK); noise(im, 11, 5)
    for y in (3, 7, 11): fill(im, 0, y, 15, y, (170, 134, 82))
    frame(im, OAK_L, OAK_D)
    fill(im, 3, 3, 12, 12, (232, 208, 158)); noise_box = None
    fill(im, 3, 3, 12, 3, (246, 226, 182)); fill(im, 3, 12, 12, 12, (190, 160, 110)); fill(im, 3, 3, 3, 12, (240, 218, 170)); fill(im, 12, 3, 12, 12, (190, 160, 110))
    for x, y in ((5, 6), (7, 5), (9, 8), (6, 9), (8, 10)): im.putpixel((x, y), (214, 188, 138, 255))
    fill(im, 10, 9, 11, 9, (170, 172, 180)); fill(im, 10, 10, 11, 10, (120, 122, 130)); im.putpixel((12, 10), (110, 76, 44, 255))   # a small knife on the board
    im.save(TEX / "prep_table_top.png")
    im = bt(); fill(im, 0, 0, 15, 15, (170, 128, 76)); noise(im, 12, 5); frame(im, (196, 154, 98), OAK_D)
    fill(im, 1, 1, 14, 3, (190, 148, 92)); fill(im, 1, 4, 14, 4, OAK_D)
    fill(im, 1, 5, 14, 11, (182, 140, 86)); fill(im, 2, 6, 13, 6, (206, 166, 110)); fill(im, 2, 10, 13, 10, (130, 94, 56))
    disc(im, 7.5, 8.2, 0.9, (236, 226, 196)); fill(im, 1, 12, 2, 15, OAK_D); fill(im, 13, 12, 14, 15, OAK_D)
    im.save(TEX / "prep_table_side.png")
    im = bt(); fill(im, 0, 0, 15, 15, (126, 92, 54)); noise(im, 13, 5); frame(im, (146, 110, 66), (90, 62, 34)); im.save(TEX / "prep_table_bottom.png")
    ENA, ENA_D, ENA_L, CH, CH_D, CH_L = (214, 232, 240), (146, 178, 198), (244, 252, 255), (150, 156, 164), (96, 102, 112), (206, 212, 220)
    im = bt(); fill(im, 0, 0, 15, 15, CH); noise(im, 14, 4); frame(im, CH_L, CH_D); disc(im, 7.5, 7.5, 4.6, CH_D); disc(im, 7.5, 7.5, 3.8, (122, 130, 140)); disc(im, 7.5, 7.5, 1.2, CH_L)
    im.save(TEX / "ice_machine_top.png")
    im = bt(); fill(im, 0, 0, 15, 15, ENA); noise(im, 15, 3); frame(im, ENA_L, ENA_D); fill(im, 0, 13, 15, 15, CH_D)
    for y in (9, 11): fill(im, 3, y, 12, y, ENA_D)
    im.save(TEX / "ice_machine_side.png")
    for on in (False, True):
        im = bt(); fill(im, 0, 0, 15, 15, ENA); noise(im, 16, 3); frame(im, ENA_L, ENA_D)
        for kx in (3, 11): disc(im, kx + 0.5, 2.5, 1.3, CH_D); im.putpixel((kx, 2), CH_L + (255,))
        fill(im, 6, 2, 9, 3, CH); fill(im, 2, 5, 13, 5, CH_L)
        px = im.load()
        for y in range(6, 11):
            t = (y - 6) / 4
            for x in range(2, 14):
                px[x, y] = ((150 - int(40 * t), 226 - int(50 * t), 244 - int(30 * t)) if on else (30 + int(8 * t), 52 + int(10 * t), 62 + int(12 * t))) + (255,)
        if on:
            for x, y in ((5, 9), (6, 8), (7, 7), (8, 8), (9, 9), (7, 9), (8, 6), (7, 6)): px[x, y] = (246, 252, 255, 255)   # a swirl in the window
        else:
            for x, y in ((4, 6), (5, 7), (11, 6)): px[x, y] = (70, 98, 110, 255)
        fill(im, 6, 11, 9, 12, CH); fill(im, 7, 13, 8, 13, CH_L); fill(im, 0, 14, 15, 15, CH_D)
        im.save(TEX / ("ice_machine_front_on.png" if on else "ice_machine_front.png"))
    save(ASSETS / "models/block/prep_table.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": f"{MOD}:block/prep_table_top", "bottom": f"{MOD}:block/prep_table_bottom", "side": f"{MOD}:block/prep_table_side"}})
    save(ASSETS / "blockstates/prep_table.json", {"variants": {"": {"model": f"{MOD}:block/prep_table"}}})
    save(ASSETS / "models/item/prep_table.json", {"parent": f"{MOD}:block/prep_table"})
    name = "ice_machine"
    for on in (False, True):
        save(ASSETS / f"models/block/{name}{'_on' if on else ''}.json", {"parent": "minecraft:block/orientable", "textures": {"top": f"{MOD}:block/{name}_top", "front": f"{MOD}:block/{name}_front{'_on' if on else ''}", "side": f"{MOD}:block/{name}_side"}})
    variants = {}
    for lit in (False, True):
        for i, face in enumerate(("north", "east", "south", "west")):
            variants[f"facing={face},lit={'true' if lit else 'false'}"] = {"model": f"{MOD}:block/{name}{'_on' if lit else ''}", **({"y": 90 * i} if i else {})}
    save(ASSETS / f"blockstates/{name}.json", {"variants": variants})
    save(ASSETS / f"models/item/{name}.json", {"parent": f"{MOD}:block/{name}"})
    for b_ in ("prep_table", "ice_machine"):
        save(DATA / f"loot_table/blocks/{b_}.json", {"type": "minecraft:block", "random_sequence": f"{MOD}:blocks/{b_}", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b_}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    save(DATA / "recipe/prep_table.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["PPP", "S S"], "key": {"P": T("minecraft:planks"), "S": item("minecraft:stick")}, "result": result(f"{MOD}:prep_table")})
    save(DATA / "recipe/ice_machine.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["III", "GSG", "ICI"], "key": {"I": item("minecraft:iron_ingot"), "G": item("minecraft:glass"), "S": item("minecraft:snow_block"), "C": item("minecraft:copper_ingot")}, "result": result(f"{MOD}:ice_machine")})
station2()

def station3():
    """The powered ice cream machine (Create): the same machine with brass trim and a shaft in the back."""
    TEX = ASSETS / "textures/block"; BR, BR_D, BR_L = (196, 150, 70), (130, 94, 40), (234, 202, 120)
    def brass(name, out, shaft=False):
        im = Image.open(TEX / f"{name}.png").convert("RGBA")
        for x in range(16):
            im.putpixel((x, 0), BR_L + (255,)); im.putpixel((x, 15), BR_D + (255,))
        for y in range(16):
            im.putpixel((0, y), BR_L + (255,)); im.putpixel((15, y), BR_D + (255,))
        if shaft:
            disc(im, 7.5, 7.5, 3.4, BR_D); disc(im, 7.5, 7.5, 2.6, BR); fill(im, 6, 6, 9, 9, (92, 70, 36)); fill(im, 7, 7, 8, 8, BR_L)
        im.save(TEX / f"{out}.png")
    brass("ice_machine_side", "powered_ice_machine_side"); brass("ice_machine_side", "powered_ice_machine_back", True)
    brass("ice_machine_top", "powered_ice_machine_top"); brass("ice_machine_front", "powered_ice_machine_front"); brass("ice_machine_front_on", "powered_ice_machine_front_on")
    for on in (False, True):
        save(ASSETS / f"models/block/powered_ice_machine{'_on' if on else ''}.json", {"parent": "minecraft:block/cube", "textures": {
            "particle": f"{MOD}:block/powered_ice_machine_side", "down": f"{MOD}:block/powered_ice_machine_back", "up": f"{MOD}:block/powered_ice_machine_top",
            "north": f"{MOD}:block/powered_ice_machine_front{'_on' if on else ''}", "south": f"{MOD}:block/powered_ice_machine_back", "east": f"{MOD}:block/powered_ice_machine_side", "west": f"{MOD}:block/powered_ice_machine_side"}})
    variants = {}
    for lit in (False, True):
        for i, face in enumerate(("north", "east", "south", "west")):
            variants[f"facing={face},lit={'true' if lit else 'false'}"] = {"model": f"{MOD}:block/powered_ice_machine{'_on' if lit else ''}", **({"y": 90 * i} if i else {})}
    save(ASSETS / "blockstates/powered_ice_machine.json", {"variants": variants})
    save(ASSETS / "models/item/powered_ice_machine.json", {"parent": f"{MOD}:block/powered_ice_machine"})
    save(DATA / "loot_table/blocks/powered_ice_machine.json", {"type": "minecraft:block", "random_sequence": f"{MOD}:blocks/powered_ice_machine", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:powered_ice_machine"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    crec("powered_ice_machine", CR, {"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": [item(f"{MOD}:ice_machine"), item("create:andesite_casing"), item("create:shaft")], "result": result(f"{MOD}:powered_ice_machine")})
station3()

def station_stand():
    """The ice cream stand: a glass-fronted counter with an awning-striped top."""
    TEX = ASSETS / "textures/block"; CREAM, RED, WOOD_ = (246, 238, 226), (200, 66, 78), (206, 170, 120)
    im = bt(); fill(im, 0, 0, 15, 15, CREAM)
    for x in range(0, 16, 4): fill(im, x, 0, x + 1, 15, RED); fill(im, x + 1, 0, x + 1, 15, (222, 96, 106))
    noise(im, 21, 3); frame(im, (255, 250, 244), (150, 120, 110)); im.save(TEX / "ice_cream_stand_top.png")
    im = bt(); fill(im, 0, 0, 15, 15, WOOD_); noise(im, 22, 5); frame(im, (226, 196, 150), (140, 104, 62))
    fill(im, 1, 1, 14, 2, RED); fill(im, 1, 3, 14, 3, (150, 40, 52))
    fill(im, 2, 5, 13, 11, (176, 214, 226)); fill(im, 2, 5, 13, 5, (226, 244, 250)); fill(im, 2, 11, 13, 11, (120, 160, 176))
    for cx, col in ((4.5, (250, 196, 60)), (8, (214, 52, 70)), (11.5, (120, 60, 130))): disc(im, cx, 8.6, 1.9, col)   # scoops behind the glass
    fill(im, 7, 5, 8, 11, (140, 104, 62)); im.save(TEX / "ice_cream_stand_side.png")
    im = bt(); fill(im, 0, 0, 15, 15, (150, 116, 76)); noise(im, 23, 5); frame(im, (170, 134, 90), (104, 76, 44)); im.save(TEX / "ice_cream_stand_bottom.png")
    save(ASSETS / "models/block/ice_cream_stand.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": f"{MOD}:block/ice_cream_stand_top", "bottom": f"{MOD}:block/ice_cream_stand_bottom", "side": f"{MOD}:block/ice_cream_stand_side"}})
    save(ASSETS / "blockstates/ice_cream_stand.json", {"variants": {"": {"model": f"{MOD}:block/ice_cream_stand"}}})
    save(ASSETS / "models/item/ice_cream_stand.json", {"parent": f"{MOD}:block/ice_cream_stand"})
    save(DATA / "loot_table/blocks/ice_cream_stand.json", {"type": "minecraft:block", "random_sequence": f"{MOD}:blocks/ice_cream_stand", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{MOD}:ice_cream_stand"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    save(DATA / "recipe/ice_cream_stand.json", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["GGG", "PSP", "PPP"], "key": {"G": item("minecraft:glass"), "P": T("minecraft:planks"), "S": item("minecraft:snow_block")}, "result": result(f"{MOD}:ice_cream_stand")})
station_stand()


# ---------------------------------------------------------------- Farmer's Delight: our stove and oven are heat sources for its pot and skillet; its cutting board gets our dough and rock salt; rice cooks in our stove
(DATA.parent / "farmersdelight/tags/block").mkdir(parents=True, exist_ok=True)
save(DATA.parent / "farmersdelight/tags/block/heat_sources.json", {"replace": False, "values": [f"{MOD}:stove", f"{MOD}:oven"]})
KNIFE = [{"type": "farmersdelight:item_ability", "action": "knife_dig"}, {"tag": "c:tools/knife"}]
PICK = [{"type": "farmersdelight:item_ability", "action": "pickaxe_dig"}, {"tag": "minecraft:pickaxes"}]
for dough_, dc_, tg_ in (("create:dough", [{"type": "neoforge:mod_loaded", "modid": "create"}], "create"), ("farmersdelight:wheat_dough", [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}], "fd")):
    for out_, n_ in (("noodles", 6), ("spaghetti", 4), ("tortilla", 3), ("macaroni", 4)):
        recipe(f"board_{out_}_{tg_}", {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}] + dc_ + [{"type": "alterra:config", "option": "fdCutting"}],
                                       "type": "farmersdelight:cutting", "ingredients": [item(dough_)], "result": [{"item": result(f"{MOD}:{out_}", n_)}], "tool": KNIFE})
recipe("board_rock_salt", {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}, {"type": "alterra:config", "option": "fdCutting"}],
                           "type": "farmersdelight:cutting", "ingredients": [item(f"{MOD}:rock_salt")], "result": [{"item": result(f"{MOD}:salt", 3)}], "tool": PICK})
recipe("stove_rice", {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}], "type": "minecraft:smoking", "category": "food", "ingredient": item("farmersdelight:rice"),
                      "result": result("farmersdelight:cooked_rice"), "experience": 0.2, "cookingtime": 100})


# ---------------------------------------------------------------- icons of the bad-food effects (18x18, drawn by the game in the effect list and the inventory)
def effect_icons():
    T_ = ASSETS / "textures/mob_effect"; T_.mkdir(parents=True, exist_ok=True)
    def blank(): return Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    def line(px, x0, y0, x1, y1, col):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1): px[round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)] = col + (255,)
    # toxin: a green skull on a dark ground
    im = blank(); px = im.load(); ellipse(px, 9, 7.5, 6.6, 6.0, (170, 220, 90), (214, 250, 150), (110, 160, 50)); rect(px, 5, 11, 12, 15, (150, 200, 80)); rect(px, 6, 15, 11, 16, (110, 160, 50))
    for x in (7, 9, 11): rect(px, x, 14, x, 16, (28, 40, 14))
    for cx in (6, 11): rect(px, cx, 6, cx + 1, 9, (20, 30, 10))
    rect(px, 8, 10, 9, 11, (28, 40, 14)); outline(im, (24, 44, 12)).save(T_ / "toxin.png")
    # food poisoning: a sick green-yellow face
    im = blank(); px = im.load(); ellipse(px, 9, 9, 7.2, 7.2, (190, 200, 70), (226, 232, 120), (140, 150, 40))
    for cx in (6, 11): line(px, cx - 1, 6, cx + 1, 8, (60, 70, 10)); line(px, cx + 1, 6, cx - 1, 8, (60, 70, 10))
    for x in range(6, 13): px[x, 13 + (1 if x % 2 else 0)] = (60, 70, 10, 255)
    rect(px, 12, 11, 13, 14, (120, 190, 60)); outline(im, (60, 70, 16)).save(T_ / "food_poisoning.png")
    # nerve damage: a neuron with sparks, violet
    im = blank(); px = im.load(); disc(im, 9, 9, 2.6, (200, 130, 255))
    for (x1, y1) in ((2, 3), (15, 3), (2, 15), (15, 15), (9, 1), (9, 17)): line(px, 9, 9, x1, y1, (150, 70, 210))
    for (x, y) in ((2, 3), (15, 3), (2, 15), (15, 15), (9, 1), (9, 17)): disc(im, x + .5, y + .5, 1.3, (230, 190, 255))
    line(px, 4, 9, 7, 7, (255, 255, 255)); line(px, 7, 7, 6, 11, (255, 255, 255)); line(px, 6, 11, 9, 9, (255, 255, 255))
    outline(im, (60, 16, 90)).save(T_ / "nerve_damage.png")
    # hallucination: an eye with a rainbow iris
    im = blank(); px = im.load(); ellipse(px, 9, 9, 8, 4.6, (240, 236, 250), (255, 255, 255), (190, 180, 210))
    for k, col in enumerate(((240, 80, 160), (250, 200, 60), (80, 200, 255), (160, 100, 255))): ellipse(px, 9, 9, 3.6 - k * 0.8, 3.6 - k * 0.8, col)
    rect(px, 8, 8, 9, 9, (20, 10, 30)); outline(im, (70, 30, 100)).save(T_ / "hallucination.png")
    # paralysis: a figure with a cross through it, grey-blue
    im = blank(); px = im.load(); disc(im, 9, 4.5, 2.4, (170, 190, 210)); rect(px, 8, 7, 10, 12, (140, 160, 184)); line(px, 4, 8, 14, 8, (140, 160, 184)); line(px, 9, 12, 5, 16, (120, 140, 164)); line(px, 9, 12, 13, 16, (120, 140, 164))
    line(px, 2, 2, 16, 16, (220, 60, 60)); line(px, 16, 2, 2, 16, (220, 60, 60)); outline(im, (30, 40, 60)).save(T_ / "paralysis.png")
    # cardiac strain: a heart with a broken beat line
    im = blank(); px = im.load(); disc(im, 6, 6, 3.2, (214, 40, 70)); disc(im, 12, 6, 3.2, (214, 40, 70))
    for y in range(7, 15): rect(px, 9 - (y - 6) if y < 14 else 8, y, 9 + (y - 6) if y < 14 else 9, y, (214, 40, 70))
    line(px, 1, 9, 5, 9, (255, 230, 230)); line(px, 5, 9, 7, 4, (255, 230, 230)); line(px, 7, 4, 10, 13, (255, 230, 230)); line(px, 10, 13, 12, 9, (255, 230, 230)); line(px, 12, 9, 17, 9, (255, 230, 230))
    outline(im, (80, 8, 24)).save(T_ / "cardiac.png")
effect_icons()


# ---------------------------------------------------------------- wild plants: poison fern, nightshade, hemlock, deathcap, oleander (the sunfern is Legendary Survival Overhaul's)
import math as _m
def _line(px, x0, y0, x1, y1, col):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(int(n) + 1): put(px, round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), col)
def fern_plant(dark, mid, light, tip, seed, spores=None):
    im, _ = canvas(); px = im.load(); r = seeded(seed)
    for a_ in (-72, -48, -24, 0, 24, 48, 72):
        ln = 7 + (3 if abs(a_) < 30 else 1) + r.randint(-1, 1); dx, dy = _m.sin(_m.radians(a_)), -_m.cos(_m.radians(a_))
        for t in range(1, ln + 1):
            x, y = 8 + dx * t, 15 + dy * t * 0.95; put(px, round(x), round(y), mid)
            if t % 2 == 0 and t > 1:   # leaflets either side of the frond
                put(px, round(x - dy * 1.2), round(y + dx * 1.2), light); put(px, round(x + dy * 1.2), round(y - dx * 1.2), dark)
        put(px, round(8 + dx * ln), round(15 + dy * ln * 0.95), tip)
    if spores:
        for _ in range(6): put(px, r.randint(3, 12), r.randint(3, 11), spores)
    return px, im
def poison_fern(): return fern_plant((44, 20, 64), (92, 44, 126), (150, 80, 190), (226, 60, 96), "poisonfern", (236, 236, 120))[1]
def nightshade():
    im, _ = canvas(); px = im.load(); r = seeded("nightshade")
    for cx, cy, rx, ry in ((5, 11, 3.6, 2.4), (11, 10, 3.8, 2.6), (8, 7, 3.4, 2.4), (8, 13, 4.6, 2.0)): ellipse(px, cx, cy, rx, ry, (52, 120, 52), (88, 160, 70), (30, 80, 36))
    for x, y in ((5, 9), (9, 10), (11, 12), (7, 12), (12, 8)):   # glossy dark berries
        ellipse(px, x, y, 1.4, 1.4, (64, 22, 92), (120, 70, 160), (36, 10, 56)); put(px, x - 1, y - 1, (220, 200, 240))
    return im
def hemlock():
    im, _ = canvas(); px = im.load()
    _line(px, 8, 15, 8, 6, (108, 150, 70)); _line(px, 8, 11, 4, 8, (108, 150, 70)); _line(px, 8, 12, 12, 9, (108, 150, 70)); _line(px, 8, 14, 5, 12, (80, 120, 56)); _line(px, 8, 14, 11, 12, (80, 120, 56))
    for k in range(3, 6): put(px, 8 - k + 2, 12 + (k % 2), (80, 120, 56)); put(px, 8 + k - 2, 12 + (k % 2), (80, 120, 56))
    for (cx, cy) in ((3, 6), (5, 3), (8, 2), (11, 3), (13, 6)):   # umbels: little white flowers on spokes
        _line(px, 8, 6, cx, cy + 1, (150, 190, 110))
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (1, 1)): put(px, cx + dx, cy + dy, (244, 248, 238) if (dx + dy) % 2 == 0 else (210, 226, 200))
    return im
def deathcap_mush(px, cx, cy, big):
    rx, ry = (5.4, 3.4) if big else (3.4, 2.2)
    for dy in range(int(ry) + 6 if big else int(ry) + 4):
        pass
    sw = 2 if big else 1; top = int(cy + ry * 0.4); bottom = 15
    for y in range(top, bottom + 1):
        for x in range(cx - sw // 2, cx - sw // 2 + sw): put(px, x, y, (232, 228, 200) if y < bottom else (190, 186, 160))
    if big: put(px, cx - 1, top + 2, (200, 196, 168)); put(px, cx + 1, top + 2, (200, 196, 168))   # the ring on the stem
    ellipse(px, cx, cy, rx, ry, (176, 186, 78), (214, 222, 120), (126, 138, 48))
    if big:
        for x, y in ((cx - 2, cy - 1), (cx + 1, cy - 2), (cx + 2, cy)): put(px, x, y, (240, 240, 220))
def deathcap():
    im, _ = canvas(); px = im.load(); deathcap_mush(px, 6, 8, True); deathcap_mush(px, 12, 12, False); return im
def oleander():
    im, _ = canvas(); px = im.load()
    for a_ in (-60, -35, -10, 12, 36, 62):
        dx, dy = _m.sin(_m.radians(a_)), -_m.cos(_m.radians(a_))
        for t in range(1, 10): put(px, round(8 + dx * t), round(15 + dy * t), (60, 130, 62) if t % 3 else (92, 168, 80))
    for (cx, cy) in ((4, 6), (11, 4), (8, 8), (13, 9)):   # pink five-petalled flowers
        for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)): put(px, cx + dx, cy + dy, (240, 110, 170))
        put(px, cx, cy, (255, 220, 120)); put(px, cx - 1, cy - 1, (250, 150, 196))
    return im
PLANT_ART = {"poison_fern": poison_fern, "nightshade": nightshade, "hemlock": hemlock, "deathcap": deathcap, "oleander": oleander}
PLANT_ITEM = {"poison_fern": "poison_fern_fronds", "nightshade": "nightshade_berries", "hemlock": "hemlock_leaves", "deathcap": "deathcap", "oleander": "oleander_leaves"}
def item_icon(plant):
    """The harvested part, as an item: the plant drawn again, smaller and tilted, with a dark edge."""
    big = PLANT_ART[plant](); im, _ = canvas(); px = im.load(); bp = big.load()
    for y in range(16):
        for x in range(16):
            if bp[x, y][3] > 0:
                nx, ny = 3 + int(x * 0.62) + 1, 2 + int(y * 0.62) + 1
                if 0 <= nx < 16 and 0 <= ny < 16: px[nx, ny] = bp[x, y]
    return outline(im)
def plants():
    TB = ASSETS / "textures/block"; TI = ASSETS / "textures/item"; TB.mkdir(parents=True, exist_ok=True); TI.mkdir(parents=True, exist_ok=True)
    (DATA / "worldgen/configured_feature").mkdir(parents=True, exist_ok=True); (DATA / "worldgen/placed_feature").mkdir(parents=True, exist_ok=True); (DATA / "neoforge/biome_modifier").mkdir(parents=True, exist_ok=True)
    rarity = {"poison_fern": 9, "nightshade": 12, "hemlock": 11, "deathcap": 14, "oleander": 13}
    import runpy; runpy.run_path(str(Path(__file__).resolve().parent / "plant_art.py"), run_name="plant_art")["write_all"]()   # the sprites of the plants and what they drop
    for name, draw in PLANT_ART.items():
        save(ASSETS / f"models/block/{name}.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": f"{MOD}:block/{name}"}})
        save(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        save(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:block/{name}"}})
        save(ASSETS / f"models/item/{PLANT_ITEM[name]}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{PLANT_ITEM[name]}"}})
        save(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "random_sequence": f"{MOD}:blocks/{name}", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
            {"type": "minecraft:item", "name": f"{MOD}:{PLANT_ITEM[name]}", "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}, {"function": "minecraft:explosion_decay"}]}]}]})
        ground = ["minecraft:grass_block", "minecraft:dirt", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:moss_block", "minecraft:rooted_dirt"]
        save(DATA / f"worldgen/configured_feature/{name}.json", {"type": "minecraft:random_patch", "config": {"tries": 18, "xz_spread": 5, "y_spread": 2, "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": f"{MOD}:{name}"}}}},
            "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [{"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}, {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": ground}]}}]}}})
        save(DATA / f"worldgen/placed_feature/{name}.json", {"feature": f"{MOD}:{name}", "placement": [{"type": "minecraft:rarity_filter", "chance": rarity[name]}, {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
        save(DATA / f"neoforge/biome_modifier/{name}.json", {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": f"{MOD}:{name}", "step": "vegetal_decoration"})
plants()



# ---- the ferns are not flat crossed planes: a rosette of ten fronds, each a full sheet of one frond (tools/plant_art.py), the outer ones leaning out at 45 degrees and the inner ones at 22.5, two standing upright through the middle
ROSETTE = [("low_back", [1, 0, 8], [15, 14, 8], -45, "x"), ("low_front", [1, 0, 8], [15, 14, 8], 45, "x"), ("low_left", [8, 0, 1], [8, 14, 15], 45, "z"), ("low_right", [8, 0, 1], [8, 14, 15], -45, "z"),
           ("mid_back", [2, 0, 8], [14, 15, 8], -22.5, "x"), ("mid_front", [2, 0, 8], [14, 15, 8], 22.5, "x"), ("mid_left", [8, 0, 2], [8, 15, 14], 22.5, "z"), ("mid_right", [8, 0, 2], [8, 15, 14], -22.5, "z"),
           ("up_a", [2, 0, 8], [14, 16, 8], 45, "y"), ("up_b", [2, 0, 8], [14, 16, 8], -45, "y")]
def rosette_model(name):
    els = []
    for n, a_, b_, ang, axis in ROSETTE:
        faces = ["east", "west"] if axis == "z" else ["north", "south"]
        els.append({"name": n, "from": a_, "to": b_, "shade": False, "rotation": {"angle": ang, "axis": axis, "origin": [8, 0, 8]}, "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#0"} for f in faces}})
    return {"render_type": "minecraft:cutout", "ambientocclusion": False, "textures": {"0": f"{MOD}:block/{name}_frond", "particle": f"{MOD}:block/{name}"}, "elements": els}
for _n in ("poison_fern",): save(ASSETS / f"models/block/{_n}.json", rosette_model(_n))



berries = [f for f, p, n, c in FRUITS if p in ("strawberry", "blueberry", "raspberry", "blackberry", "redcurrant")] + ["minecraft:sweet_berries", "minecraft:glow_berries"]
save(DATA / "tags/item/fruits.json", {"values": fruitl})
save(DATA / "tags/item/jams.json", {"values": jams})
save(DATA / "tags/item/dried_fruits.json", {"values": driedl})
save(DATA / "tags/item/berries.json", {"values": berries})

# ---------------------------------------------------------------- nothing is eaten raw: baked goods chain from baked bases
import glob as _glob, os as _os
def _drop(*names):
    for n_ in names:
        for f_ in _glob.glob(str(DATA / f"recipe/food/{n_}.json")): _os.remove(f_)
_drop("bun_plain", "bun_*_dough", "tortilla_plain", "tortilla_*_dough", "pie_crust_plain", "pie_crust_*_dough", "flatbread_plain", "board_tortilla_*")
def dough_item():
    im, _ = canvas(); px = im.load()
    ellipse(px, 8, 9, 6.6, 4.4, (232, 214, 170), (248, 236, 200), (196, 174, 128))
    for x, y in ((5, 8), (9, 10), (11, 8)): put(px, x, y, (210, 188, 140))
    return outline(im)
add_food("dough", "Dough", dough_item(), dict(ingredient=True, stack=64))
def wrapper_item():
    im, _ = canvas(); px = im.load()
    ellipse(px, 8, 9, 6.4, 4.2, (236, 224, 186), (250, 244, 214), (206, 190, 150)); ellipse(px, 8, 8.8, 5.0, 3.0, (244, 234, 200), (252, 248, 222), (224, 210, 170))
    return outline(im)
add_food("dumpling_wrapper", "Dumpling Wrapper", wrapper_item(), dict(ingredient=True, stack=64))
recipe("dumpling_wrapper", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:flatbread")], "result": result(f"{MOD}:dumpling_wrapper", 4)})
recipe("dough_plain", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:wheat"), item("minecraft:wheat"), item("minecraft:water_bucket")], "result": result(f"{MOD}:dough", 3)})
for kind_, t_, cook in (("smelting", "minecraft:smelting", 200), ("smoking", "minecraft:smoking", 100), ("campfire", "minecraft:campfire_cooking", 400)):
    recipe(f"flatbread_dough_{kind_}", {"type": t_, "category": "food", "ingredient": item(f"{MOD}:dough"), "result": result(f"{MOD}:flatbread"), "experience": 0.2, "cookingtime": cook})
recipe("noodles_dough", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:dough"), item(f"{MOD}:dough")], "result": result(f"{MOD}:noodles", 8)})
recipe("spaghetti_dough", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:dough")], "result": result(f"{MOD}:spaghetti", 3)})
FLAT = item(f"{MOD}:flatbread")
recipe("bun_from_flatbread", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FLAT, item("minecraft:sugar")], "result": result(f"{MOD}:bun", 2)})
recipe("tortilla_from_flatbread", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FLAT], "result": result(f"{MOD}:tortilla", 2)})
recipe("pie_crust_from_flatbread", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FLAT, FLAT, item("minecraft:sugar")], "result": result(f"{MOD}:pastry_crust")})
BUN, CRUST = item(f"{MOD}:bun"), item(f"{MOD}:pastry_crust")
for fruit, path, name, col in FRUITS:
    F = item(fruit)
    recipe(f"{path}_pie", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [CRUST, F, F, F, item("minecraft:sugar")], "result": result(f"{MOD}:{path}_fruit_pie")})
    recipe(f"{path}_muffin", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [BUN, F, item("minecraft:sugar")], "result": result(f"{MOD}:{path}_muffin", 2)})
    recipe(f"{path}_bar", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [F, FLAT, item("minecraft:honey_bottle")], "result": result(f"{MOD}:{path}_bar", 3)})
recipe("fruit_porridge", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FLAT, FR, FR, item("minecraft:milk_bucket"), item("minecraft:bowl")], "result": result(f"{MOD}:fruit_porridge")})
recipe("fruit_bread", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [BUN, BUN, FR, item("minecraft:sugar")], "result": result(f"{MOD}:fruit_bread")})
recipe("berry_cookies", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FLAT, item("minecraft:sugar"), T(f"{MOD}:berries")], "result": result(f"{MOD}:berry_cookies", 6)})
arecipe("emu_egg_pie", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [CRUST, am("boiled_emu_egg"), FR, FR, item("minecraft:sugar")], "result": result(f"{MOD}:emu_egg_pie")})

save(DATA / "tags/item/uncooked.json", {"values": [{"id": i_, "required": False} for i_ in (
    "minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton", "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:potato",
    "farmersdelight:minced_beef", "farmersdelight:chicken_cuts", "farmersdelight:mutton_chops", "farmersdelight:bacon", "farmersdelight:ham", "farmersdelight:cod_slice", "farmersdelight:salmon_slice", "farmersdelight:beef_patty", "farmersdelight:raw_pasta", "farmersdelight:rice", "farmersdelight:wheat_dough", "create:dough",
    "alexsmobs:moose_ribs", "alexsmobs:kangaroo_meat", "alexsmobs:lobster_tail", "alexsmobs:raw_catfish", "alexsmobs:flying_fish", "alexsmobs:blobfish", "alexsmobs:emu_egg", f"{MOD}:dough")]})

# ---------------------------------------------------------------- ice creams and other frozen treats
add_food("wafer_cone", "Wafer Cone", TREATS["wafer_cone"], dict(ingredient=True, stack=64))
for fruit, path, name, col in FRUITS:
    add_food(f"{path}_ice_cream", f"{name} Ice Cream", None, dict(nutrition=5, saturation=0.5, eat=1.6, stack=16))
    add_food(f"{path}_popsicle", f"{name} Popsicle", None, dict(nutrition=4, saturation=0.4, eat=1.2, stack=16))
for fid_, title_ in (("honey_ice_cream", "Honey Ice Cream"), ("chocolate_ice_cream", "Chocolate Ice Cream")): add_food(fid_, title_, None, dict(nutrition=6, saturation=0.6, eat=1.6, stack=16))
add_food("ice_cream_sandwich", "Ice Cream Sandwich", None, dict(nutrition=7, saturation=0.6, eat=1.6, stack=16))
add_food("sundae", "Sundae", None, dict(nutrition=9, saturation=0.8, eat=2.0, returns="minecraft:bowl", stack=16))
SNOW, MILK, SUGAR, CONE = item("minecraft:snowball"), item("minecraft:milk_bucket"), item("minecraft:sugar"), item(f"{MOD}:wafer_cone")
recipe("wafer_cone", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FLAT, SUGAR], "result": result(f"{MOD}:wafer_cone", 4)})
ICE = []
for fruit, path, name, col in FRUITS:
    F = item(fruit)
    recipe(f"{path}_ice_cream", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [CONE, F, F, SNOW, SUGAR, MILK], "result": result(f"{MOD}:{path}_ice_cream")})
    recipe(f"{path}_popsicle", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [F, F, SNOW, SUGAR, item("minecraft:stick")], "result": result(f"{MOD}:{path}_popsicle", 2)})
    ICE.append(f"{MOD}:{path}_ice_cream")
recipe("honey_ice_cream", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [CONE, item("minecraft:honey_bottle"), SNOW, MILK], "result": result(f"{MOD}:honey_ice_cream")})
recipe("chocolate_ice_cream", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [CONE, item("minecraft:cocoa_beans"), item("minecraft:cocoa_beans"), SNOW, SUGAR, MILK], "result": result(f"{MOD}:chocolate_ice_cream")})
recipe("ice_cream_sandwich", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [FLAT, FLAT, SNOW, SUGAR, MILK], "result": result(f"{MOD}:ice_cream_sandwich", 2)})
ICE += [f"{MOD}:honey_ice_cream", f"{MOD}:chocolate_ice_cream"]
# ---- soft serve (the swirl) and stacked cones
SCOOPS = {}
for _f, path, name, col in FRUITS:
    add_food(f"{path}_soft_serve", f"{name} Soft Serve", None, dict(nutrition=5, saturation=0.5, eat=1.6, stack=16, taste=84))
    recipe(f"{path}_soft_serve", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:{path}_ice_cream"), SNOW], "result": result(f"{MOD}:{path}_soft_serve")})
    ICE.append(f"{MOD}:{path}_soft_serve")
    # with Create the machines do the work (and use its stress): a mixer makes the ice cream, a deployer puts the snowball on for the swirl
    F_ = item(fruit)
    recipe(f"{path}_soft_serve_freezer", {"type": f"{MOD}:freezing", "category": "food", "ingredient": item(f"{MOD}:{path}_ice_cream"), "result": result(f"{MOD}:{path}_soft_serve"), "experience": 0.0, "cookingtime": 100})
    crec(f"{path}_ice_cream_mixer", CR, {"type": "create:mixing", "ingredients": [CONE, F_, F_, SNOW, SUGAR], "results": [result(f"{MOD}:{path}_ice_cream")]})
    crec(f"{path}_soft_serve_deployer", CR, {"type": "create:deploying", "ingredients": [item(f"{MOD}:{path}_ice_cream"), SNOW], "results": [result(f"{MOD}:{path}_soft_serve")]})
    SCOOPS[f"{path}_ice_cream"] = [col[0] << 16 | col[1] << 8 | col[2], 0, name]; SCOOPS[f"{path}_soft_serve"] = [col[0] << 16 | col[1] << 8 | col[2], 1, name]
for _x, _t, _c in (("honey", "Honey", (246, 190, 64)), ("chocolate", "Chocolate", (112, 70, 46))):
    add_food(f"{_x}_soft_serve", f"{_t} Soft Serve", None, dict(nutrition=6, saturation=0.6, eat=1.6, stack=16, taste=86))
    recipe(f"{_x}_soft_serve", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item(f"{MOD}:{_x}_ice_cream"), SNOW], "result": result(f"{MOD}:{_x}_soft_serve")})
    ICE.append(f"{MOD}:{_x}_soft_serve")
    recipe(f"{_x}_soft_serve_freezer", {"type": f"{MOD}:freezing", "category": "food", "ingredient": item(f"{MOD}:{_x}_ice_cream"), "result": result(f"{MOD}:{_x}_soft_serve"), "experience": 0.0, "cookingtime": 100})
    crec(f"{_x}_soft_serve_deployer", CR, {"type": "create:deploying", "ingredients": [item(f"{MOD}:{_x}_ice_cream"), SNOW], "results": [result(f"{MOD}:{_x}_soft_serve")]})
    SCOOPS[f"{_x}_ice_cream"] = [_c[0] << 16 | _c[1] << 8 | _c[2], 0, _t]; SCOOPS[f"{_x}_soft_serve"] = [_c[0] << 16 | _c[1] << 8 | _c[2], 1, _t]
crec("wafer_cone_saw", CR, {"type": "create:cutting", "ingredients": [FLAT], "processing_time": 40, "results": [result(f"{MOD}:wafer_cone", 3)]})
save(ROOT / "alterra_kitchen_scoops.json", SCOOPS)
save(DATA / "recipe/ice_cream_stack.json", {"type": f"{MOD}:ice_cream_stack", "category": "food"})
(ASSETS / "textures/item/stack").mkdir(parents=True, exist_ok=True); (ASSETS / "models/item/stack").mkdir(parents=True, exist_ok=True)
_over = []
for _n in range(1, 6):
    edge_dark(treat_art.cone_layer(_n)).save(ASSETS / f"textures/item/stack/cone_{_n}.png")
    for _i in range(_n):
        for _sw in (0, 1): edge_dark(treat_art.scoop_layer(_n, _i, bool(_sw))).save(ASSETS / f"textures/item/stack/scoop_{_n}_{_i}_{_sw}.png")
        edge_dark(treat_art.twist_layer(_n, _i)).save(ASSETS / f"textures/item/stack/twist_{_n}_{_i}.png")
    for _t in range(3 ** _n):   # each scoop is round (0), a swirl (1) or a twist (2); layers: cone, then a scoop and (for a twist) its second colour
        _code = _t * 10 + _n; _tex = {"layer0": f"{MOD}:item/stack/cone_{_n}"}
        for _i in range(_n):
            _d = _t // 3 ** _i % 3
            _tex[f"layer{2 * _i + 1}"] = f"{MOD}:item/stack/scoop_{_n}_{_i}_{min(_d, 1)}"
            if _d == 2: _tex[f"layer{2 * _i + 2}"] = f"{MOD}:item/stack/twist_{_n}_{_i}"
        save(ASSETS / f"models/item/stack/s{_code}.json", {"parent": "minecraft:item/generated", "textures": _tex})
        _over.append((_code, {"predicate": {"custom_model_data": _code}, "model": f"{MOD}:item/stack/s{_code}"}))
save(ASSETS / "models/item/ice_cream_stack.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/stack/cone_1", "layer1": f"{MOD}:item/stack/scoop_1_0_0"}, "overrides": [o for _c, o in sorted(_over)]})
save(DATA / "tags/item/ice_creams.json", {"values": ICE})
recipe("sundae", {"type": "minecraft:crafting_shapeless", "category": "food", "ingredients": [item("minecraft:bowl"), T(f"{MOD}:ice_creams"), T(f"{MOD}:ice_creams"), T(f"{MOD}:sauces")], "result": result(f"{MOD}:sundae")})
# cold food cools and quenches a little (Legendary Survival Overhaul's data of our built-in packs; the tooltip reads it too)
for kind_, packs_ in (("thirst", "lso_drinks"), ("temperature", "lso_temp")):
    (ROOT / f"resourcepacks/{packs_}/data/{MOD}/legendarysurvivaloverhaul/{kind_}/consumables").mkdir(parents=True, exist_ok=True)
cold = [f"{p_}_ice_cream" for _f, p_, _n, _c in FRUITS] + [f"{p_}_popsicle" for _f, p_, _n, _c in FRUITS] + [f"{p_}_soft_serve" for _f, p_, _n, _c in FRUITS] + ["honey_ice_cream", "chocolate_ice_cream", "honey_soft_serve", "chocolate_soft_serve", "ice_cream_stack", "ice_cream_sandwich", "sundae"]
for cid in cold:
    hyd = 3 if cid.endswith("_popsicle") else 1
    save(ROOT / f"resourcepacks/lso_drinks/data/{MOD}/legendarysurvivaloverhaul/thirst/consumables/{cid}.json", [{"effects": [], "hydration": hyd, "properties": {}, "saturation": 1.0}])
    save(ROOT / f"resourcepacks/lso_temp/data/{MOD}/legendarysurvivaloverhaul/temperature/consumables/{cid}.json", [{"duration": 1200, "group": "DRINK", "temperature_level": -1}])

save(ROOT / "alterra_kitchen_foods.json", foods)
print(f"{len(foods)} foods, {len(list((DATA / 'recipe/food').glob('*.json')))} recipes")

# ---- names the game needs that the core's language file does not have. The core regenerates kitchen's en_us.json on every full build, so they are added here, after it.
LANG_EXTRA = {
    "stove": "Stove", "oven": "Oven", "kitchen_counter": "Kitchen Counter", "prep_table": "Prep Table", "ice_cream_stand": "Ice Cream Stand", "ice_machine": "Ice Cream Machine", "powered_ice_machine": "Powered Ice Cream Machine", "drink_mixer": "Drink Mixer",
    "rock_salt": "Rock Salt", "poison_fern": "Fiddlehead Fern", "nightshade": "Duskberry Bush", "hemlock": "Wild Parsley", "deathcap": "Meadow Cap", "oleander": "Rosebay", "fruit_press": "Fruit Press",
}
SECTION_EXTRA = {"preserves": "Jams & Preserves", "baking": "Baking", "frozen": "Frozen Treats", "meals": "Meals", "pantry": "Pantry", "seasonings": "Seasonings", "wild": "Wild Plants & Salt", "dishes": "Dishes", "stations": "Kitchen Stations", "decor": "Crates & Decor"}
_lp = ASSETS / "lang/en_us.json"; _lang = json.loads(_lp.read_text()) if _lp.exists() else {}
for _k, _v in LANG_EXTRA.items(): _lang[f"block.{MOD}.{_k}"] = _v
for _k, _v in SECTION_EXTRA.items(): _lang[f"section.{MOD}.{_k}"] = _v
_lp.parent.mkdir(parents=True, exist_ok=True); _lp.write_text(json.dumps(_lang, indent=2, ensure_ascii=False) + "\n")

# the Drink Mixer's drinks quench thirst like the other mixes (Legendary Survival Overhaul)
save(ROOT / f"resourcepacks/lso_drinks/data/{MOD}/legendarysurvivaloverhaul/thirst/consumables/mixed_drink.json", [{"effects": [], "hydration": 9, "properties": {}, "saturation": 4.0}])
