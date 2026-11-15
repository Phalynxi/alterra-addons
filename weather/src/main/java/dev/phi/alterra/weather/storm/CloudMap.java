package dev.phi.alterra.weather.storm;

import net.minecraft.world.level.Level;

/**
 * Where the clouds are: one function of position and time that the server, every game and the scene shader (PostFx, the same maths in GLSL) work out alike,
 * so everyone sees the same clouds and the rain falls only where they are thick. Rain brings more cloud, a thunderstorm nearly closes the sky.
 * The noise uses integer hashing so Java and the graphics card give the same numbers.
 */
public final class CloudMap {
    private CloudMap() {}
    public static final double SPAN = 4_194_304.0;   // coordinates are wrapped only this far out: the noise is not periodic, so wrapping nearer made the whole sky change at once, every few thousand blocks

    private static float hash(int x, int y, int z) {
        int n = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        n = (n ^ (n >>> 13)) * 1274126177; n ^= n >>> 16;
        return (n & 0xFFFFFF) / 16777216f;
    }
    private static float smooth(float t) { return t * t * t * (t * (t * 6f - 15f) + 10f); }   // the same blend as the shader
    public static float noise(float x, float y, float z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        float fx = smooth(x - ix), fy = smooth(y - iy), fz = smooth(z - iz);
        float a = lerp(lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx), lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy);
        float b = lerp(lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx), lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy);
        return lerp(a, b, fz);
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static float fbm(float x, float y, float evo) {
        float v = 0f, w = 0.5f;
        for (int i = 0; i < 3; i++) { v += noise(x, y, 0.37f * i + evo) * w; x = x * 2.03f + 17.1f; y = y * 2.03f + 17.1f; w *= 0.5f; }
        return v;
    }

    /** How far the clouds have drifted along x and z: steadily east, wandering slowly north and south over hours (the same everywhere, from the world's time). */
    public static double drift(long gameTime, float pt) { double t = gameTime % 1_000_000L + pt; return t * 0.03 + 900.0 * Math.sin(t * 0.0000131); }
    public static double driftZ(long gameTime, float pt) { double t = gameTime % 1_000_000L + pt; return 1400.0 * Math.sin(t * 0.0000087 + 1.3) + 600.0 * Math.sin(t * 0.0000231); }
    /** How far the clouds have changed: they slowly form and fade (a full change takes about half a day). */
    public static float evolve(long gameTime, float pt) { return (float) (((gameTime % 1_000_000L) + pt) * 0.00008); }

    /** The cloud cover over a point, 0 (clear) to 1 (thick), with the weather (rain and thunder add cloud). */
    public static float cover(Level l, double x, double z, float pt) {
        double cx = (x + drift(l.getGameTime(), pt)) % SPAN, cz = (z + driftZ(l.getGameTime(), pt) + 7.92) % SPAN;
        if (cx < 0) cx += SPAN; if (cz < 0) cz += SPAN;
        float raw = fbm((float) cx * 0.0065f, (float) cz * 0.0065f, evolve(l.getGameTime(), pt)) + 0.16f * l.getRainLevel(pt) + 0.22f * l.getThunderLevel(pt) + 0.55f * StormCells.level(l.getGameTime(), x, z);   // the same as the shader: storms bring more cloud but leave gaps
        return Math.max(0f, Math.min(1f, (raw - 0.47f) / 0.15f));   // the same as the shader: scattered cumulus in fair weather
    }

    /** Is it raining at this spot: the weather is rainy and a thick enough cloud is overhead. */
    public static boolean rainsHere(Level l, double x, double z) { return StormCells.level(l.getGameTime(), x, z) > 0.1f || cover(l, x, z, 1f) > 0.12f; }   // under any real cloud (only clear gaps stay dry) }
}
