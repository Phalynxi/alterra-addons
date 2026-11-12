# Alterra addons

Open-source addons for **Alterra** (a NeoForge 1.21.1 mod: new biomes, mountains, sky islands and fruit trees). Alterra itself is
closed source; its addons, the addon API and this documentation are open (MIT).

## The addons

| Addon | What it adds | Needs |
|---|---|---|
| **Alterra Kitchen** (`alterra_kitchen`) | Fruit juices in their own glasses (also as fluids), a fruit press, fruit crates, a grape trellis | |
| **Alterra Weather** (`alterra_weather`) | Sandstorms, blizzards, fog banks, petal storms, pollen, ashfall and an aurora, each with its own schedule and wind | |
| **Alterra Music** (`alterra_music`) | Original background music for the biomes: cold, temperate, warm, dry, sea, caves and sky | |
| **Alterra for Farmer's Delight** (`alterra_farmersdelight`) | Crystal, Ember and Frost knives; cooking pot and cutting board recipes | Farmer's Delight |
| **Alterra for Create** (`alterra_create`) | Millstone, crushing and saw recipes; Astral Water; juice pressing, mixing and bottling | Create |
| **Alterra for Create Aeronautics** (`alterra_aeronautics`) | Clouds lift airships like levitite | Create Aeronautics |

## Installing

In the game open **Mods → Alterra → Config → Addons**. The page lists the addons here and lets you install, update and remove them
(restart the game afterwards). Or download a jar from the [releases](../../releases) and put it in your `mods` folder like any other mod.

The **Community** tab on that page lists addons other people made: any public GitHub repository with the topic
[`alterra-addon`](https://github.com/topics/alterra-addon). Those are not reviewed, and the game asks before installing one.

## Making your own addon

* [Making an addon](docs/making-an-addon.md) - the template, the API, data-only addons
* [Publishing](docs/publishing.md) - get it into the in-game list (by topic, or by pull request to `addons.json`)
* [The addon API](docs/api.md) · [The field guide format](docs/field-guide.md) · [Security](docs/security.md)

## This repository

```
api/            the small public API (AlterraApi); compile-time only, the real class comes with Alterra
kitchen/ weather/ music/ farmersdelight/ create/ aeronautics/    one folder per addon
template/       a starting point for a new addon (copy it into its own repository)
addons.json     the official list the game reads
tools/          release.py (build, hash, publish), validate_index.py (checks addons.json), the art and sound generators
```

Build everything with `./gradlew build` (Java 21); each addon's jar ends up in `<addon>/build/libs/`.
