# Publishing an addon

Two ways to appear in the in-game addon page. Both need your jar as a **GitHub release asset**, and a small manifest with its SHA-256.

## The manifest

```json
{
  "id": "alterra_example",
  "name": "Alterra Example",
  "description": "One sentence, 240 characters at most.",
  "author": "you",
  "version": "1.0.0",
  "alterra": "1.0",
  "requires": ["create"],
  "download": "https://github.com/you/alterra-example/releases/download/v1.0.0/alterra_example-1.0.0.jar",
  "sha256": "<64 hex digits: sha256sum of that jar>",
  "homepage": "https://github.com/you/alterra-example"
}
```

| Field | Meaning |
|---|---|
| `id` | The addon's **mod id** (the one in `neoforge.mods.toml`). Lowercase letters, digits, `_`; 2-64 characters. Not `alterra`. |
| `version` | Letters, digits and `. + _ -`; compared as dotted numbers (`1.10` is newer than `1.9`). The game offers **Update** when this is higher than the installed version. |
| `alterra` | The oldest Alterra version it works with. The game refuses to install it on an older Alterra. |
| `requires` | Mod ids it needs (`create`, `farmersdelight`, or another addon). The game will not install it until they are there. |
| `download` | `https://github.com/...` and ending in `.jar`. |
| `sha256` | Checked after the download; a jar that does not match is thrown away. |

## 1. By topic (community)

1. Give your repository the topic **`alterra-addon`** (Settings → About → Topics).
2. Put the manifest in the **root of the default branch** as `alterra-addon.json`.
3. Its `download` must be a release asset **of the same repository** (`https://github.com/<you>/<repo>/releases/download/...`).

That is all. It shows up in the **Community** tab (the game searches GitHub for the topic). It is marked *not reviewed*, and the game asks
before installing it. The template's release workflow fills `version`, `download` and `sha256` in for you on every release.

## 2. Into the official list (reviewed)

Open a pull request to this repository that adds your manifest to [`addons.json`](../addons.json). The check on the pull request
(`tools/validate_index.py --download`) downloads your jar and verifies the checksum, that the jar's mod id is the `id`, and the rules above.
A maintainer then looks at what the addon does and its source. Listed addons appear in the **Listed** tab without the warning. To ship
an update, open another pull request with the new version, link and checksum.

Your addon should be open source (so it can be looked at), say what it does honestly, and not do anything at startup beyond what its
description says. See [security](security.md).

## Releasing

Tag a release (`v1.0.0`); the template's workflow builds the jar, attaches it, and commits the new `alterra-addon.json`. To do it by hand:

```
./gradlew build
sha256sum build/libs/alterra_example.jar
gh release create v1.0.0 build/libs/alterra_example.jar
```

and put the link and checksum in the manifest.
