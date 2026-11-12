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
        SANDSTORMS = b.comment("Sandstorms in deserts.").define("sandstorms", true);
        FOG_BANKS = b.comment("Fog in misty biomes.").define("fogBanks", true);
        PETAL_STORMS = b.comment("Petal storms in blossom woods.").define("petalStorms", true);
        AURORA = b.comment("Northern lights over snowy biomes at night.").define("aurora", true);
        BLIZZARDS = b.comment("Blizzards in snowy biomes.").define("blizzards", true);
        POLLEN = b.comment("Pollen in flower biomes on sunny days.").define("pollen", true);
        ASHFALL = b.comment("Ash falling in burnt biomes.").define("ashfall", true);
        INTENSITY = b.comment("How many particles. 1.0 is normal, lower it if the game runs badly.").defineInRange("intensity", 1.0, 0.1, 2.0);
        STEP_ASIDE = b.comment("Turn off effects that another weather or fog mod already does (Weather2, Distant Horizons fog and so on).").define("stepAside", true);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
