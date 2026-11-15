"""Draws the creative tab section banners of Alterra Kitchen (foods, ingredients, dishes): 16 frames of 162x18, a title plate on the left and a shelf of the mod's own item sprites on the right.
The drinks and decor banners are older hand-made ones kept in the same folder."""
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "kitchen/src/main/resources/assets/alterra_kitchen"
OUT = ROOT / "textures/gui/fancy_tab_section"; ITEMS = ROOT / "textures/item"
W, H, F = 162, 18, 16
SECTIONS = {
    "preserves": ((246, 200, 188), (190, 110, 110), ["mango_jam", "plum_compote", "strawberry_dried", "cherry_jam", "orange_slices"]),
    "baking": ((246, 214, 164), (206, 150, 100), ["plum_fruit_pie", "strawberry_muffin", "mango_bar", "berry_cookies", "bun"]),
    "frozen": ((200, 228, 242), (110, 156, 196), ["mango_ice_cream", "plum_soft_serve", "strawberry_popsicle", "cherry_sorbet", "sundae"]),
    "meals": ((240, 208, 170), (190, 130, 86), ["citrus_lobster", "moose_stew", "catfish_with_plum_sauce", "lobster_orchard_salad", "fruit_porridge"]),
    "pantry": ((214, 226, 176), (150, 170, 112), ["noodles", "spaghetti", "dough", "wafer_cone", "pastry_crust"]),
    "seasonings": ((236, 228, 200), (176, 156, 112), ["salt", "herb_salt", "ember_flakes", "frost_crystals", "autumn_spice"]),
    "wild": ((206, 226, 184), (110, 150, 96), ["poison_fern_fronds", "nightshade_berries", "hemlock_leaves", "deathcap", "oleander_leaves"]),
    "dishes": ((244, 200, 192), (196, 126, 120), ["dish_burger", "dish_noodles", "dish_pie", "sundae", "mango_soft_serve"]),
    "stations": ((218, 220, 228), (120, 124, 140), ["block/prep_table_side", "block/counter_side", "block/stove_front", "block/oven_front", "block/ice_machine_front"]),
}
def banner(name, top, bottom, sprites):
    out = Image.new("RGBA", (W, H * F))
    imgs = [Image.open((ROOT / "textures" / f"{s}.png") if s.startswith("block/") else (ITEMS / f"{s}.png")).convert("RGBA") for s in sprites]
    for f in range(F):
        fr = Image.new("RGBA", (W, H)); px = fr.load()
        for y in range(H):
            t = y / (H - 1)
            for x in range(W):
                c = [top[k] + (bottom[k] - top[k]) * t for k in range(3)]
                a = max(0.0, 1 - x / 78) * 0.5   # the title plate: darker on the left, fading out
                px[x, y] = tuple(int(v * (1 - a) + 40 * a) for v in c) + (255,)
        for x in range(W): px[x, 0] = tuple(min(255, v + 30) for v in px[x, 0][:3]) + (255,); px[x, H - 1] = (30, 22, 18, 255)
        for x in range(76, W): px[x, H - 3] = tuple(int(v * 0.7) for v in px[x, H - 3][:3]) + (255,)   # the shelf they stand on
        for k, im in enumerate(imgs):
            bob = round(math.sin((f / F) * math.tau + k * 1.3) * 0.6)
            fr.alpha_composite(im, (80 + k * 17, 1 + bob), (0, 0, 16, 14))
        out.paste(fr, (0, H * f))
    OUT.mkdir(parents=True, exist_ok=True); out.save(OUT / f"{name}.png")
for n, (a, b, s) in SECTIONS.items(): banner(n, a, b, s)
print("banners:", ", ".join(SECTIONS))
