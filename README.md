# Alterra addons

Addons for Alterra, a NeoForge 1.21.1 mod that adds biomes, tall mountains, sky islands and fruit trees. Alterra itself is closed source.
The addons, the API they use and these docs are open (MIT).

What's here:

- **alterra_kitchen**: juices (each with its own glass), a fruit press, crates, a grape trellis
- **alterra_weather**: sandstorms, blizzards, fog, petals, pollen, ash and an aurora, on their own timers
- **alterra_music**: background music for the biomes
- **alterra_farmersdelight**: three knives and some recipes (needs Farmer's Delight)
- **alterra_create**: Create recipes, including juice pressing and bottling (needs Create)
- **alterra_aeronautics**: clouds lift airships (needs Create Aeronautics)

## Installing

In the game: Mods > Alterra > Config > Addons. It lists these, you press Install, then restart. Updates and removing work the same way.
Or just download a jar from [releases](../../releases) and put it in your mods folder.

The Community tab on that page shows addons from other people: any public GitHub repo with the topic `alterra-addon`. Nobody checks those,
so the game asks twice before installing one.

## Making your own

- [making-an-addon.md](docs/making-an-addon.md) has the template and the API
- [publishing.md](docs/publishing.md) explains how to get it into the list
- [api.md](docs/api.md), [field-guide.md](docs/field-guide.md), [security.md](docs/security.md)

## Layout

`api/` is the small API (only needed to compile), `template/` is a starting point, `addons.json` is the list the game reads, and `tools/`
has the release script, the list checker and the scripts that make the weather sounds and the music. Each addon has its own folder.
`./gradlew build` builds everything (Java 21).
