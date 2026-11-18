# The guide

Every feature of every mod, in the order you meet them. Settings are named as they appear in the game. Anything marked *server* happens in
the world; everything else is only drawn on your computer.

## Settings and the hub

- **The hub button** sits on the title, pause and options screens. Its icon turns through the logos of the mods; the tooltip names the one showing.
- **The hub window** lists the mods that have settings (Alterra, Atmos, Illuminate) with how many pages and settings each has. Open one to change it.
- **Each mod's window** has groups on the left (press one to open its pages), the settings of the open page on the right, a switch for on/off
  options and a slider for numbers. Changes apply at once and are saved. *Reset to defaults* resets the open page.
- The same options are in each mod's config file (`config/<modid>-client.toml`).

## Atmos (weather and sky)

### Weather
- **Biome weather**: sandstorms in deserts, fog banks in misty biomes, petal storms in blossom woods, aurora over snowy biomes at night,
  blizzards in snowy biomes, pollen on sunny days in flower biomes, ashfall in burnt biomes. Each has its own switch.
- **Storm rules**: *Follow vanilla weather* (rain and thunder bring each biome's own storm instead of plain rain), *Storm share* (how often),
  *Storms on their own* (a timer, without rain), *Distant storms* (see the next biome's storm on the horizon).
- **Rain**: rings where drops land on water, rain and snow slanted by the wind (drawn by Atmos, not vanilla), and how far the wind bends it.
- **Thunderstorms**: some thunderstorms are heavy (a gale, pouring rain, a dark sky), with lightning around you. *Heavy storm share*,
  *Lightning rate*, and *Real strikes* (what share set fire and do damage; the rest are for show).

### World
- **Ground**: footprints and puffs where things walk on snow, sand and mud; shore splashes (waves throw spray inland that wets the land);
  snow tracks (walking presses snow layers down); snow that settles on slopes; wind that moves sand into dunes and snow into drifts (blocks move);
  avalanches on steep slopes (heavy snowfall, explosions, lightning, or you running across).

### Disasters (*server*)
- **Tornadoes**: now and then in heavy storms, a funnel that travels with the wind. It can tear up soft blocks (leaves, plants, glass, snow,
  carpet, wool), pulls players, mobs, items and boats in, and has a continuous roar that gets louder as you get closer and pans towards it.
- **Hail** that hurts, and can break glass open to the sky. **Dust devils** (small harmless whirls of sand on hot days).
- **Whirlpools** in the open sea: the water really turns and drains down a carved funnel; boats and swimmers are drawn in and turned.
- *Pull things in* and *How often* apply to all disasters.

### Sound and performance
- *Weather volume* and *Tornado volume*. The wind, rain and storm loops are long and gusty so they do not sound like a repeat.
- *Intensity* (how many particles), *Adapt to frame rate*, *Step aside for other mods* (turn off an effect another weather or fog mod already does).

## Illuminate (the look)

### General
- **Quality** 0 to 2 (fewer samples on low).
- **Colour and glow**: the colour grade (a gentle film curve, warm light, cool shadows, with a strength slider), the glow around bright things (strength slider),
  and *Scene look*, the switch for the whole scene pass.

### Water
- **Surface**: water coloured by depth and biome, mirroring the sky, with the sun's glint, ripples and rain rings; *Reflections* (water, wet ground
  and metal mirror the world, not only the sky); *Waves* (slow swells, boats ride them); *Smooth water* (water that fills and drains moves smoothly);
  *Shore foam*. At sunrise and sunset the water mirrors the sky's colours and shows a wide path of the sun.
- **Underwater**: caustics on the bottom (and under water), fog that closes in with distance, and sun shafts that follow the sun. *Underwater
  strength* scales caustics and shafts. The surface seen from below is a mirror and a bright window to the sky.

### Sky
- **Clouds and haze**: real clouds, drifting with the wind; they go dark in storms (only nearby ones), darken in the evening, and inside a cloud
  you are in a soft white mist that fades in. *Cloud amount* sets the cover. *Haze* in the distance, with a strength slider.
- **Sun**: light shafts through trees (strength slider) and sun and moon shadows (a shadow map, off by default, with a strength slider).
- **Sunset and storms**: at sunrise and sunset the sky's real glow colours the clouds, the far haze and the water on the sun's side, and the sun
  and the land redden as the sun sinks. *Sunset strength*. Lightning flashes rise fast and fall fast; *Flash strength*.
- The sun and moon are round, with all moon phases.

### World
- **Snow**: drawn as its own smooth surface (rounded, no gaps, slopes between steps) instead of the blocks' layers.
- **Foliage**: leaves and plants sway in the wind (*Sway amount*).
- **Surfaces**: wet ground darkens in rain and puddles gather in low spots; metal shines; lava glows and lights the ground next to it.

## Kitchen

- **Drinks**: juices of every fruit, each in its own glass, made in the **Fruit Press**; mixed drinks (many named blends), smoothies and shakes;
  **wines**, ciders, mead and liqueurs aged in the **Keg**; mixed in the **Drink Mixer**.
- **Meals and dishes**: made at the **Stove**, **Oven**, **Kitchen Counter** and **Prep Table**; dishes are assembled from ingredients, each tinted
  by what is in it; pantry goods, seasonings, preserves, baking and wild foods (fiddlehead fern, duskberry bush, wild parsley, meadow cap, rosebay, rock salt).
- **Ice cream**: the **Ice Cream Stand**, **Ice Cream Machine** and a powered machine; scoops stack in a cone.
- **Crates** for every fruit, and a **Trellis** for grapes.
- **Taste and the Traveller's Handbook**: the first time you taste something it is written in the handbook you carry; the notes live on the book.
- Works with Create (millstone, pressing, mixing, bottling, in the machines you already have) and JEI.

## Prism Lib (for modders)

- **Services**: wind, clock, clouds, wetness, storms (with regional storm cells), the sea, water events, funnels, ground height, snow surface.
- **Disturbances**: one channel for steps, wheels, rotor wash, hulls, blasts and splashes; whatever reacts (snow, sand, water, leaves) listens.
- **Settings**: `Settings.category(modId, title, description, options, icon, group)` and `SettingsClient.install(...)` give a mod its own window and a place in the hub.
- **Particles**: its own particle engine (nothing goes through Minecraft's).
- **Grids**: a shallow-water solver and field grids.
- **Hub**: `PrismHub.register(...)` puts a mod in the hub with a logo (a tall strip of square frames is played as an animation).
