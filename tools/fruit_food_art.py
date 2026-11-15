"""Fruit foods with the fruit on them: jam jars, muffins, pies, compote, bars, sorbet and dust pouches, each carrying a small picture of its own fruit
(the fruit's item texture shrunk to about 6x6), so no two fruits give the same sprite. Shaded like the drinks: dark edge, four tones, glints."""
from pathlib import Path
from PIL import Image
from pixel_kit import Sp, ramp, mix

CORE = Path.home() / "alterra/src/main/resources/assets/alterra/textures/item"
WHITE = (255, 255, 255)

def emblem(path, col, size=6):
    """The fruit shrunk to size x size: a list of rows of (r, g, b) or None. Falls back to a drawn berry for fruits without a texture of their own."""
    f = CORE / f"{path}.png"
    if not f.exists():
        s = Sp(); s.ball(8, 8, 5, col); s.put(8, 3, (70, 130, 50)); s.put(9, 3, (90, 160, 60)); im = s.image()
    else: im = Image.open(f).convert("RGBA")
    bb = im.getbbox() or (0, 0, 16, 16); im = im.crop(bb)
    n = max(im.size); sq = Image.new("RGBA", (n, n), (0, 0, 0, 0)); sq.paste(im, ((n - im.size[0]) // 2, (n - im.size[1]) // 2))
    small = sq.resize((size, size), Image.BOX); px = small.load(); rows = []
    for y in range(size):
        row = []
        for x in range(size):
            r, g, b, a = px[x, y]
            if a < 110: row.append(None); continue
            lum = (r + g + b) / 3; k = 1.18   # a little more contrast so it reads at this size
            row.append(tuple(max(0, min(255, int(lum + (v - lum) * k))) for v in (r, g, b)))
        rows.append(row)
    return rows
def stamp(s, emb, x0, y0, dark=False):
    for y, row in enumerate(emb):
        for x, c in enumerate(row):
            if c: s.put(x0 + x, y0 + y, mix(c, (0, 0, 0), 0.25) if dark else c)

def _wafer(s, x0, x1, y0, y1):
    w = ramp((222, 176, 104))
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1): s.put(x, y, w[1] if (x + y) % 3 == 0 else (w[3] if (x + y) % 3 == 1 else w[2]))

def jam_sprite(col, emb):
    s = Sp(); t = ramp(col); glass = (204, 226, 236); gl_d = (136, 168, 188)
    for y in range(5, 15):   # the jar: glass edge, fruit jam inside with a glossy top
        for x in range(3, 13):
            edge = x in (3, 12) or y in (5, 14)
            s.put(x, y, (gl_d if edge else (t[3] if y == 7 else (t[2] if x < 8 else t[1]))))
    s.rect(4, 6, 11, 6, mix(col, WHITE, 0.55)); s.put(4, 7, mix(glass, WHITE, 0.4))   # head space, a glint on the glass
    for y in range(7, 13): s.put(4, y, mix(t[2], WHITE, 0.38))
    s.rect(3, 2, 12, 4, mix(col, (40, 30, 30), 0.30)); s.rect(3, 2, 12, 2, mix(col, (255, 255, 255), 0.10)); s.rect(4, 4, 11, 4, mix(col, (30, 20, 20), 0.55))   # a cloth lid with a band
    for x in range(3, 13, 2): s.put(x, 3, mix(col, WHITE, 0.30))
    s.rect(5, 8, 10, 13, (244, 232, 204)); s.rect(5, 8, 10, 8, (255, 250, 232)); s.rect(5, 13, 10, 13, (196, 178, 146))   # the label
    stamp(s, emb, 5, 8 if len(emb) <= 5 else 7); s.rim(); s.outline(); return s.image()

def muffin_sprite(col, emb):
    s = Sp(); cake = (214, 156, 84); ct = ramp(cake); cup = mix(col, WHITE, 0.45); cp = ramp(cup)
    s.ellipse(8, 6.2, 6.4, 4.4, cake); s.rect(3, 6, 12, 8, ct[2])
    for y in range(9, 15):   # the paper cup: pleats
        w = 5 - (14 - y) * 0.18
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, cp[3] if (x % 2 == 0) else cp[1])
    s.rect(3, 9, 12, 9, cp[2]); s.rect(4, 14, 11, 14, cp[1])
    for x, y in ((5, 4), (9, 3), (11, 6), (6, 7), (3, 7), (8, 6)): s.put(x, y, mix(col, WHITE, 0.15))   # fruit bits in the cake
    for x, y in ((4, 5), (10, 4), (7, 2)): s.put(x, y, ct[4])   # sugar glints
    stamp(s, emb, 6, 0 if len(emb) <= 5 else -1) if False else None
    for y, row in enumerate(emb[:4]):   # the fruit sits on top, half sunk into the dome
        for x, c in enumerate(row): 
            if c: s.put(5 + x, 1 + y, c)
    s.rim(); s.outline(); return s.image()

def pie_sprite(col, emb):
    if sum(col) / 3 > 165: col = mix(col, (150, 56, 24), 0.30)   # a pale fruit would vanish into the crust
    s = Sp(); crust = (216, 160, 84); cr = ramp(crust); fill = ramp(col)
    s.ellipse(8, 9.4, 7.2, 5.4, mix(crust, (60, 40, 20), 0.35))   # the tin
    s.ellipse(8, 8.6, 6.8, 5.0, crust)
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.8, (y + 0.5 - 8.6) / 5.0; d = dx * dx + dy * dy
            if d <= 1 and d > 0.70: s.put(x, y, cr[3] if (x + y) % 2 == 0 else cr[2])   # the crimped edge
    s.ellipse(8, 8.6, 4.9, 3.4, col)   # fruit filling
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 4.9, (y + 0.5 - 8.6) / 3.4
            if dx * dx + dy * dy <= 1: s.put(x, y, fill[3] if (x - y) % 4 == 0 else (fill[1] if (x + 2 * y) % 5 == 0 else fill[2]))   # chunks of fruit
    for off in (-2, 2):   # two strips each way over the filling
        s.line(8 + off - 4, 5, 8 + off + 4, 12, cr[3]); s.line(8 + off + 4, 5, 8 + off - 4, 12, cr[2])
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 4.9, (y + 0.5 - 8.6) / 3.4
            if dx * dx + dy * dy > 1.0 and (s.at(x, y) in (cr[3], cr[2])) and ((x - 8) ** 2 / 6.8 ** 2 + (y - 8.6) ** 2 / 5.0 ** 2) < 0.70: s.put(x, y, crust)   # strips stop at the rim
    for x, y in ((6, 7), (10, 9), (8, 10)): s.put(x, y, fill[4])
    s.put(4, 6, cr[4]); s.rim(); s.outline(); return s.image()

def compote_sprite(col, emb):
    s = Sp(); bowl = (232, 214, 184); bt = ramp(bowl); ft = ramp(col)
    for y in range(8, 15):
        w = 6.2 - (y - 8) * 0.42
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, bt[3] if x < 6 else (bt[2] if x < 10 else bt[1]))
    s.rect(2, 7, 13, 8, bt[3]); s.rect(4, 14, 11, 14, bt[1])
    s.rect(3, 9, 12, 9, mix(col, (255, 255, 255), 0.0)); s.rect(5, 12, 10, 12, mix(bowl, col, 0.6))   # a coloured band on the bowl
    s.ellipse(8, 7.4, 5.6, 2.2, col)   # the compote, glossy
    for x, y in ((5, 6), (9, 7), (11, 6), (7, 8)): s.put(x, y, ft[3])
    s.put(6, 6, ft[4]); s.put(10, 6, ft[4])
    for y, row in enumerate(emb[:5]):   # a piece of the fruit on top
        for x, c in enumerate(row):
            if c: s.put(6 + x, 1 + y, c)
    s.rim(); s.outline(); return s.image()

def bar_sprite(col, emb):
    s = Sp(); wrap = mix(col, (255, 255, 255), 0.05); wt = ramp(wrap)
    s.rect(2, 5, 13, 12, wt[2]); s.rect(2, 5, 13, 5, wt[3]); s.rect(2, 12, 13, 12, wt[1])
    s.rect(1, 7, 1, 10, wt[1]); s.rect(14, 7, 14, 10, wt[1])   # the crimped ends of the wrapper
    for y in (7, 8, 9, 10): s.put(1, y, wt[3] if y % 2 == 0 else wt[1]); s.put(14, y, wt[3] if y % 2 == 0 else wt[1])
    s.rect(3, 6, 12, 11, (252, 244, 226)); s.rect(3, 6, 12, 6, (255, 252, 240)); s.rect(3, 11, 12, 11, (206, 190, 160))   # a paper band across the middle
    stamp(s, emb, 5, 5 if len(emb) > 6 else 6)
    for x in range(3, 13, 3): s.put(x, 11, wt[2])
    s.rim(); s.outline(); return s.image()

def sorbet_sprite(col, emb):
    s = Sp(); t = ramp(col); glass = (200, 224, 238); gd = (122, 156, 180)
    for y in range(9, 12):   # the coupe glass, the sorbet showing through
        w = 6 - (y - 9) * 0.9
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, mix(glass, col, 0.35) if (x + y) % 4 else mix(glass, WHITE, 0.5))
    s.rect(2, 8, 13, 8, gd); s.rect(7, 12, 8, 13, gd); s.rect(4, 14, 11, 14, gd); s.rect(5, 13, 10, 13, mix(gd, WHITE, 0.2))
    s.ball(8, 5.8, 4.6, col)   # the scoop
    for x, y in ((5, 4), (4, 6), (11, 6), (9, 8)): s.put(x, y, t[3] if y < 7 else t[1])
    s.line(11, 2, 14, 1, (222, 176, 104)); s.line(12, 4, 14, 2, (222, 176, 104)); s.put(14, 1, (190, 140, 76))   # a wafer
    for y, row in enumerate(emb[:4]):   # the fruit on top
        for x, c in enumerate(row[:4]):
            if c: s.put(6 + x, y + 1, c)
    s.rim(); s.outline(); return s.image()

def dust_sprite(col, emb):
    s = Sp(); cloth = (232, 214, 176); ct = ramp(cloth); dt = ramp(col)
    for y in range(5, 15):   # a drawstring pouch
        w = 5.5 - abs(y - 10) * 0.14
        for x in range(int(8 - w), int(8 + w) + 1): s.put(x, y, ct[3] if x < 6 else (ct[2] if x < 10 else ct[1]))
    s.rect(5, 3, 10, 5, ct[2]); s.rect(5, 3, 6, 5, ct[3]); s.rect(5, 5, 10, 5, (190, 60, 52))   # the gathered neck and its string
    s.put(11, 5, (190, 60, 52)); s.put(12, 6, (150, 44, 40))
    s.ellipse(8, 3, 4, 1.6, col)   # a little of the dust spilling over the top
    for x, y in ((6, 2), (9, 2), (8, 3)): s.put(x, y, dt[4])
    stamp(s, emb, 5, 7)
    s.rim(); s.outline(); return s.image()

def dried_sprite(col, emb):
    """Two pieces of dried fruit: leathery, wrinkled, darker and duller than the fresh one, each with a little stem."""
    s = Sp(); dc = mix((col[0] * 0.62, col[1] * 0.55, col[2] * 0.5), (110, 84, 60), 0.35); dc = tuple(int(v) for v in dc); t = ramp(dc)
    for cx, cy, r in ((5.2, 9.4, 3.9), (10.6, 7.4, 3.7)):
        s.ball(cx, cy, r, dc, hi=False)
        for k in range(3): s.line(cx - r + 1 + k * 2, cy - 1 + (k % 2) * 2, cx - r + 2.5 + k * 2, cy + 2, t[1])   # wrinkles
        s.put(cx - 1, cy - r + 1, t[4]); s.put(cx, cy - r - 0.4, (96, 70, 44)); s.put(cx + 1, cy - r - 1, (120, 90, 56))
    for y, row in enumerate(emb[:3]):   # a hint of the fresh colour at the heart
        for x, c in enumerate(row[:3]):
            if c: s.put(4 + x, 9 + y, mix(c, dc, 0.55))
    s.rim(); s.outline(); return s.image()

def all_for(path, col):
    e5, e6 = emb = emblem(path, col, 5), emblem(path, col, 6)
    return {f"{path}_jam": jam_sprite(col, e5), f"{path}_muffin": muffin_sprite(col, e5), f"{path}_fruit_pie": pie_sprite(col, e5), f"{path}_compote": compote_sprite(col, e5),
            f"{path}_bar": bar_sprite(col, e6), f"{path}_sorbet": sorbet_sprite(col, e5), f"{path}_dried": dried_sprite(col, e5), f"{path}_dust": dust_sprite(col, e6)}
