# Publishing

Two ways to show up in the in-game list. Either way the jar has to be a GitHub release asset, and you need a small manifest with its SHA-256.

## Manifest

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
  "sha256": "64 hex digits, from sha256sum on that jar",
  "homepage": "https://github.com/you/alterra-example"
}
```

- `id` is the mod id from your neoforge.mods.toml (lowercase letters, digits, `_`, not `alterra`).
- `version` can use letters, digits and `. + _ -`. It's compared as dotted numbers (1.10 is newer than 1.9). The game offers Update when it's higher than what's installed.
- `alterra` is the oldest Alterra version it works with.
- `requires` is mod ids it needs (create, farmersdelight, other addons). The game won't install it without them.
- `download` has to be a github.com link ending in .jar.

## By topic

Add the topic `alterra-addon` to your repo and put the manifest in the root of the default branch as `alterra-addon.json`. The jar has to be
a release asset of that same repo. It then shows up in the Community tab, marked as not reviewed. The release workflow in the template fills
in version, link and checksum for you.

## In the official list

Open a pull request that adds your manifest to [addons.json](../addons.json). A check downloads your jar and verifies the checksum, that
the mod id matches, and the rules above. Then I look at what the addon does and at the source. For an update, send another pull request
with the new version, link and checksum.

Listed addons should be open source and do what the description says, nothing else at startup. See [security.md](security.md).

## Releasing

Push a tag like `v1.0.0` and the template workflow builds the jar, makes the release and updates alterra-addon.json. By hand:

```
./gradlew build
sha256sum build/libs/alterra_example.jar
gh release create v1.0.0 build/libs/alterra_example.jar
```

then put the link and checksum in the manifest.
