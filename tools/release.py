#!/usr/bin/env python3
"""Builds the addons, publishes them as a GitHub release of this repository and updates addons.json (version, download link, SHA-256).

    python3 tools/release.py <tag> [addon ...] [--publish]

Without --publish it only builds and rewrites addons.json (look at the diff first). With --publish it also creates the release with `gh`
and uploads the jars. The tag names the release (v1.0.0); each addon keeps the version from its own build.gradle."""
import hashlib, json, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO = "Phalynxi/alterra-addons"
META = {   # id -> folder, name, description, requires, oldest Alterra
    "alterra_kitchen": ("kitchen", "Alterra Kitchen", "Juices (each with its own glass), a fruit press, fruit crates and a grape trellis.", [], "1.0"),
    "alterra_weather": ("weather", "Alterra Weather", "Sandstorms, blizzards, fog, petals, pollen, ash and an aurora in Alterra's biomes. They run on their own timer, not on vanilla rain.", [], "1.0"),
    "alterra_music": ("music", "Alterra Music", "Background music for Alterra's biomes: two tracks each for cold, temperate, warm and dry land, the sea and caves, plus one for the sky.", [], "1.0"),
    "alterra_farmersdelight": ("farmersdelight", "Alterra for Farmer's Delight", "Crystal, Ember and Frost knives, and some cooking pot and cutting board recipes.", ["farmersdelight"], "1.0"),
    "alterra_create": ("create", "Alterra for Create", "Create recipes for Alterra: millstone, crushing, saw, Astral Water, and juice pressing, mixing and bottling.", ["create"], "1.0"),
    "alterra_aeronautics": ("aeronautics", "Alterra for Create Aeronautics", "Alterra's clouds lift airships like levitite.", ["aeronautics"], "1.0"),
}

def version_of(folder):
    return re.search(r"version\s*=\s*'([^']+)'", (ROOT / folder / "build.gradle").read_text()).group(1)

def main(argv):
    publish = "--publish" in argv; args = [a for a in argv if not a.startswith("--")]
    tag = args[0]; only = set(args[1:])
    subprocess.run(["./gradlew", "build", "-q"], cwd=ROOT, check=True)
    index_path = ROOT / "addons.json"
    index = json.loads(index_path.read_text()) if index_path.exists() else {"addons": []}
    by_id = {a["id"]: a for a in index["addons"]}
    jars = []
    for aid, (folder, name, desc, requires, alterra) in META.items():
        if only and aid not in only: continue
        jar = ROOT / folder / "build/libs" / f"{aid}.jar"
        ver = version_of(folder)
        named = ROOT / "dist" / f"{aid}-{ver}.jar"; named.parent.mkdir(exist_ok=True); named.write_bytes(jar.read_bytes())
        sha = hashlib.sha256(named.read_bytes()).hexdigest()
        by_id[aid] = {"id": aid, "name": name, "description": desc, "author": "Phalynxi", "version": ver, "alterra": alterra, "requires": requires,
                      "download": f"https://github.com/{REPO}/releases/download/{tag}/{named.name}", "sha256": sha,
                      "homepage": f"https://github.com/{REPO}/tree/main/{folder}"}
        jars.append(named); print(f"{aid} {ver} {sha[:12]}...")
    index["addons"] = [by_id[a] for a in META if a in by_id] + [a for k, a in by_id.items() if k not in META]
    index_path.write_text(json.dumps(index, indent=2) + "\n")
    if publish:
        subprocess.run(["gh", "release", "create", tag, "--repo", REPO, "--title", f"Alterra addons {tag}", "--notes",
                        "Addon jars. Install them from Alterra's settings (Addons page), or put the jars in your mods folder.", *map(str, jars)], check=True)
        print("published", tag)

if __name__ == "__main__": main(sys.argv[1:])
