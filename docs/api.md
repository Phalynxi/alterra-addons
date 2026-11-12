# The addon API

`dev.phi.alterra.api.AlterraApi` (source: [`api/`](../api/src/main/java/dev/phi/alterra/api/AlterraApi.java)). Compile against it with
`compileOnly project(':api')` (or the jar from a release); at run time the class comes with Alterra. Call everything from your mod
constructor: the calls only collect, and Alterra applies them once every mod is loaded.

## Creative tab

```java
AlterraApi.addTabSection(ResourceLocation id, int textColor, Supplier<List<ItemStack>> items)
AlterraApi.addTabItems(Supplier<List<ItemStack>> items)       // for the last section ("Boats & More")
```

A section needs a banner and a title in your own namespace:

* `assets/<ns>/textures/gui/fancy_tab_section/<path>.png` - 162 x 288 px: 16 animation frames of 162 x 18 (plus `<path>.png.mcmeta` for the animation, like the vanilla format)
* `section.<ns>.<path>` in your lang file - the title

The tab is [Fancy Tab Sections](https://modrinth.com/mod/fancytabsections) (bundled in Alterra).

## Settings

```java
AlterraApi.addSettingsCategory(String name, String description, Supplier<ItemStack> icon, List<Option> options)
Option.bool(name, description, ModConfigSpec.BooleanValue)
Option.number(name, description, ModConfigSpec.DoubleValue, min, max, step, unit)
Option.integer(name, description, ModConfigSpec.IntValue, min, max, prefix)
```

The values belong to your own `ModConfigSpec` (register it with `container.registerConfig`); Alterra shows the page and saves the specs
when the screen closes.

## Field guide

No call: ship `<modid>_guide.json` ([format](field-guide.md)).

## Addon manager

The in-game page is Alterra's; there is nothing to call. See [publishing](publishing.md).
