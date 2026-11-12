# Alterra addon template

Starting point for an [Alterra](https://github.com/Phalynxi/alterra-addons) addon. See
[making-an-addon](https://github.com/Phalynxi/alterra-addons/blob/main/docs/making-an-addon.md) and
[publishing](https://github.com/Phalynxi/alterra-addons/blob/main/docs/publishing.md).

1. Copy this folder into a new repo.
2. Rename `alterra_example` everywhere (build.gradle, settings.gradle, the mod class and package, neoforge.mods.toml, alterra-addon.json, the assets folder).
3. Add the topic `alterra-addon` to the repo.
4. `git tag v1.0.0 && git push --tags`. The workflow builds the jar, makes the release and fills in alterra-addon.json.

`./gradlew build` builds the jar locally (Java 21).
