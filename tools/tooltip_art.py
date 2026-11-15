"""Writes the tooltip frame definition (kitchen/src/main/resources/alterra_kitchen_tooltip.json): the pieces of the ornate frame as rows of characters and the colours of each taste tier,
and, with a path to Minecraft's client jar's ascii.png, a mock-up image of how the tooltips look. The game draws the frame from the json (KitchenTooltips).
Run: python3 -I tools/tooltip_art.py [ascii.png] [out.png]"""
import json, sys
from pathlib import Path
OUT = Path(__file__).resolve().parent.parent / "kitchen/src/main/resources/alterra_kitchen_tooltip.json"
def mirror(half): return [r + r[-2::-1] for r in half]
TOP = mirror(["........G", "...M..MGG", ".MMM.MMLL", "MMMMMMMMM"])
TOP_HIGH = mirror([".........MGG", "..M....MMLGG", ".MLM..MMMMLL", "MMMMMMMMMMMM"])
BOTTOM = mirror(["MMMMMM", ".MMMDG", "..MMDG", ".....G"])

# ---- stat icons, 9x9: hunger, satiety, thirst, warm, cool and the five flavours
ICON_DIR = OUT.parent / "assets/alterra_kitchen/textures/gui/sprites/stat"
def _icon(rows, pal):
    from PIL import Image
    assert len(rows) == 9 and all(len(r) == 9 for r in rows), rows
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0)); px = im.load()
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".": px[x, y] = pal[ch] + (255,)
    return im
def _drumstick(meat, light, dark):
    return _icon(["....ooo..", "...olmmo.", "..olmmmdo", "..ommmmdo", "..odmmdo.", ".obodoo..", "obbo.....", ".obo.....", "..o......"],
                 {"o": (58, 28, 16), "m": meat, "l": light, "d": dark, "b": (240, 230, 210)})
ICONS = {
    "hunger": _drumstick((196, 108, 56), (232, 150, 92), (146, 76, 38)),
    "satiety": _drumstick((240, 190, 60), (255, 232, 130), (190, 130, 30)),
    "thirst": _icon(["....o....", "....o....", "...obo...", "..obbbo..", ".obwbbbo.", ".obbbbbo.", ".obbbbbo.", "..obbbo..", "...ooo..."], {"o": (20, 50, 110), "b": (70, 150, 230), "w": (205, 232, 255)}),
    "warm": _icon(["....o....", "...oro...", "..orro...", "..orYro..", ".orYYYro.", ".orYYYro.", ".orYYYro.", "..orrro..", "...ooo..."], {"o": (110, 30, 10), "r": (230, 90, 30), "Y": (255, 210, 70)}),
    "cool": _icon(["....c....", ".c..c..c.", "..c.c.c..", "...ccc...", "ccccWcccc", "...ccc...", "..c.c.c..", ".c..c..c.", "....c...."], {"c": (130, 210, 255), "W": (255, 255, 255)}),
    "sweet": _icon([".........", ".oo...oo.", "oPPo.oPPo", "oPWPoPPPo", "oPPPPPPPo", ".oPPPPPo.", "..oPPPo..", "...oPo...", "....o...."], {"o": (110, 30, 70), "P": (240, 110, 190), "W": (255, 200, 235)}),
    "savory": _icon([".........", "..ooooo..", ".oMMMMMo.", "oMLLMMMMo", "oMLMMMDMo", "oMMMMDDMo", ".oMDDDMo.", "..ooooo..", "........."], {"o": (70, 34, 18), "M": (190, 96, 52), "L": (234, 150, 98), "D": (140, 66, 34)}),
    "spicy": _icon([".....gg..", "....gg...", "...ooo...", "..orrro..", "..orrRo..", ".orrRro..", ".orrro...", "..oro....", "...o....."], {"o": (100, 20, 20), "r": (220, 40, 30), "R": (255, 120, 90), "g": (70, 150, 60)}),
    "fresh": _icon(["..ooooo..", ".oLLLLLo.", "oLLLvLLlo", "oLLvLvLlo", "oLvLLLvlo", "oLLLvLLlo", ".oLLvLlo.", "..ooooo..", "....o...."], {"o": (30, 90, 100), "L": (150, 240, 232), "l": (84, 200, 196), "v": (50, 140, 146)}),
    "toxic": _icon(["..ooooo..", ".oGGGGGo.", "oGGGGGGGo", "oGKGGGKGo", "oGGGGGGGo", ".oGGKGGo.", ".oGWGWGo.", "..oWWWo..", "...ooo..."], {"o": (24, 44, 12), "G": (150, 210, 80), "K": (24, 36, 10), "W": (110, 160, 50)}),
    "rich": _icon([".........", "..ooooo..", ".oYYYYYo.", "oYWWYYYYo", "oYYYYYYDo", "oYYYYYDDo", ".oDDDDDo.", "..ooooo..", "........."], {"o": (110, 80, 10), "Y": (250, 214, 90), "W": (255, 244, 190), "D": (200, 160, 40)}),
}
def write_icons():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    for name, im in ICONS.items(): im.save(ICON_DIR / f"{name}.png")
write_icons()

TIERS = [  # min taste, frame, shade, light, gem, background top, background bottom
    (0, "A02828", "4A1010", "E06060", "FF6A6A", "1C0C0C", "2C1313"),
    (20, "9A9A9A", "454545", "D8D8D8", "E4E4E4", "17171A", "232328"),
    (40, "E8E0D0", "6B6350", "FFFFFF", "FFF6DC", "1A1814", "2A261E"),
    (60, "F2C84B", "6B5418", "FFE9A0", "FFF08A", "1D1708", "2D240D"),
    (80, "F29A3A", "6B3A10", "FFD090", "FFC070", "1F1208", "30190B"),
    (95, "D878F0", "5A2A6B", "F5C0FF", "F0B0FF", "190D20", "281238"),
    (120, "FFE9A8", "5A4A20", "FFFFFF", "FFFFFF", "0B0A1E", "1A1238"),   # divine: the game cycles its colours through the rainbow
]
UNKNOWN = dict(zip(("main", "dark", "light", "gem", "bg", "bg2"), ("8A7FA8", "3C3550", "C8BEE0", "B0A4D0", "141118", "201B28")))
data = {"unknown": UNKNOWN, "top": TOP, "topHigh": TOP_HIGH, "bottom": BOTTOM, "tiers": [dict(zip(("min", "main", "dark", "light", "gem", "bg", "bg2"), t)) for t in TIERS]}
OUT.write_text(json.dumps(data, indent=1)); print("wrote", OUT)

if len(sys.argv) > 2:
    from PIL import Image, ImageDraw
    SHEET = Image.open(sys.argv[1]).convert("RGBA")
    def glyph(ch):
        c = ord(ch); g = SHEET.crop(((c % 16) * 8, (c // 16) * 8, (c % 16) * 8 + 8, (c // 16) * 8 + 8)); px = g.load(); w = 0
        for x in range(8):
            if any(px[x, y][3] > 0 for y in range(8)): w = x + 1
        return g, (w if ch != " " else 3)
    def tint(g, col):
        out = Image.new("RGBA", g.size); o = out.load(); p = g.load()
        for y in range(8):
            for x in range(8):
                if p[x, y][3] > 0: o[x, y] = col + (255,)
        return out
    STAR = ["...#...", "...#...", "..###..", "#######", ".#####.", "..###..", ".##.##."]; STAR_O = ["...#...", "...#...", "..#.#..", "###.###", ".#...#.", "..#.#..", ".##.##."]
    def tw(s): return sum((7 if c in "★☆" else glyph(c)[1]) + 1 for c in s) - 1
    def text(img, x, y, s, col):
        for sh in (True, False):
            cx = x
            for ch in s:
                c = tuple(v // 4 for v in col) if sh else col; o = 1 if sh else 0
                if ch in "★☆":
                    for yy, row in enumerate(STAR if ch == "★" else STAR_O):
                        for xx, v in enumerate(row):
                            if v == "#": img.putpixel((cx + xx + o, y + yy + o + 1), c + (255,))
                    cx += 8
                else:
                    g, w = glyph(ch)
                    if ch != " ": img.alpha_composite(tint(g, c), (cx + o, y + o))
                    cx += w + 1
    def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    def tier(t): return [x for x in data["tiers"] if x["min"] <= t][-1]
    NAMES = [(20, "Awful", (170, 0, 0)), (40, "Bland", (170, 170, 170)), (60, "Fine", (255, 255, 255)), (80, "Tasty", (255, 255, 85)), (95, "Delicious", (255, 170, 0)), (120, "Perfect", (255, 85, 255)), (999, "Divine", (255, 235, 150))]
    def taste_line(t):
        word, col = next((n, c) for m, n, c in NAMES if t < m)
        st = max(1, min(5, round(min(t, 100) / 20)))
        return "Taste: " + "★" * (5 if t >= 120 else st) + "☆" * (0 if t >= 120 else 5 - st) + " " + word, col
    BAR_W, BAR_H = 64, 7
    WORDCOL = {"Awful": (160, 40, 40), "Bland": (154, 154, 154), "Fine": (232, 224, 208), "Tasty": (242, 200, 75), "Delicious": (242, 154, 58), "Perfect": (216, 120, 240), "Divine": (255, 233, 168)}
    def word_of(sc): return next(n for m, n, c in NAMES if sc < m)
    def meter_width(sc): return tw("Taste") + 4 + BAR_W + 2 + 4 + tw(word_of(sc))
    def lw(s): return sum(tw(t) for t, _ in s) if isinstance(s, list) else tw(s)
    def mixc(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))
    def draw_meter(img, x, y, sc, ms):
        divine = sc >= 120; main = WORDCOL[word_of(sc)]; d = ImageDraw.Draw(img)
        text(img, x, y + 1, "Taste", (170, 170, 170)); bx = x + tw("Taste") + 4; by = y + 1
        d.rectangle([bx - 1, by - 1, bx + BAR_W, by + BAR_H], fill=(0, 0, 0, 255)); d.rectangle([bx, by, bx + BAR_W - 1, by + BAR_H - 1], fill=(42, 36, 32, 255))
        fill = round(BAR_W * min(sc, 100) / 100); dark = mixc(main, (0, 0, 0), .45)
        for i in range(fill):
            col = hue(((ms // 10 + i * 4) % 360) / 360.0, .55) if divine else mixc(dark, main, min(1, i / BAR_W + .25))
            d.line([(bx + i, by), (bx + i, by + BAR_H - 1)], fill=col + (255,)); img.putpixel((bx + i, by), mixc(col, (255, 255, 255), .35) + (255,)); img.putpixel((bx + i, by + BAR_H - 1), mixc(col, (0, 0, 0), .35) + (255,))
        sweep = (ms // 18) % (BAR_W + 24) - 12
        for i in range(max(0, sweep - 3), min(fill - 1, sweep + 3) + 1):
            a = 150 - abs(i - sweep) * 40
            if a > 0:
                for yy in range(by + 1, by + BAR_H - 1): c0 = img.getpixel((bx + i, yy)); img.putpixel((bx + i, yy), tuple(int(c0[k] + (255 - c0[k]) * a / 255) for k in range(3)) + (255,))
        for k in range(1, 5): xx = bx + BAR_W * k // 5; [img.putpixel((xx, yy), tuple(int(v * .6) for v in img.getpixel((xx, yy))[:3]) + (255,)) for yy in range(by, by + BAR_H)]
        wx = bx + BAR_W + 6; word = word_of(sc)
        for j, ch in enumerate(word):
            text(img, wx, y + 1, ch, hue(((ms // 12 + j * 20) % 360) / 360.0, .55) if divine else main); wx += glyph(ch)[1] + 1
    def piece(img, rows, ox, oy, pal):
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch != ".": img.putpixel((ox + c, oy + r), pal[ch] + (255,))
    import math, colorsys
    def hue(h, sat): r, g, b = colorsys.hsv_to_rgb(h % 1.0, sat, 1.0); return (int(r * 255), int(g * 255), int(b * 255))
    def along(p, left, top, right, bottom):
        w, h = right - left, bottom - top
        if p < w: return (left + p, top)
        p -= w
        if p < h: return (right, top + p)
        p -= h
        if p < w: return (right - p, bottom)
        p -= w; return (left, bottom - p)
    def text_width_one(ch): return (7 if ch in '★☆' else glyph(ch)[1]) + 1
    def tooltip(lines, taste, ms=0):
        cardline = next((c for t, c in lines if t == '@card'), None)
        w = max([lw(t) for t, _ in lines if t != '@card'] + ([card_w(cardline, taste)] if cardline else [])); h = 10 + (2 if len(lines) > 1 else 0) + ((11 * card_rows(cardline, taste) - 1) if cardline else 0)
        P = 14; img = Image.new("RGBA", (w + 2 * P + 8, h + 2 * P + 8), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
        x0, y0 = P + 4, P + 4; t = tier(taste); main, dark, light, gem = (hexc(t[k]) for k in ("main", "dark", "light", "gem")); bg, bg2 = hexc(t["bg"]), hexc(t["bg2"])
        divine = t["min"] >= 120; high = t["min"] >= 80
        if divine:
            hh = (ms % 6000) / 6000.0; main = hue(hh, .5); light = hue(hh + .03, .15); gem = hue(hh + .015, .32); dark = tuple(int(v * .35) for v in main)
        pulse = 0x40 + int(0x30 * math.sin(ms / (250.0 if divine else 600.0)))
        left, top, right, bottom = x0 - 4, y0 - 4, x0 + w + 3, y0 + h + 3
        for yy in range(top, bottom + 1):
            f = (yy - top) / max(1, bottom - top); d.line([(left, yy), (right, yy)], fill=tuple(int(bg[i] + (bg2[i] - bg[i]) * f) for i in range(3)) + (240,))
        shaded = tuple(int(v * 0.72) for v in main)
        d.line([(left, top), (right, top)], fill=main + (255,)); d.line([(left, top), (left, bottom)], fill=main + (255,))
        d.line([(left, bottom), (right, bottom)], fill=shaded + (255,)); d.line([(right, top), (right, bottom)], fill=shaded + (255,))
        d.rectangle([left + 1, top + 1, right - 1, bottom - 1], outline=dark + (255,))
        pal = {"M": main, "L": light, "D": dark, "G": gem}
        for cx, cy, sx, sy in ((left, top, 1, 1), (right, top, -1, 1), (left, bottom, 1, -1), (right, bottom, -1, -1)):
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1): img.putpixel((cx + dx, cy + dy), gem + (255,))
            img.putpixel((cx - 1, cy - 1), light + (255,)); img.putpixel((cx + 1, cy + 1), tuple(int(v * 0.7) for v in gem) + (255,))
            for k in range(2, 6): img.putpixel((cx + sx * k, cy), light + (255,)); img.putpixel((cx, cy + sy * k), light + (255,))
        per = 2 * (right - left) + 2 * (bottom - top); tail = 14 if high else 8; head = int(ms / (14 if divine else 22 if high else 34)) % per
        for i in range(tail):
            pt = along((head - i) % per, left, top, right, bottom); a = int(255 * (1 - i / tail)); base = img.getpixel(pt)
            img.putpixel(pt, tuple(int(base[k] + (light[k] - base[k]) * a / 255) for k in range(3)) + (255,))
        for k, (cx, cy) in enumerate(((left, top), (right, top), (left, bottom), (right, bottom))):
            if ((ms / (500.0 if divine else 1100.0)) + k * 0.27) % 1.0 < 0.14:
                for dd in range(-2, 3): img.putpixel((cx + dd, cy), (255, 255, 255, 255)); img.putpixel((cx, cy + dd), (255, 255, 255, 255))
        tp = data["topHigh"] if taste >= 80 else data["top"]
        piece(img, tp, (left + right) // 2 - len(tp[0]) // 2, top - (len(tp) - 1), pal)
        piece(img, data["bottom"], (left + right) // 2 - len(data["bottom"][0]) // 2, bottom, pal)
        if high and (ms // 700) % 3 == 0:
            cx, cy = (left + right) // 2, top - (len(tp) - 1)
            for dd in (-1, 0, 1): img.putpixel((cx + dd, cy), (255, 255, 255, 255)); img.putpixel((cx, cy + dd), (255, 255, 255, 255))
        pal = {"M": main, "L": light, "D": dark, "G": gem}
        y = y0
        for i, (s_, col) in enumerate(lines):
            if s_ == "@card": draw_card(img, x0, y, cardline, taste, ms); y += 11 * card_rows(cardline, taste)
            else:
                text(img, x0, y, s_, col); y += 12
                if i == 0 and len(lines) > 1: d.line([(x0, y - 2), (x0 + w, y - 2)], fill=main + (110,))
        return img
    G = (170, 170, 170)
    FLAVS = ["sweet", "savory", "spicy", "fresh", "rich"]
    def stats_items(c):
        it = [("hunger", str(c["hunger"])), ("satiety", "%.1f" % c["sat"])]
        if c.get("thirst"): it.append(("thirst", str(c["thirst"])))
        if c.get("temp"): it.append(("warm" if c["temp"] > 0 else "cool", ""))
        return it
    def stats_w(c): return sum(9 + (1 + tw(t) if t else 0) for _, t in stats_items(c)) + 5 * (len(stats_items(c)) - 1)
    def flav_items(c):
        if not c.get("flavors"): return []
        f = c["flavors"]; order = sorted(range(5), key=lambda i: -f[i])
        return [(FLAVS[i], str(f[i])) for i in order if f[i] > 0][:2]
    def flav_w(items): return sum(10 + tw(t) + 5 for _, t in items) - 5
    def inline_count(c, taste):
        fl = flav_items(c)
        for k in range(len(fl), 0, -1):
            if stats_w(c) + 8 + flav_w(fl[:k]) <= tw(taste_line(taste)[0]): return k
        return 0
    def card_rows(c, taste): return 2 + (1 if flav_items(c) and inline_count(c, taste) == 0 else 0)
    def card_w(c, taste):
        w = max(tw(taste_line(taste)[0]), stats_w(c)); fl = flav_items(c); k = inline_count(c, taste)
        if k: w = max(w, stats_w(c) + 8 + flav_w(fl[:k]))
        elif fl: w = max(w, flav_w(fl))
        return w
    def draw_card(img, x, y, c, taste, ms):
        divine = taste >= 120; line, col = taste_line(taste)
        if divine:
            cx = x
            for j, ch in enumerate(line): text(img, cx, y, ch, hue(((ms // 12 + j * 14) % 360) / 360.0, .55)); cx += text_width_one(ch)
        else: text(img, x, y, line, col)
        y += 11; cx = x
        for name, t in stats_items(c):
            img.alpha_composite(ICONS[name], (cx, y)); cx += 9
            if t: text(img, cx + 1, y + 1, t, G); cx += 1 + tw(t)
            cx += 5
        fl = flav_items(c); k = inline_count(c, taste)
        if k: cx += 3
        elif fl: y += 11; cx = x; k = len(fl)
        for name, t in fl[:k]:
            img.alpha_composite(ICONS[name], (cx, y)); text(img, cx + 10, y + 1, t, FCOL[name]); cx += 10 + tw(t) + 5
    FCOL = {"sweet": (255, 85, 255), "savory": (255, 170, 0), "spicy": (255, 85, 85), "fresh": (85, 255, 255), "rich": (255, 255, 85)}
    items = [("Salted Beef Burger", 91, dict(hunger=12, sat=9.4, flavors=[2, 4, 0, 1, 1])), ("Plum Pie", 88, dict(hunger=8, sat=5.1)), ("Plum Jam", 62, dict(hunger=4, sat=1.6)),
             ("Sunrise Mixer", 96, dict(hunger=5, sat=2.4, thirst=8, temp=-1)), ("Beef Chowder", 34, dict(hunger=9, sat=4.0, flavors=[0, 3, 0, 0, 2])), ("Mulled Cider", 70, dict(hunger=2, sat=1.0, thirst=6, temp=1)),
             ("Dry Bread", 45, dict(hunger=4, sat=1.8)), ("Ambrosia Feast", 125, dict(hunger=20, sat=19.0, flavors=[4, 5, 0, 1, 1]))]
    tips = [tooltip([(n, (255, 255, 255)), ("@card", c)], t) for n, t, c in items]
    S = 4; rows = [tips[0:2], tips[2:4], tips[4:7], tips[7:8]]; pad = 10
    W = max(sum(t.size[0] for t in r) + pad * (len(r) + 1) for r in rows) * S; H = (sum(max(t.size[1] for t in r) for r in rows) + pad * (len(rows) + 1)) * S
    sheet = Image.new("RGBA", (W, H), (60, 60, 70, 255)); y = pad * S
    for r in rows:
        x = pad * S
        for t in r: sheet.alpha_composite(t.resize((t.size[0] * S, t.size[1] * S), Image.NEAREST), (x, y)); x += (t.size[0] + pad) * S
        y += (max(t.size[1] for t in r) + pad) * S
    sheet.save(sys.argv[2]); print("wrote", sys.argv[2], sheet.size)
    if len(sys.argv) > 3:
        sel = [items[0], items[1], items[2], items[3], items[7], items[5]]
        frames = []; S2 = 3
        for f in range(120):
            ms = f * 50; tips2 = [tooltip([(n, (255, 255, 255)), ("@card", c)], t, ms) for n, t, c in sel]
            rows2 = [tips2[0:2], tips2[2:4], tips2[4:6]]; pad2 = 10
            W2 = max(sum(t.size[0] for t in r) + pad2 * (len(r) + 1) for r in rows2) * S2; H2 = (sum(max(t.size[1] for t in r) for r in rows2) + pad2 * (len(rows2) + 1)) * S2
            sh = Image.new("RGBA", (W2, H2), (60, 60, 70, 255)); y = pad2 * S2
            for r in rows2:
                x = pad2 * S2
                for t in r: sh.alpha_composite(t.resize((t.size[0] * S2, t.size[1] * S2), Image.NEAREST), (x, y)); x += (t.size[0] + pad2) * S2
                y += (max(t.size[1] for t in r) + pad2) * S2
            frames.append(sh.convert("P", palette=Image.ADAPTIVE))
        frames[0].save(sys.argv[3], save_all=True, append_images=frames[1:], duration=50, loop=0, disposal=1); print("wrote", sys.argv[3], len(frames), "frames")
    if len(sys.argv) > 4:   # a showcase of the public tiers (no Divine, that one is a secret): a still image and an animated gif next to it
        show = [("Fish and Apple Wrap", 14, dict(hunger=6, sat=2.1, flavors=[2, 2, 0, 1, 0])), ("Plain Noodles", 30, dict(hunger=4, sat=1.8, flavors=[0, 1, 0, 0, 0])),
                ("Apple Muffin", 50, dict(hunger=5, sat=2.6, flavors=[2, 0, 0, 1, 1])), ("Mulled Cider", 70, dict(hunger=2, sat=1.0, thirst=6, temp=1)),
                ("Plum Pie", 88, dict(hunger=8, sat=5.1, flavors=[4, 0, 0, 1, 2])), ("Salted Beef Burger", 97, dict(hunger=12, sat=9.4, flavors=[2, 4, 0, 1, 1]))]
        base = Path(sys.argv[4]); base.parent.mkdir(parents=True, exist_ok=True)
        def sheet_at(ms, scale):
            tips3 = [tooltip([(n, (255, 255, 255)), ("@card", c)], t, ms) for n, t, c in show]
            rows3 = [tips3[0:3], tips3[3:6]]; pad3 = 12
            W3 = max(sum(t.size[0] for t in r) + pad3 * (len(r) + 1) for r in rows3) * scale; H3 = (sum(max(t.size[1] for t in r) for r in rows3) + pad3 * (len(rows3) + 1)) * scale
            sh = Image.new("RGBA", (W3, H3), (46, 44, 54, 255)); y = pad3 * scale
            for r in rows3:
                x = pad3 * scale
                for t in r: sh.alpha_composite(t.resize((t.size[0] * scale, t.size[1] * scale), Image.NEAREST), (x, y)); x += (t.size[0] + pad3) * scale
                y += (max(t.size[1] for t in r) + pad3) * scale
            return sh
        sheet_at(300, 4).convert("RGB").save(base.with_suffix(".png"))
        fr = [sheet_at(f * 50, 3).convert("P", palette=Image.ADAPTIVE) for f in range(80)]
        fr[0].save(base.with_suffix(".gif"), save_all=True, append_images=fr[1:], duration=50, loop=0, disposal=1)
        print("showcase:", base.with_suffix(".png"), base.with_suffix(".gif"))
