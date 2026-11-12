package dev.phi.alterra.weather;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings of Alterra Weather (config/alterra_weather-client.toml, also on Alterra's settings screen). */
public final class WeatherConfig {
    private WeatherConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SANDSTORMS, FOG_BANKS, PETAL_STORMS, AURORA, BLIZZARDS, POLLEN, ASHFALL, STEP_ASIDE;
    public static final ModConfigSpec.DoubleValue INTENSITY;
    static {
        var b = new ModConfigSpec.Builder();
        SANDSTORMS = b.comment("Sandstorms in deserts while it rains elsewhere: blowing dust and close, sandy fog.").define("sandstorms", true);
        FOG_BANKS = b.comment("Fog banks in misty biomes at dawn and in rain.").define("fogBanks", true);
        PETAL_STORMS = b.comment("Gusts of petals in blossom woods when the weather turns.").define("petalStorms", true);
        AURORA = b.comment("An aurora over snowy biomes on clear nights.").define("aurora", true);
        BLIZZARDS = b.comment("Blizzards in snowy biomes while it snows: driven snow and a white-out.").define("blizzards", true);
        POLLEN = b.comment("Pollen drifting over flower biomes on warm, clear days.").define("pollen", true);
        ASHFALL = b.comment("Ash drifting down over burnt and ember biomes.").define("ashfall", true);
        INTENSITY = b.comment("How many particles the effects use (1.0 = normal; lower helps weak computers).").defineInRange("intensity", 1.0, 0.1, 2.0);
        STEP_ASIDE = b.comment("Switch off the effects that a weather, fog or sky mod you have installed already does (Weather2, Particle Rain, Distant Horizons fog...).").define("stepAside", true);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
