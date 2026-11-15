package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.config.HeavyConfig;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The heavy thunderstorm: when vanilla thunder starts, a roll decides whether this one is a heavy one. A heavy storm lasts as long as the thunder does
 * and builds up and fades smoothly. It makes the wind a gale, the rain pour, the sky dark, and (on the server) strikes lightning near the players.
 * The server and the client each keep their own state; the roll is worked out from the time, so they agree.
 */
public final class HeavyStorm {
    private HeavyStorm() {}
    private static final float[] strength = new float[2];      // 0 server, 1 client
    private static final boolean[] roll = new boolean[2], wasThunder = new boolean[2];
    /** Game time /weather thunderstorm keeps it heavy until (0: not forced). Kept in step on the clients by ForcedStorm. */
    public static volatile long forcedUntil;

    private static int side(Level level) { return level.isClientSide ? 1 : 0; }

    /** 0..1: how heavy the storm is here, now. */
    public static float level(Level level) { return strength[side(level)]; }

    /** How heavy the storm is at a place: the world-wide heavy thunderstorm or a travelling cell over that spot, whichever is stronger. */
    public static float at(Level level, double x, double z) { return Math.max(level(level), StormCells.level(level.getGameTime(), x, z)); }
    /** The rain at a place: vanilla's rain level, or the downpour under a storm cell (a cell rains even when the weather is clear). */
    public static float rainAt(Level level, double x, double z) { return Math.max(level.getRainLevel(1f), Math.min(1f, 1.4f * StormCells.level(level.getGameTime(), x, z))); }

    public static void update(Level level) {
        int i = side(level); long time = level.getGameTime();
        float thunder = level.getThunderLevel(1f);
        boolean now = thunder > 0.05f;
        if (now && !wasThunder[i]) roll[i] = Storms.rollSlot(time / 600L) < HeavyConfig.get(HeavyConfig.SHARE);
        wasThunder[i] = now;
        boolean enabled = HeavyConfig.get(HeavyConfig.HEAVY);
        // it is only ever as heavy as the thunder is: when the weather is cleared (or the thunder runs out) the storm goes with it, forced or not
        float target = !enabled ? 0f : (time < forcedUntil || roll[i]) ? thunder : 0f;
        if (thunder <= 0.01f && forcedUntil != 0) forcedUntil = 0;
        strength[i] += Mth.clamp(target - strength[i], -0.006f, 0.006f);   // takes a few minutes to build up
    }
    public static void reset() { strength[0] = strength[1] = 0f; roll[0] = roll[1] = false; wasThunder[0] = wasThunder[1] = false; forcedUntil = 0; }
}
