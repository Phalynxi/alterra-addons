# Alterra addon template

A starting point for an addon for [Alterra](https://github.com/Phalynxi/alterra-addons). Read
[Making an addon](https://github.com/Phalynxi/alterra-addons/blob/main/docs/making-an-addon.md) and
[Publishing](https://github.com/Phalynxi/alterra-addons/blob/main/docs/publishing.md).

1. Copy this folder into a new repository.
2. Rename `alterra_example` everywhere (build.gradle, settings.gradle, the mod class and package, `neoforge.mods.toml`, `alterra-addon.json`, the assets folder).
3. Add the topic `alterra-addon` to the repository.
4. Push a tag (`git tag v1.0.0 && git push --tags`): the workflow builds the jar, makes the release and fills in `alterra-addon.json`.

`./gradlew build` (Java 21) builds the jar locally.
