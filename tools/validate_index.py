#!/usr/bin/env python3
"""Checks addons.json (the official addon list) or a community addon's alterra-addon.json, with the same rules the in-game manager applies.

    python3 tools/validate_index.py [addons.json | alterra-addon.json ...] [--download]

--download also fetches every jar, checks its SHA-256, and checks that the jar is a mod whose id is the listed id.
Exit code 1 if anything is wrong (used by the pull request check)."""
import hashlib, io, json, re, sys, urllib.request, zipfile
from pathlib import Path

ID = re.compile(r"[a-z][a-z0-9_]{1,63}$"); VERSION = re.compile(r"[0-9A-Za-z.+_-]{1,32}$"); SHA = re.compile(r"[0-9a-f]{64}$")
LIMIT = 64 << 20

def entries(path):
    d = json.loads(Path(path).read_text())
    return d["addons"] if isinstance(d, dict) and "addons" in d else [d]

def check(e, official, repo=None):
    errs = []
    for k in ("id", "name", "description", "version", "download", "sha256"):
        if not isinstance(e.get(k), str) or not e[k].strip(): errs.append(f"missing or empty '{k}'")
    if errs: return errs
    if not ID.match(e["id"]) or e["id"] == "alterra": errs.append("id must be lowercase letters, digits and _ (2-64 characters, starting with a letter) and cannot be 'alterra'")
    if not VERSION.match(e["version"]): errs.append("version may only use letters, digits and . + _ - (max 32)")
    if not SHA.match(e["sha256"].lower()): errs.append("sha256 must be 64 hex digits")
    d = e["download"]
    if not d.startswith("https://github.com/") or not d.endswith(".jar") or ".." in d: errs.append("download must be an https://github.com/... link to a .jar")
    if repo and not d.lower().startswith(f"https://github.com/{repo}/releases/download/".lower()): errs.append(f"a community addon's jar must be a release asset of {repo}")
    if len(e["description"]) > 240: errs.append("description is longer than 240 characters (it is cut off in the game)")
    if len(e["name"]) > 40: errs.append("name is longer than 40 characters")
    for r in e.get("requires", []):
        if not ID.match(r): errs.append(f"requires: '{r}' is not a mod id")
    if "alterra" in e and not re.match(r"[0-9][0-9.]*$", str(e["alterra"])): errs.append("'alterra' (the oldest Alterra version it works with) must be a version like 1.0")
    return errs

def download(e):
    req = urllib.request.Request(e["download"], headers={"User-Agent": "alterra-addons-validate"})
    data = urllib.request.urlopen(req, timeout=120).read(LIMIT + 1)
    if len(data) > LIMIT: return ["jar is larger than 64 MB"]
    errs = []
    if hashlib.sha256(data).hexdigest() != e["sha256"].lower(): errs.append("the jar does not match the sha256")
    try:
        z = zipfile.ZipFile(io.BytesIO(data)); toml = z.read("META-INF/neoforge.mods.toml").decode()
        ids = re.findall(r'(?m)^\s*modId\s*=\s*"([^"]+)"', toml.split("[[dependencies", 1)[0])
        if e["id"] not in ids: errs.append(f"the jar's mod id is {ids}, not '{e['id']}'")
    except KeyError: errs.append("the jar has no META-INF/neoforge.mods.toml (is it a mod?)")
    except zipfile.BadZipFile: errs.append("the download is not a jar")
    return errs

def main(argv):
    files = [a for a in argv if not a.startswith("--")] or ["addons.json"]
    bad = 0
    for f in files:
        official = Path(f).name == "addons.json"
        seen = set()
        for e in entries(f):
            errs = check(e, official)
            if e.get("id") in seen: errs.append("listed twice")
            seen.add(e.get("id"))
            if "--download" in argv and not errs:
                try: errs += download(e)
                except Exception as ex: errs.append(f"could not download: {ex}")
            print(("OK   " if not errs else "FAIL ") + f"{f}: {e.get('id')} {e.get('version', '')}")
            for x in errs: print("       - " + x)
            bad += bool(errs)
    sys.exit(1 if bad else 0)

if __name__ == "__main__": main(sys.argv[1:])
