package dev.phi.alterra.weather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings for the heavy thunderstorms (config/alterra_weather-common.toml). They act on the server, so they live here and not in the client settings. */
public final class HeavyConfig {
    private HeavyConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HEAVY, LIGHTNING, FINITE_WATER, CLOUD_RAIN, AVALANCHES, SNOW_SETTLES, WIND_MOVES_LOOSE, SNOW_TRACKS;
    public static final ModConfigSpec.DoubleValue SHARE, LIGHTNING_RATE, REAL_BOLTS;
    static {
        var b = new ModConfigSpec.Builder();
        HEAVY = b.comment("Some thunderstorms are heavy: a gale, pouring rain, a dark sky and lightning all around.").define("heavyThunderstorms", true);
        SHARE = b.comment("How many thunderstorms are heavy ones (0 none, 1 all).").defineInRange("heavyShare", 0.6, 0.0, 1.0);
        AVALANCHES = b.comment("Snow on steep slopes breaks loose and slides down in heavy snowfall and blizzards, and when something explodes nearby.").define("avalanches", true);
        SNOW_SETTLES = b.comment("Snow is a loose pile: on a slope layers creep downhill by themselves, and a steep, deep slope is what avalanches start from.").define("snowSettles", true);
        WIND_MOVES_LOOSE = b.comment("Sandstorms lift loose sand off exposed edges and drop it downwind as dunes, blizzards carry snow downwind into drifts.").define("windMovesLoose", true);
        SNOW_TRACKS = b.comment("Walking on layers of snow presses them down: players and mobs leave real tracks in it.").define("snowTracks", true);
        CLOUD_RAIN = b.comment("Rain and snow fall only under thick clouds (everyone sees the same clouds; crops, fire and the rest follow them too). Rain brings more cloud and a thunderstorm nearly closes the sky.").define("cloudRain", true);
        FINITE_WATER = b.comment("Water is a real amount: it runs off, levels out and fills things up slowly, never makes endless sources, dries up and boils. The sea and rivers stay endless below sea level. Off by default: water flows as in the game.").define("finiteWater", false);
        LIGHTNING = b.comment("Heavy thunderstorms strike lightning around the players.").define("lightning", true);
        LIGHTNING_RATE = b.comment("How often. 1 is about one strike every few seconds near each player.").defineInRange("lightningRate", 1.0, 0.1, 4.0);
        REAL_BOLTS = b.comment("What share of the strikes are real (set fires, hurt things). The rest are for show.").defineInRange("realBolts", 0.15, 0.0, 1.0);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
