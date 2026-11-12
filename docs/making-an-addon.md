# Making an addon

An addon is an ordinary NeoForge 1.21.1 mod that depends on Alterra. Alterra does not load addons specially; it gives them a few
places to plug in ([the API](api.md)) and the in-game page to install them.

## Start from the template

Copy [`template/`](../template) into a new repository and rename: the folder, `mod_id` in `build.gradle`, the package, the id in
`neoforge.mods.toml` and in `alterra-addon.json`. The id must be lowercase letters, digits and `_`; start it with `alterra_` so people
can find it, and use it as the namespace of everything you add (`my_addon:thing`).

```
build.gradle                  NeoForge MDK-style build; compile against the API jar from this repository
src/main/java/...             your mod class
src/main/resources/META-INF/neoforge.mods.toml
src/main/resources/assets/<id>/...   models, textures, lang
src/main/resources/data/<id>/...     recipes, loot tables, tags
alterra-addon.json            what the in-game list reads (see publishing.md)
.github/workflows/release.yml builds the jar, makes a release, fills in the version, link and checksum
```

`neoforge.mods.toml` must list Alterra as a required dependency:

```toml
[[dependencies.my_addon]]
modId="alterra"
type="required"
versionRange="[1.0,)"
ordering="AFTER"
side="BOTH"
```

## Hooking in

Everything is in `dev.phi.alterra.api.AlterraApi`; call it from your mod constructor.

```java
@Mod("alterra_example")
public class Example {
    public Example(IEventBus bus, ModContainer container) {
        // a section in the Alterra creative tab: banner texture, title key and items
        AlterraApi.addTabSection(ResourceLocation.fromNamespaceAndPath("alterra_example", "things"), 0xFFF2E6C8,
                () -> List.of(new ItemStack(MyItems.THING.get())));
        // a page in Alterra's settings screen
        AlterraApi.addSettingsCategory("Example", "What my addon does.", () -> new ItemStack(Items.APPLE),
                List.of(AlterraApi.Option.bool("Fancy mode", "Makes things fancy.", MyConfig.FANCY)));
    }
}
```

Field guide pages need no code: put `alterra_example_guide.json` in the jar ([format](field-guide.md)).

## Data-only addons

If your addon is only recipes, tags, loot or biome modifiers, it needs no Java: a jar with `neoforge.mods.toml` and a `data/` folder is
a mod. [`create/`](../create) and [`aeronautics/`](../aeronautics) are examples (their build file is three lines). Make the recipes depend
on what they use with `neoforge:conditions` if the other mod is optional.

## Things worth knowing

* Alterra's own items are in the `alterra` namespace (`alterra:peach`, `alterra:redwood_log`...). Use them in recipes and tags freely.
* Alterra's biomes carry the vanilla and common (`c:`) biome tags of the biome they are based on, so tag-based mods treat them correctly.
* Test with Alterra's jar and your jar in the same `mods` folder; the API is only a compile-time stub.
* Keep to the style: no glow, no marketing text, plain wording, vanilla-like pixel art.
