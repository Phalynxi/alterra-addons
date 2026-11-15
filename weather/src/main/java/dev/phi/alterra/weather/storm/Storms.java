package dev.phi.alterra.weather.storm;

import net.minecraft.util.Mth;

/**
 * When storms happen. Not vanilla rain: every kind has its own timer, worked out from the game time and the player's region (2048-block squares),
 * so clients agree without sending anything. A storm builds up, holds, fades, and its wind has a fixed direction.
 */
public final class Storms {
    private Storms() {}

    /** period: ticks per cell; chance: how often a cell has a storm; duration: ticks at full strength (min, max); ramp: ticks to build / fade. */
    public enum Kind {
        SANDSTORM("sandstorms", 36000, 0.75f, 5000, 9000, 1800),
        BLIZZARD("blizzards", 36000, 0.75f, 5000, 9000, 1800),
        FOG("fog_banks", 24000, 0.85f, 4000, 8000, 2400),
        PETALS("petal_storms", 24000, 0.65f, 2500, 5000, 900),
        POLLEN("pollen", 24000, 0.75f, 6000, 10000, 1500),
        ASH("ashfall", 30000, 0.7f, 6000, 10000, 1800),
        AURORA("aurora", 24000, 0.8f, 7000, 12000, 1500);
        public final String tag; final long period; final float chance; final int minDur, maxDur, ramp;
        Kind(String tag, long period, float chance, int minDur, int maxDur, int ramp) { this.tag = tag; this.period = period; this.chance = chance; this.minDur = minDur; this.maxDur = maxDur; this.ramp = ramp; }
    }

    public static final int REGION = 2048;

    /** Storms switched on with /weather: the game time they last until. The server tells the clients, so everyone sees (and the wind and the trees feel) the same. */
    public static final java.util.Map<Kind, Long> FORCED_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean forced(Kind k, long time) { Long u = FORCED_UNTIL.get(k); return u != null && time < u; }

    private static long mix(long a, long b, long c, long d) {
        long h = a * 0x9E3779B97F4A7C15L ^ (b + 0x7F4A7C15L) * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L ^ d * 0x27D4EB2F165667C5L;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L; h ^= h >>> 33;
        return h;
    }
    public static int region(double coordinate) { return Math.floorDiv((int) Math.floor(coordinate), REGION); }

    /** 0..1: how far into its storm this region is at this time (0 = none, building and fading eased, 1 = full). */
    public static float level(Kind k, long time, int rx, int rz) {
        if (forced(k, time)) return 1f;
        long cell = Math.floorDiv(time, k.period), local = time - cell * k.period;
        long h = mix(k.ordinal() + 1, cell, rx, rz);
        if (((h >>> 8) & 0xFFFF) / 65536f >= k.chance) return 0f;
        long dur = k.minDur + ((h >>> 24) & 0xFFFF) * (k.maxDur - k.minDur) / 65536L;
        long room = k.period - dur - 2L * k.ramp;
        long start = k.ramp + (room > 0 ? ((h >>> 40) & 0xFFFF) * room / 65536L : 0);   // when it reaches full strength
        long end = start + dur;
        if (local < start - k.ramp || local > end + k.ramp) return 0f;
        float t = local < start ? (float) (local - (start - k.ramp)) / k.ramp : local > end ? 1f - (float) (local - end) / k.ramp : 1f;
        return t * t * (3f - 2f * t);
    }

    /** 0..1, the same for everyone in this region at this time: used to decide whether a vanilla rain turns into this storm. */
    public static float roll(Kind k, long time, int rx, int rz) { return ((mix(k.ordinal() + 41, time, rx, rz) >>> 12) & 0xFFFF) / 65536f; }

    /** 0..1 for a slot of game time (600 ticks), the same everywhere: for deciding whether a thunderstorm is a heavy one. */
    public static float rollSlot(long slot) { return ((mix(77, slot, 5, 9) >>> 12) & 0xFFFF) / 65536f; }

    /** The direction (radians) this storm's wind blows towards; fixed for the length of the storm. */
    public static double wind(Kind k, long time, int rx, int rz) {
        long cell = Math.floorDiv(time, k.period);
        return (mix(k.ordinal() + 17, cell, rx, rz) >>> 11 & 0xFFFF) / 65536.0 * Mth.TWO_PI;
    }
}
