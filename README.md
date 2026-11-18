# Alterra addons

Addons for Alterra, a NeoForge 1.21.1 mod that adds biomes, tall mountains, sky islands and fruit trees. Alterra itself is closed source.
The addons, the API they use and these docs are open (MIT). Everything is version 1.0.

| Mod | What it is |
| --- | --- |
| **Atmos** (`alterra_weather`) | Weather and sky: biome storms, rain, heavy thunderstorms and lightning, tornadoes, hail, whirlpools, dust devils, footprints, avalanches, sounds |
| **Illuminate** (`alterra_illuminate`) | The look, with Sodium and no shader pack: water, caustics, real clouds, sunrise and sunset, sun shafts, shadows, snow, swaying leaves, wet ground |
| **Kitchen** (`alterra_kitchen`) | Juices, mixed drinks, wines, dishes, ice cream, a kitchen of stations, taste notes in the Traveller's Handbook |
| **Prism Lib** (`prismlib`) | The library under them: the settings hub, shared providers (wind, storms, sea), particles, grids, one disturbance channel |

Alterra has Farmer's Delight, Create and Aeronautics support built in. Atmos, Illuminate and Prism Lib also work without Alterra.

**[Read the guide](docs/guide.md)** for every feature of every mod, and what each setting does.

## Installing

Download the jars from [releases](../../releases) and put them in your mods folder. Illuminate needs Sodium and Prism Lib; Atmos needs Prism Lib;
Kitchen needs Alterra. Atmos's disasters are drawn by Illuminate.

In the game, Alterra can also install and update these for you: Mods > Alterra > Config > Addons. The Community tab shows addons from other people
(any public GitHub repo with the topic `alterra-addon`); nobody checks those, so the game asks twice before installing one.

## Settings

One button on the title, pause and options screens opens the Prism hub: a window with every mod that has settings. Its icon turns through the
mods' logos. Each mod has its own window, with groups, pages and sliders.

## Making your own

- [making-an-addon.md](docs/making-an-addon.md) has the template and the API
- [api.md](docs/api.md) is the Alterra API: creative tab, settings, wind, rivers, biomes, loading tips, version
- [publishing.md](docs/publishing.md) explains how to get it into the list
- [field-guide.md](docs/field-guide.md) (the Traveller's Handbook pages), [security.md](docs/security.md)

## Layout

`api/` is the API (only needed to compile), `template/` is a starting point, `addons.json` is the list the game reads, and `tools/`
has the release script, the list checker and the script that makes the weather sounds. Each addon has its own folder.
`./gradlew build` builds everything (Java 21). The addons compile against Prism Lib 1.0 (`./gradlew publishToMavenLocal` in its repo).
