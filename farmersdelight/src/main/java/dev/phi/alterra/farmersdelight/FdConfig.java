package dev.phi.alterra.farmersdelight;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FdConfig {
    private FdConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue EMBER_KNIFE_COOKS;
    static {
        var b = new ModConfigSpec.Builder();
        EMBER_KNIFE_COOKS = b.comment("The Ember Knife cooks what it cuts or kills.").define("emberKnifeCooks", true);
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) {
        try { return v.get(); } catch (IllegalStateException notLoaded) { return v.getDefault(); }
    }
}
