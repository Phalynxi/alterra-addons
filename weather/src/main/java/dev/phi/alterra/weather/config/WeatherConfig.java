package dev.phi.alterra.weather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings of Atmos (config/alterra_weather-client.toml, also on Alterra's settings screen). */
public final class WeatherConfig {
    private WeatherConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SANDSTORMS, FOG_BANKS, PETAL_STORMS, AURORA, BLIZZARDS, POLLEN, ASHFALL, STEP_ASIDE, ADAPT, FOLLOW_VANILLA, OWN_STORMS, DISTANT, REPLACE_RAIN, SWAY, FOOTPRINTS, SHORE_SPLASH, SWAY_PLANTS, RIPPLES, FOAM, WATER_LOOK, CAUSTICS, LAVA_GLOW, WATER_WAVES, SCENE_LOOK;
    public static final ModConfigSpec.DoubleValue INTENSITY, STORM_SHARE, RAIN_SLANT, SWAY_AMOUNT, CAUSTIC_STRENGTH;
    static {
        var b = new ModConfigSpec.Builder();
        SANDSTORMS = b.comment("Sandstorms in deserts.").define("sandstorms", true);
        FOG_BANKS = b.comment("Fog in misty biomes.").define("fogBanks", true);
        PETAL_STORMS = b.comment("Petal storms in blossom woods.").define("petalStorms", true);
        AURORA = b.comment("Northern lights over snowy biomes at night.").define("aurora", true);
        BLIZZARDS = b.comment("Blizzards in snowy biomes.").define("blizzards", true);
        POLLEN = b.comment("Pollen in flower biomes on sunny days.").define("pollen", true);
        ASHFALL = b.comment("Ash falling in burnt biomes.").define("ashfall", true);
        INTENSITY = b.comment("How many particles. 1.0 is normal, lower it if the game runs badly.").defineInRange("intensity", 1.0, 0.1, 2.0);
        ADAPT = b.comment("Use fewer particles when the frame rate drops.").define("adaptToFrameRate", true);
        FOLLOW_VANILLA = b.comment("Storms follow vanilla weather: when it rains or thunders, every biome shows its own version (sandstorm in deserts, blizzard in snow, petals in blossom woods, ash where it burnt, mist in misty places).").define("followVanillaWeather", true);
        STORM_SHARE = b.comment("How often vanilla rain turns into the biome storm instead of plain rain or snow. 1 is the normal share (deserts always, other biomes about half the time), 0 never.").defineInRange("stormShare", 1.0, 0.0, 2.0);
        DISTANT = b.comment("See a storm in the next biome on the horizon before you reach it.").define("distantStorms", true);
        REPLACE_RAIN = b.comment("Draw rain and snow with Alterra's own, blown along by the wind, instead of vanilla's straight-down sheets.").define("replaceRain", true);
        RAIN_SLANT = b.comment("How far the wind pushes the rain sideways. 1 is normal.").defineInRange("rainSlant", 1.0, 0.0, 1.5);
        SWAY = b.comment("Leaves and plants sway in the wind (made in the shader, with Sodium or without). Needs a restart to switch on or off.").define("swayingFoliage", true);
        SWAY_PLANTS = b.comment("Grass and flowers sway too (with Sodium only).").define("swayingPlants", true);
        SWAY_AMOUNT = b.comment("How far things sway. 1 is normal.").defineInRange("swayAmount", 1.0, 0.0, 2.5);
        RIPPLES = b.comment("Raindrops that land on water make rings.").define("rainRipples", true);
        FOAM = b.comment("A band of foam along the water's edge that moves in and out.").define("shoreFoam", true);
        CAUSTICS = b.comment("Rippling light on the bottom of shallow water, in daylight.").define("waterCaustics", true);
        WATER_WAVES = b.comment("The water surface rises and falls in slow swells (with Sodium only).").define("waterWaves", true);
        SCENE_LOOK = b.comment("A softer, warmer look for the whole scene (with Sodium only, no shader pack): colour grading, a gentle glow on bright things, light shafts from the sun and haze in the distance.").define("sceneLook", true);
        WATER_LOOK = b.comment("The water reflects the sky and the sun or moon and shows ripple normals instead of the animated water picture (with Sodium only, no shader pack). Costs next to nothing: it is a few lines in the water shader.").define("waterReflections", true);
        LAVA_GLOW = b.comment("Flickering glow on lava and on the ground next to it.").define("lavaGlow", true);
        CAUSTIC_STRENGTH = b.comment("How bright the caustics and the lava glow are.").defineInRange("causticStrength", 1.0, 0.1, 2.0);
        OWN_STORMS = b.comment("Storms also come on their own timer, without vanilla rain.").define("ownStorms", true);
        FOOTPRINTS = b.comment("Prints and puffs of dust or snow where you and other things walk on snow, sand and mud.").define("footprints", true);
        SHORE_SPLASH = b.comment("Waves break on the shore and throw spray inland, which wets the land (more in storms and wind).").define("shoreSplashes", true);
        STEP_ASIDE = b.comment("Turn off effects that another weather or fog mod already does (Weather2, Distant Horizons fog and so on).").define("stepAside", true);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
