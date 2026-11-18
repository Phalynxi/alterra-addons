# API

`dev.phi.alterra.api.AlterraApi`, source in [api/](../api/src/main/java/dev/phi/alterra/api/AlterraApi.java). Compile against it with
`compileOnly project(':api')` or the jar from a release. At runtime the class comes with Alterra. Call everything from your mod
constructor, Alterra uses it after all mods have loaded.

## Creative tab

```java
AlterraApi.addTabSection(ResourceLocation id, int textColor, Supplier<List<ItemStack>> items)
AlterraApi.addTabItems(Supplier<List<ItemStack>> items)    // goes in the last section, "Boats & More"
```

A section needs two things in your own namespace:

- a banner at `assets/<ns>/textures/gui/fancy_tab_section/<path>.png`, 162x288 (16 frames of 162x18), with a `.png.mcmeta` for the animation
- a lang key `section.<ns>.<path>` for the title

The tab uses Fancy Tab Sections, which is bundled in Alterra.

## Settings

```java
AlterraApi.addSettingsCategory(String name, String description, Supplier<ItemStack> icon, List<Option> options)
Option.bool(name, description, ModConfigSpec.BooleanValue)
Option.number(name, description, ModConfigSpec.DoubleValue, min, max, step, unit)
Option.integer(name, description, ModConfigSpec.IntValue, min, max, prefix)
```

The values live in your own ModConfigSpec (register it with `container.registerConfig`). Alterra shows the page and saves your spec when the
screen closes.

## Field guide

No call needed, add `<modid>_guide.json` ([field-guide.md](field-guide.md)).

## Rivers

```java
AlterraApi.riverSurface(ServerLevel level, int x, int z)   // the river's water surface over the column, NaN when there is none
AlterraApi.isRiver(level, x, z)
```

Without Alterra these answer "no river".

## Biomes

```java
AlterraApi.biomes()            // every Alterra biome: id, display name, dimension, enabled
AlterraApi.biomeEnabled(id)    // whether it is switched on in Alterra's settings (true when unknown)
```

## Loading tips

```java
AlterraApi.addLoadingTip("Your tip")   // shown on the loading screen among Alterra's own
```

## Version

`AlterraApi.apiVersion()` is 2 (1: tab, settings, wind, compat; 2: rivers, biomes, loading tips). Check it before calling newer methods.
