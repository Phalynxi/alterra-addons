package dev.phi.alterra.weather.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings for the disasters (config/alterra_weather-disasters.toml). They act on the server. They only happen where Alterra Illuminate is installed too: it draws them. */
public final class DisasterConfig {
    private DisasterConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue TORNADOES, DUST_DEVILS, VORTEXES, HAIL, TORNADO_BLOCKS, HAIL_GLASS, PULL_THINGS;
    public static final ModConfigSpec.DoubleValue RATE;
    static {
        var b = new ModConfigSpec.Builder();
        TORNADOES = b.comment("Tornadoes now and then in heavy thunderstorms: a funnel that travels with the wind, throws things around and tears up leaves and plants.").define("tornadoes", true);
        DUST_DEVILS = b.comment("Small harmless whirls of sand in the desert on hot, dry days.").define("dustDevils", true);
        VORTEXES = b.comment("Whirlpools in the open sea that pull boats and swimmers in.").define("vortexes", true);
        HAIL = b.comment("Some heavy thunderstorms bring hail: stones that hurt and bounce, and now and then break glass.").define("hail", true);
        TORNADO_BLOCKS = b.comment("Tornadoes lift soft blocks (leaves, plants, glass, snow, carpet, wool) and fling them away. Off: they only move things that are free (players, mobs, items).").define("tornadoBlocks", true);
        HAIL_GLASS = b.comment("Hail can break glass and panes that are open to the sky.").define("hailBreaksGlass", true);
        PULL_THINGS = b.comment("Disasters push and pull players, mobs, items and boats. Off: they are only for show.").define("pullThings", true);
        RATE = b.comment("How often disasters start. 1 is normal, 0 never, 3 often.").defineInRange("rate", 1.0, 0.0, 3.0);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
