"""Tough As Nails values for Alterra Kitchen's drinks and cold foods, made from the Legendary Survival Overhaul values we already ship (so there is one source of truth).
TAN reads plain item tags: toughasnails:hydration/<percent>_hydration_drinks, and toughasnails:cooling_consumed_items / heating_consumed_items.
Our juices have 10 LSO hydration, TAN's own juices are in the 20 tag, so the percent is twice the LSO hydration, rounded to the nearest tag TAN has."""
import json
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "kitchen/src/main/resources/resourcepacks"
PACKS = {"tan_drinks": "Tough As Nails: Kitchen drink thirst", "tan_temp": "Tough As Nails: Kitchen drink temperature"}
MOD = "alterra_kitchen"
BUCKETS = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100]

def save(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(obj, indent=2) + "\n")

def main():
    import shutil
    for name in PACKS:
        if (RES / name).exists(): shutil.rmtree(RES / name)
    hydration, cool, heat = {}, [], []
    for f in sorted((RES / f"lso_drinks/data/{MOD}/legendarysurvivaloverhaul/thirst/consumables").glob("*.json")):
        h = max(e.get("hydration", 0) for e in json.loads(f.read_text()))
        if h > 0: hydration.setdefault(min(BUCKETS, key=lambda b: abs(b - 2 * h)), []).append(f"{MOD}:{f.stem}")
    for f in sorted((RES / f"lso_temp/data/{MOD}/legendarysurvivaloverhaul/temperature/consumables").glob("*.json")):
        t = sum(e.get("temperature_level", 0) for e in json.loads(f.read_text()))
        if t < 0: cool.append(f"{MOD}:{f.stem}")
        elif t > 0: heat.append(f"{MOD}:{f.stem}")
    for name, text in PACKS.items(): save(RES / name / "pack.mcmeta", {"pack": {"pack_format": 48, "description": text}})
    drinks, temp = RES / "tan_drinks/data/toughasnails/tags/item", RES / "tan_temp/data/toughasnails/tags/item"
    for pct, ids in hydration.items(): save(drinks / f"hydration/{pct}_hydration_drinks.json", {"replace": False, "values": ids})
    save(temp / "cooling_consumed_items.json", {"replace": False, "values": cool})
    save(temp / "heating_consumed_items.json", {"replace": False, "values": heat})
    print("tan:", {k: len(v) for k, v in sorted(hydration.items())}, "cooling", len(cool), "heating", len(heat))

main()
