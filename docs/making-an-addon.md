# Making an addon

An addon is a normal NeoForge 1.21.1 mod that depends on Alterra. Alterra gives you a few hooks ([api.md](api.md)) and the in-game page
for installing.

## Template

Copy [template/](../template) into a new repo and rename things: the folder, `mod_id` in build.gradle, the package, the id in
neoforge.mods.toml and alterra-addon.json. The id is lowercase letters, digits and `_`. Starting it with `alterra_` helps people find it.
Use it as the namespace for everything you add.

What's in it: a build.gradle (compiles against the API jar in `libs/`), a mod class, neoforge.mods.toml, assets and data folders,
`alterra-addon.json` (what the in-game list reads) and a workflow that makes releases.

Your neoforge.mods.toml needs Alterra as a dependency:

```toml
[[dependencies.my_addon]]
modId="alterra"
type="required"
versionRange="[1.0,)"
ordering="AFTER"
side="BOTH"
```

## Hooks

All in `AlterraApi`. Call from your mod constructor.

```java
@Mod("alterra_example")
public class Example {
    public Example(IEventBus bus, ModContainer container) {
        AlterraApi.addTabSection(ResourceLocation.fromNamespaceAndPath("alterra_example", "things"), 0xFFF2E6C8,
                () -> List.of(new ItemStack(MyItems.THING.get())));
        AlterraApi.addSettingsCategory("Example", "What my addon does.", () -> new ItemStack(Items.APPLE),
                List.of(AlterraApi.Option.bool("Fancy mode", "Makes things fancy.", MyConfig.FANCY)));
    }
}
```

Guide pages don't need code, just a `alterra_example_guide.json` in the jar ([field-guide.md](field-guide.md)).

## Data-only addons

If you only have recipes, tags, loot tables or biome modifiers you don't need Java. A jar with a neoforge.mods.toml and a data folder is a mod.
[create/](../create) and [aeronautics/](../aeronautics) are like that. If the recipes use another mod, put a `neoforge:conditions` mod_loaded
condition on them.

## Notes

- Alterra's own stuff is in the `alterra` namespace (`alterra:peach`, `alterra:redwood_log`). Use it in recipes and tags.
- Alterra's biomes are in the vanilla and `c:` biome tags of the biome they're based on.
- The API jar is only for compiling. To test, put Alterra and your jar in the same mods folder.
