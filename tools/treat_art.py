"""Hand-built sprites for the kitchen's fruit foods, frozen treats, seasonings and bakes, drawn from shapes with tools/pixel_kit.py. Each fruit has a kind (round, citrus, wedge, strip, berry) that decides the shape
of its slices and dried pieces; the colour comes from the fruit. overrides(fruits) gives {food id: Image} for the generator to use instead of its simpler pictures."""
import math, random
from pixel_kit import Sp, ramp, mix, clamp

ARCH = {"sun_apple": "round", "sour_apple": "round", "frost_apple": "round", "ember_apple": "round", "apple": "round", "peach": "round", "plum": "round", "ember_plum": "round", "ember_pear": "round", "frost_pear": "round",
        "frost_plum": "round", "gilded_peach": "round", "pomegranate": "round", "fig": "round", "baobab": "round", "orange": "citrus", "honeymelon": "wedge", "mango": "strip", "prickly_pear": "strip",
        "strawberry": "berry", "blueberry": "berry", "raspberry": "berry", "blackberry": "berry", "redcurrant": "berry", "grapes": "berry", "white_grapes": "berry", "cherry": "berry"}
CREAM = (250, 240, 214); WOOD = (176, 128, 74); GREEN = (84, 160, 70)
def flesh_of(col): return mix(col, CREAM, 0.62)
def seed(): return (60, 40, 30)

# ---------------------------------------------------------------- slices and dried pieces
def slice_sprite(arch, col):
    s = Sp()
    if arch == "round":
        s.ball(8, 8, 6.4, col, hi=False); f = flesh_of(col); s.ball(8, 8, 5.2, f, hi=False)
        s.ball(8, 8, 1.7, mix(f, (255, 255, 255), 0.4), hi=False)
        for dx, dy in ((-0.6, -0.4), (0.6, 0.4), (0, 0.9)): s.put(8 + dx, 8 + dy, seed())
    elif arch == "citrus":
        s.ball(8, 8, 6.6, col, hi=False); s.ball(8, 8, 5.7, (252, 244, 224), hi=False); fl = mix(col, (255, 230, 150), 0.35); s.ball(8, 8, 4.9, fl, hi=False)
        for a in range(0, 360, 45):
            s.line(8, 8, 8 + math.cos(math.radians(a)) * 4.8, 8 + math.sin(math.radians(a)) * 4.8, (252, 244, 224))
        s.put(8, 8, (252, 244, 224))
    elif arch == "wedge":   # a melon wedge: the rind along the bottom, flesh above, seeds in a row
        rind = (86, 150, 70); f = col
        for y in range(16):
            for x in range(16):
                dx, dy = (x + 0.5 - 8) / 7.2, (y + 0.5 - 5.5) / 6.4
                if dx * dx + dy * dy <= 1 and y >= 5:
                    d = math.hypot(dx, dy)
                    s.put(x, y, rind if d > 0.9 else (mix(col, CREAM, 0.7) if d > 0.8 else f))
        s.line(2, 5, 14, 5, ramp(col)[3])
        for x in (5, 8, 11): s.put(x, 7, seed()); 
    elif arch == "strip":   # a scored cheek: the flesh cut in a grid, the skin showing round the bottom
        skin = mix(col, (200, 70, 40), 0.55)
        for y in range(16):
            for x in range(16):
                dx, dy = (x + 0.5 - 8) / 6.8, (y + 0.5 - 8.2) / 4.9
                if dx * dx + dy * dy <= 1:
                    d = math.hypot(dx, dy)
                    s.put(x, y, skin if (dy > 0.55 and d > 0.78) else (ramp(col)[3] if dx + dy < -0.3 else ramp(col)[2]))
        for k in range(-2, 3):
            for t in range(-6, 7):
                x, y = 8 + t * 0.5 + k * 2.2, 8 + t * 0.5 - k * 0.0
        for gx in (4.5, 7, 9.5, 12): s.line(gx, 4.2, gx - 0.8, 10.2, ramp(col)[1])   # the cuts, down
        for gy in (6, 8.2): s.line(2.6, gy, 13.4, gy, ramp(col)[1])   # and across
        s.put(5, 5, ramp(col)[4]); s.put(8, 5.4, ramp(col)[4])
    else:   # berry: two halves with the pale middle and the pips
        for cx, cy in ((5.2, 9), (10.6, 7)):
            s.ball(cx, cy, 3.9, col, hi=False); s.ball(cx, cy, 2.9, mix(col, CREAM, 0.55), hi=False)
            for dx, dy in ((-0.8, -0.4), (0.8, 0.2), (0, 1.0)): s.put(cx + dx, cy + dy, ramp(col)[1])
        s.put(5, 4, GREEN); s.put(6, 4, GREEN); s.put(11, 2, GREEN); s.put(10, 2, GREEN)
    s.rim(); s.outline(); return s.image()

def dried_sprite(arch, col):
    dark = mix(col, (70, 44, 28), 0.55); s = Sp(); r = random.Random(sum(map(ord, arch + str(col))))
    if arch == "round" or arch == "citrus":   # a dried ring (apple) or a wheel (citrus)
        s.ball(8, 8, 6.2, dark, hi=False); s.ball(8, 8, 1.8, (0, 0, 0), hi=False)
        for y in range(16):
            for x in range(16):
                if s.at(x, y) == (0, 0, 0): s.g[y][x] = None
        for a in range(0, 360, 36): s.line(8 + math.cos(math.radians(a)) * 2.4, 8 + math.sin(math.radians(a)) * 2.4, 8 + math.cos(math.radians(a)) * 5.6, 8 + math.sin(math.radians(a)) * 5.6, ramp(dark)[1])
    elif arch == "berry":   # a heap of raisins
        for cx, cy in ((5, 10), (10, 10), (7.5, 6), (12, 5.5), (4.5, 5)): s.ball(cx, cy, 2.6, dark, sy=0.8)
    else:   # leathery folded strips
        for k in range(3):
            y0 = 4 + k * 3.4
            for x in range(2, 14):
                y = y0 + 1.4 * math.sin(x * 0.7 + k * 2); s.put(x, y, ramp(dark)[2 if (x + k) % 3 else 3]); s.put(x, y + 1, ramp(dark)[1])
    for _ in range(6):   # a dusting of sugar
        x, y = r.randint(3, 12), r.randint(3, 12)
        if s.at(x, y): s.put(x, y, (250, 244, 230))
    s.rim(); s.outline(); return s.image()

# ---------------------------------------------------------------- cups, scoops, bars, pies
def glass_cup(s):
    gl = (196, 224, 240)
    for y in range(8, 12):
        w = 6 - (y - 8) // 2
        for x in range(8 - w, 8 + w): s.put(x, y, gl if (x + y) % 5 else (232, 246, 252))
    s.rect(7, 12, 8, 13, gl); s.rect(5, 14, 10, 14, gl)
def sorbet_sprite(col):
    s = Sp(); glass_cup(s)
    s.ball(5.8, 7.2, 3.6, col); s.ball(10.2, 7.2, 3.6, mix(col, (255, 255, 255), 0.3)); s.ball(8, 4.6, 3.4, col)
    s.put(7, 1.4, (60, 150, 70)); s.put(8.4, 0.8, (60, 150, 70)); s.put(8, 1.8, (36, 110, 50))   # a mint leaf
    s.rim(); s.outline(); return s.image()
def bar_sprite(col):
    s = Sp(); r = random.Random(sum(col))
    t = ramp((206, 160, 92))
    s.rect(2, 5, 13, 11, t[2]); s.rect(2, 5, 13, 5, t[3]); s.rect(2, 11, 13, 11, t[1])
    for _ in range(14):
        x, y = r.randint(3, 12), r.randint(6, 10); s.put(x, y, t[4] if r.random() < 0.5 else t[1])   # oats
    for x in range(2, 14):   # a glaze drizzled across, a dribble over the edge
        y = 7 + round(math.sin(x * 0.9)); s.put(x, y, ramp(col)[3]); s.put(x, y + 1, ramp(col)[2])
    s.put(5, 12, ramp(col)[2]); s.put(10, 12, ramp(col)[2]); s.put(5, 13, ramp(col)[1])
    for bx, by in ((4, 7), (9, 9), (11, 6)): s.put(bx, by, ramp(col)[1])   # bits of dried fruit
    s.rim(); s.outline(); return s.image()
def pie_sprite(arch, col):
    s = Sp(); CR = (214, 160, 88)
    s.ellipse(8, 9, 7.4, 5.2, (156, 102, 52)); s.ellipse(8, 8.7, 6.7, 4.6, CR)
    s.ellipse(8, 8.5, 5.0, 3.2, ramp(col)[2])   # the filling
    if arch == "berry":   # crumble over the top, the filling showing through
        r = random.Random(sum(col))
        for _ in range(26):
            x, y = r.randint(3, 12), r.randint(6, 11)
            if s.at(x, y): s.put(x, y, ramp(CR)[3 if r.random() < 0.5 else 2])
    else:   # a lattice of pastry strips, spaced so the filling shows in the diamonds between them
        for k in (-3.4, 0, 3.4):
            for t in range(-7, 8):
                for sgn, lvl in ((1, 3), (-1, 2)):
                    x, y = 8 + t, 8.5 + sgn * t * 0.5 + k
                    if ((x - 8) / 5.6) ** 2 + ((y - 8.5) / 3.6) ** 2 < 1: s.put(x, y, ramp(CR)[lvl])
    for a in range(0, 360, 22): s.put(8 + math.cos(math.radians(a)) * 7.0, 9 + math.sin(math.radians(a)) * 4.7, ramp(CR)[3] if (a // 22) % 2 else (176, 120, 58))   # the crimped rim
    s.rim(); s.outline(); return s.image()

# ---------------------------------------------------------------- frozen treats
def cone(s, scoop_cols, sprinkles=False, cherry=False):
    wafer = ramp((222, 176, 104))
    for y in range(7, 15):   # the cone, narrowing to a point, in a waffle pattern, no wider than the scoop
        w = max(0, 3.9 - (y - 7) * 0.5)
        for x in range(int(8 - w), int(8 + w) + 1):
            s.put(x, y, wafer[1] if (x + y) % 3 == 0 else (wafer[3] if (x + y) % 3 == 1 else wafer[2]))
    for i, c in enumerate(scoop_cols):
        cy = 5.4 - i * 3.4; s.ball(8, cy, 4.1 - i * 0.3, c)
    top = 5.4 - (len(scoop_cols) - 1) * 3.4
    t = ramp(scoop_cols[0]); s.put(5, 7.8, t[2]); s.put(5, 8.6, t[2]); s.put(11, 7.6, t[2])   # drips over the rim
    if sprinkles:
        for dx, dy, c in ((-1.5, -1, (255, 90, 120)), (1.5, -0.5, (255, 255, 255)), (0, 1.5, (90, 190, 255)), (-2, 1, (255, 220, 80)), (2, 1.5, (255, 90, 120))): s.put(8 + dx, top + dy, c)
    if cherry: s.ball(8, top - 3.4, 1.7, (200, 36, 52)); s.put(8, top - 5.4, (80, 130, 60)); s.put(9, top - 6, (80, 130, 60))
def ice_cream_sprite(col, kind=None):
    s = Sp(); cone(s, [col], sprinkles=(kind == "sprinkle")); s.rim(); s.outline(); return s.image()
def popsicle_sprite(col):
    s = Sp(); t = ramp(col); wood = ramp((196, 150, 86))
    s.rect(7, 11, 8, 15, wood[2]); s.put(7, 15, wood[1]); s.put(8, 15, wood[1])
    for y in range(1, 12):   # a rounded bar that is lighter at the top
        for x in range(4, 12):
            if y == 1 and x in (4, 11): continue
            s.put(x, y, t[3] if y < 4 else (t[2] if y < 8 else t[1]))
    s.rect(5, 2, 5, 9, t[4]); s.put(6, 1, t[4])   # a glint down the left
    s.put(11, 1, None); s.put(11, 2, None); s.put(10, 1, None)   # a bite out of the corner
    s.put(8, 12, t[2]); s.put(5, 12, t[1])   # a drip
    s.rim(); s.outline(); return s.image()
def sundae_sprite(col):
    s = Sp(); glass_cup(s)
    s.ball(5.6, 7.4, 3.4, col); s.ball(10.4, 7.4, 3.4, (252, 232, 190)); s.ball(8, 4.8, 3.2, mix(col, (255, 255, 255), 0.25))
    s.ball(8, 3.2, 2.0, (252, 252, 248)); s.ball(8, 1.9, 1.3, (200, 36, 52), hi=True)   # cream and a cherry
    s.rect(11, 3, 11, 7, (222, 176, 104)); s.put(12, 4, (222, 176, 104))   # a wafer
    s.rim(); s.outline(); return s.image()
def sandwich_sprite(col):
    s = Sp(); cookie = ramp((120, 74, 48))
    for y0 in (3, 10):
        s.rect(2, y0, 13, y0 + 2, cookie[2]); s.rect(2, y0, 13, y0, cookie[3]); s.rect(2, y0 + 2, 13, y0 + 2, cookie[1])
        for x in (4, 7, 10): s.put(x, y0 + 1, cookie[0])
    t = ramp(col); s.rect(2, 6, 13, 9, t[2]); s.rect(2, 6, 13, 6, t[3]); s.put(4, 10, t[2]); s.put(11, 10, t[1])
    s.rim(); s.outline(); return s.image()


# ---------------------------------------------------------------- seasonings: different containers, not the same cone in different colours
def shaker(col, cap=(150, 150, 160)):
    s = Sp(); gl = (200, 226, 240)
    s.rect(5, 5, 10, 14, gl)
    t = ramp(col); s.rect(6, 7, 9, 13, t[2]); s.rect(6, 7, 6, 13, t[3]); s.rect(9, 7, 9, 13, t[1])   # the powder inside
    s.rect(5, 2, 10, 4, cap); s.rect(5, 2, 10, 2, (206, 206, 214)); s.rect(5, 4, 10, 4, (104, 104, 114))
    for x in (6, 8): s.put(x, 3, (60, 60, 70))
    s.put(5, 6, (250, 252, 255)); s.put(5, 7, (250, 252, 255))
    s.rim(); s.outline(); return s.image()
def pouch(col, string=(196, 60, 52)):
    s = Sp(); cloth = ramp((218, 196, 150)); t = ramp(col)
    for y in range(5, 15):
        w = 3 + min(3, (y - 5) * 0.8)
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, cloth[2] if x > 8 - w + 1 else cloth[3])
    s.rect(5, 4, 10, 5, cloth[3]); s.line(5, 6, 10, 6, string); s.put(10, 7, string); s.put(11, 8, string)   # the drawstring tied at the neck
    s.rect(6, 1, 9, 3, t[2]); s.rect(6, 1, 7, 2, t[3]); s.put(7, 0, t[3]); s.put(8, 1, t[4])   # the dust heaped from the open top
    s.put(7, 10, t[2]); s.put(8, 11, t[2]); s.put(9, 10, t[1])   # a smear of it on the cloth
    s.rim(); s.outline(); return s.image()
def vial(col):
    s = Sp(); t = ramp(col); gl = (206, 230, 244)
    s.rect(6, 5, 9, 14, gl); s.rect(7, 7, 8, 13, t[3]); s.put(7, 8, t[4]); s.put(8, 10, t[4]); s.put(7, 12, t[4])
    s.rect(6, 2, 9, 4, (170, 124, 78)); s.rect(6, 2, 9, 2, (200, 156, 100))
    s.put(5, 7, (252, 252, 255))
    for dx, dy in ((-2, 4), (3, 3), (4, 9), (-3, 10)): s.put(8 + dx, dy, mix(col, (255, 255, 255), 0.5))   # a glow round it
    s.rim(); s.outline(); return s.image()
def shards(col):
    """Crystals standing apart in a cluster, each with a lit left face and a shadowed right face, a dark line between them."""
    s = Sp(); t = ramp(col)
    for cx, base, h in ((3.5, 14, 7), (7, 14, 11), (11, 14, 8), (14, 14, 4)):
        for y in range(int(base - h), base + 1):
            tip = y < base - h + 3
            for x in ((cx,) if tip else (cx - 1, cx, cx + 1)): s.put(x, y, t[3] if x < cx else (t[1] if x > cx else t[2]))
        s.put(cx - 1, base - h + 4, t[4])
    s.rim(); s.outline(); return s.image()
def mortar(col):
    s = Sp(); stone = ramp((150, 148, 156)); t = ramp(col)
    for y in range(8, 14):
        w = 6 - (13 - y) * 0.3
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, stone[3] if x < 7 else (stone[1] if x > 10 else stone[2]))
    s.rect(4, 14, 11, 14, stone[1]); s.rect(3, 7, 12, 8, stone[3])
    s.rect(5, 5, 10, 7, t[2]); s.rect(5, 5, 7, 6, t[3]); s.put(6, 4, t[3]); s.put(9, 5, t[4]); s.put(8, 4, t[2])   # the flakes heaped in it
    s.line(10, 2, 13, 6, (176, 128, 74)); s.put(14, 7, (150, 104, 56))   # a pestle
    s.rim(); s.outline(); return s.image()
def peel(col):
    s = Sp(); t = ramp(col)
    for k, (cx, cy) in enumerate(((5, 10), (10, 7), (7, 4))):   # curls of peel
        for a in range(0, 330, 12):
            r = 1.2 + a / 330 * 2.8
            s.put(cx + math.cos(math.radians(a)) * r, cy + math.sin(math.radians(a)) * r * 0.8, t[3] if (a // 40) % 2 else t[2])
        s.put(cx, cy, t[1])
    s.rim(); s.outline(); return s.image()
def bundle(col):
    """A bunch of herb sprigs tied with red thread, grains of salt scattered round the bottom."""
    s = Sp(); g = ramp((110, 176, 90)); t = ramp(col)
    for a_, ln in ((-30, 10), (-12, 12), (6, 12), (24, 10)):
        for i in range(2, ln):
            x, y = 8 + math.sin(math.radians(a_)) * i * 0.8, 13 - math.cos(math.radians(a_)) * i
            s.put(x, y, g[2]); s.put(x - 1, y, g[3]); s.put(x + 1, y, g[3] if i % 2 else g[1])   # leaves either side of each stem
    s.rect(6, 11, 9, 12, (200, 60, 52)); s.put(10, 12, (200, 60, 52)); s.put(10, 13, (200, 60, 52))
    for x, y in ((3, 13), (12, 13), (5, 14), (10, 14)): s.put(x, y, t[3]); s.put(x + 1, y, t[4])
    s.rim(); s.outline(); return s.image()
def dish(col):
    s = Sp(); wood = ramp((176, 128, 74)); t = ramp(col)
    for y in range(8, 13):
        w = 6 - (12 - y) * 0.3
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, wood[3] if x < 6 else (wood[1] if x > 10 else wood[2]))
    s.rect(3, 7, 12, 7, wood[3]); s.rect(3, 6, 12, 6, wood[2])
    s.rect(4, 4, 11, 6, t[2]); s.rect(4, 4, 7, 5, t[3]); s.put(6, 3, t[3]); s.put(9, 4, t[4]); s.put(10, 3, t[2])
    s.line(2, 2, 6, 4, (40, 96, 70)); s.put(1, 1, (40, 96, 70))   # a strand of kelp
    s.rim(); s.outline(); return s.image()
SEASONINGS = {"salt": lambda: shaker((246, 246, 242)), "onion_salt": lambda: shaker((232, 208, 166)), "smoked_salt": lambda: shards((168, 130, 100)), "frost_crystals": lambda: shards((150, 214, 240)),
              "ember_flakes": lambda: mortar((206, 56, 36)), "autumn_spice": lambda: mortar((192, 110, 52)), "sun_zest": lambda: peel((246, 200, 70)), "tart_zest": lambda: peel((164, 212, 84)),
              "herb_salt": lambda: bundle((244, 244, 240)), "kelp_salt": lambda: dish((92, 140, 110)), "cocoa_sugar": lambda: pouch((122, 78, 52)), "berry_dust": lambda: pouch((176, 62, 112)), "glow_spice": lambda: vial((252, 214, 100))}

# ---------------------------------------------------------------- bakes: three breads that no longer look alike
def flatbread_sprite():
    s = Sp(); t = ramp((226, 184, 112)); r = random.Random(3)
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 7.2, (y + 0.5 - 8.4) / 4.6 - 0.18 * ((x - 8) / 7.2)   # a puffed, slightly lopsided round
            if dx * dx + dy * dy <= 1: s.put(x, y, t[3] if dx + dy < -0.3 else (t[2] if dx + dy < 0.6 else t[1]))
    for x, y in ((4, 7), (7, 9), (10, 7), (12, 10), (6, 11)): s.put(x, y, (130, 80, 40)); s.put(x + 1, y, (170, 110, 56))   # char marks
    s.rim(); s.outline(); return s.image()
def tortilla_sprite():
    s = Sp(); t = ramp((240, 220, 166)); r = random.Random(11)
    s.ellipse(8, 8.6, 7.2, 5.0, (240, 220, 166))
    for _ in range(9):   # toasted blotches
        x, y = r.randint(3, 12), r.randint(6, 11)
        if s.at(x, y): s.put(x, y, (200, 150, 84)); s.put(x + 1, y, (216, 172, 104))
    s.line(2, 8.6, 5, 7.4, t[4])   # the edge catches the light
    s.rim(); s.outline(); return s.image()
def bun_sprite():
    s = Sp(); t = ramp((214, 150, 70))
    for y in range(16):   # a domed bun, flat underneath, with seeds on top
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 7.4, (y + 0.5 - 11) / 7.2
            if dx * dx + dy * dy <= 1 and y <= 12: s.put(x, y, t[3] if dx + dy < -0.5 else (t[2] if dx + dy < 0.3 else t[1]))
    s.rect(2, 12, 13, 13, t[1]); s.line(2, 13, 13, 13, ramp(t[1])[1])
    for x, y in ((6, 5), (9, 4), (11, 6), (8, 7), (5, 8)): s.put(x, y, (250, 240, 214))
    s.rim(); s.outline(); return s.image()

# ---------------------------------------------------------------- Alex's Mobs dishes that did not read
def lobster_sprite():
    s = Sp(); red = (206, 62, 40); t = ramp(red)
    s.ellipse(8, 8.5, 3.4, 5.4, red)   # the body and its segments
    for y in (6, 8, 10, 12): s.line(5, y, 11, y, t[1])
    s.ellipse(8, 13.5, 3.8, 1.8, mix(red, (255, 140, 80), 0.2))   # the tail fan
    for sd in (-1, 1):   # the claws, a big pincer each side
        cx = 8 + sd * 5.2; s.ellipse(cx, 4, 2.2, 2.8, red); s.put(cx - sd * 0.5, 1.4, t[0]); s.put(cx + sd * 0.8, 1.6, t[1]); s.line(8 + sd * 3, 6.5, cx, 5.5, red)
    for sd in (-1, 1):   # legs and antennae
        for k, y in enumerate((8, 10, 12)): s.put(8 + sd * 4.2, y, t[1]); s.put(8 + sd * 5, y + 0.6, t[1])
        s.line(8 + sd * 1, 3, 8 + sd * 3.5, 0, t[1])
    s.put(7, 4, (20, 20, 24)); s.put(9, 4, (20, 20, 24))
    s.rim(); s.outline(); return s.image()
def catfish_sprite():
    s = Sp()
    s.ellipse(8, 9.6, 7.6, 5.2, (206, 222, 238)); s.ellipse(8, 9.4, 6.6, 4.3, (226, 238, 248))   # a cool grey-blue plate, so the fish stands out
    fish = (190, 120, 58); t = ramp(fish)
    s.ellipse(7.2, 9, 5.6, 3.2, fish)
    for x in range(3, 11): s.put(x, 11, t[1]); s.put(x, 7, t[3])
    for k, (x, y) in enumerate(((12, 6), (13, 6), (13, 7), (12, 8), (13, 10), (12, 11), (13, 12), (13, 11))): s.put(x, y, t[2] if k % 2 else t[3])   # the forked tail
    s.line(1.5, 8, 3.5, 8.6, (40, 26, 20)); s.line(1.5, 10.4, 3.5, 9.6, (40, 26, 20)); s.line(2.5, 7, 3.8, 8, (40, 26, 20))   # long whiskers
    s.put(4, 8.4, (255, 255, 255)); s.put(4, 8.9, (20, 16, 16))   # the eye
    for x, y in ((6.4, 8.4), (8.8, 9.4), (7.0, 10.4)): s.ball(x, y, 1.0, (140, 46, 120))   # plum sauce
    s.rim(); s.outline(); return s.image()
def stew_sprite(bowl_col, broth, chunks, bits):
    s = Sp(); b = ramp(bowl_col); r = random.Random(sum(broth))
    for y in range(8, 14):
        w = 7 - (13 - y) * 0.45
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, b[3] if x < 5 else (b[1] if x > 11 else b[2]))
    s.rect(1, 7, 14, 8, b[3]); s.rect(1, 8, 14, 8, b[2])
    s.ellipse(8, 6.2, 6.4, 2.6, broth)
    for _ in range(5): x, y = r.randint(4, 11), r.randint(4, 7); s.ball(x, y, 1.4, chunks, hi=False)
    for _ in range(4): x, y = r.randint(3, 12), r.randint(4, 8); s.put(x, y, bits)
    for k in range(2): s.put(5 + k * 5, 2, (240, 240, 244)); s.put(6 + k * 5, 1, (240, 240, 244))   # steam
    s.rim(); s.outline(); return s.image()
def salad_sprite():
    s = Sp(); b = ramp((236, 226, 204)); g = ramp((92, 160, 74))
    for y in range(8, 14):
        w = 7 - (13 - y) * 0.45
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, b[3] if x < 5 else (b[1] if x > 11 else b[2]))
    s.rect(1, 7, 14, 8, b[3])
    for cx, cy in ((4, 6), (7, 4), (10, 5), (12, 7), (6, 7)): s.ball(cx, cy, 2.4, (96, 164, 78))
    s.ellipse(9, 4.6, 3.2, 1.8, (210, 70, 50))   # a piece of lobster tail
    for x, y in ((5, 5), (11, 4), (8, 7)): s.put(x, y, (250, 190, 60))   # citrus bits
    s.rim(); s.outline(); return s.image()

# ---------------------------------------------------------------- the rest of the frozen treats
def special_ice_cream(col, sprinkles=False):
    s = Sp(); cone(s, [col], sprinkles=sprinkles); s.rim(); s.outline(); return s.image()
def wafer_cone_sprite():
    s = Sp(); wafer = ramp((222, 176, 104))
    for y in range(2, 15):
        w = max(0, 5.5 - (y - 2) * 0.42)
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, wafer[1] if (x + y) % 3 == 0 else (wafer[3] if (x + y) % 3 == 1 else wafer[2]))
    s.rect(3, 2, 12, 2, wafer[3]); s.rect(3, 3, 12, 3, wafer[2])
    s.rim(); s.outline(); return s.image()

# ---------------------------------------------------------------- stacked ice cream: a cone and up to five scoops, each round or a soft-serve swirl; the scoop pictures are grey, the game tints each with its flavour
GREY = (232, 232, 232)
def layout(n):
    """Cone height, scoop radius, the first scoop's centre and the step up for each next one, for n scoops."""
    ch = 7 if n == 1 else (6 if n <= 3 else 5)
    r = 4.1 - 0.4 * (n - 1); cone_top = 16 - ch; cy0 = cone_top - r * 0.3
    step = 0 if n == 1 else (cy0 - r * 0.95) / (n - 1)
    return ch, r, cy0, step
def cone_layer(n):
    s = Sp(); ch = layout(n)[0]; wafer = ramp((222, 176, 104))
    for y in range(16 - ch, 15):
        w = max(0, 3.9 - (y - (16 - ch)) * (3.9 / (ch - 1)))
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, wafer[1] if (x + y) % 3 == 0 else (wafer[3] if (x + y) % 3 == 1 else wafer[2]))
    s.rect(4, 16 - ch, 11, 16 - ch, wafer[3])
    s.rim(); s.outline(); return s.image()
def scoop_layer(n, i, swirl):
    s = Sp(); ch, r, cy0, step = layout(n); cy = cy0 - i * step
    if not swirl:
        s.ball(8, cy, r, GREY)
    else:   # a soft-serve swirl: three tiers narrowing to a curled tip, with a groove between them
        t = ramp(GREY)
        for k, (dy, rx, ry, dx) in enumerate(((0.9, r * 1.0, r * 0.52, 0), (-0.5, r * 0.78, r * 0.48, 0.5), (-1.7, r * 0.5, r * 0.42, 0))):
            s.ellipse(8 + dx, cy + dy, rx, ry, (GREY if k % 2 == 0 else (214, 214, 214)))
        s.put(8.8, cy - 2.6, t[3]); s.put(9.4, cy - 2.2, t[2])
    # a lit edge and a shadowed one, then a dark outline of its own so the scoops separate when they overlap
    s.rim(); s.outline((72, 72, 72)); return s.image()
def twist_layer(n, i):
    """Every other diagonal band of a swirl scoop (outline pixels left alone), painted over it in the twist's second flavour."""
    im = scoop_layer(n, i, True).copy(); px = im.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a == 0 or r < 110 or ((x + 2 * y) // 3) % 2 == 0: px[x, y] = (0, 0, 0, 0)
    return im
def soft_serve_sprite(col):
    s = Sp(); cone(s, [col]); s.g = [[None] * 16 for _ in range(16)]
    wafer = ramp((222, 176, 104))
    for y in range(8, 15):
        w = max(0, 3.9 - (y - 8) * 0.55)
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, wafer[1] if (x + y) % 3 == 0 else (wafer[3] if (x + y) % 3 == 1 else wafer[2]))
    t = ramp(col)
    for k, (dy, rx, ry, dx) in enumerate(((7.4, 4.1, 2.0, 0), (5.0, 3.4, 1.8, 0.4), (2.8, 2.3, 1.6, 0))):   # three tiers and a curled tip
        s.ellipse(8 + dx, dy, rx, ry, col if k % 2 == 0 else mix(col, (255, 255, 255), 0.12))
    s.put(8.6, 1.3, t[3]); s.put(9.4, 1.7, t[2]); s.put(8, 1.9, t[3])
    s.rim(); s.outline(); return s.image()

def overrides(fruits):
    out = {}
    for item, path, name, col in fruits:
        arch = ARCH.get(path, "round"); c = tuple(col)
        out[f"{path}_slices"] = slice_sprite(arch, c); out[f"{path}_dried"] = dried_sprite(arch, c); out[f"{path}_sorbet"] = sorbet_sprite(c)
        out[f"{path}_bar"] = bar_sprite(c); out[f"{path}_fruit_pie"] = pie_sprite(arch, c)
        out[f"{path}_ice_cream"] = ice_cream_sprite(c); out[f"{path}_popsicle"] = popsicle_sprite(c)
        out[f"{path}_dust"] = pouch(mix(c, (255, 255, 255), 0.12)); out[f"{path}_soft_serve"] = soft_serve_sprite(c)
    import fruit_food_art
    for item, path, name, col in fruits: out.update(fruit_food_art.all_for(path, tuple(col)))   # the fruit foods that carry a picture of their fruit
    for k, f in SEASONINGS.items(): out[k] = f()
    out.update({"flatbread": flatbread_sprite(), "tortilla": tortilla_sprite(), "bun": bun_sprite(), "citrus_lobster": lobster_sprite(), "catfish_with_plum_sauce": catfish_sprite(),
                "moose_stew": stew_sprite((150, 104, 62), (130, 78, 44), (96, 50, 34), (240, 150, 50)), "kangaroo_stew": stew_sprite((232, 220, 196), (178, 88, 52), (150, 70, 52), (240, 190, 70)),
                "lobster_orchard_salad": salad_sprite(), "honey_ice_cream": special_ice_cream((246, 190, 64)), "honey_soft_serve": soft_serve_sprite((246, 190, 64)), "chocolate_soft_serve": soft_serve_sprite((112, 70, 46)), "chocolate_ice_cream": special_ice_cream((112, 70, 46), True),
                "ice_cream_sandwich": sandwich_sprite((252, 238, 200)), "sundae": sundae_sprite((190, 60, 80)), "wafer_cone": wafer_cone_sprite()})
    return out
