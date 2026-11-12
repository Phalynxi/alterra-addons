package dev.phi.alterra.music;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MusicConfig {
    private MusicConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, SKY;
    public static final ModConfigSpec.IntValue MIN_MINUTES, MAX_MINUTES;
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Use Alterra's music in its biomes instead of the vanilla tracks.").define("enabled", true);
        SKY = b.comment("A track for the sky islands.").define("skyMusic", true);
        MIN_MINUTES = b.comment("Shortest pause between tracks, in minutes.").defineInRange("minMinutes", 3, 0, 30);
        MAX_MINUTES = b.comment("Longest pause between tracks, in minutes.").defineInRange("maxMinutes", 8, 1, 60);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
