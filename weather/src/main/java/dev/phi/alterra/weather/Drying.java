package dev.phi.alterra.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/** How fast wet things dry where it is: faster when it is warm, slower when it is cold. It uses Legendary Survival Overhaul's temperature at that spot when that mod is there, and the biome's own temperature otherwise. 1 is the normal pace. */
public final class Drying {
    private Drying() {}
    private static Method world; private static boolean tried;

    public static float factor(Level level, BlockPos pos) {
        float t;
        Float lso = lso(level, pos);
        if (lso != null) t = lso;
        else t = level.getBiome(pos).value().getBaseTemperature() * 20f;   // 0.8 (a plains) is 16
        return Math.max(0.2f, Math.min(2.5f, 0.35f + (t - 4f) / 20f));
    }

    private static Float lso(Level level, BlockPos pos) {
        if (!tried) {
            tried = true;
            try { world = Class.forName("sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil").getMethod("getWorldTemperature", Level.class, BlockPos.class); } catch (Throwable t) { world = null; }
        }
        if (world == null) return null;
        try { return ((Number) world.invoke(null, level, pos)).floatValue(); } catch (Throwable t) { world = null; return null; }
    }
}
