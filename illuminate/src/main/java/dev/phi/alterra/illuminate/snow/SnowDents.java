package dev.phi.alterra.illuminate.snow;

import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The fine dents in the snow: a heightfield at four cells to the block, kept only where something has stepped (a sparse set of 16 by 16 block tiles), added to the snow surface when it is
 * drawn. A foot presses a round dent down and heaps a little of what it displaced in a ring around it; fresh snowfall and wind fill the dents in again over minutes. This is the fine
 * detail; the layers pressed down for real by the weather mod (an eighth of a block at a time) are the coarse part under it.
 */
public final class SnowDents {
    private SnowDents() {}
    public static final int R = 4, TC = 16 * R;
    private static final class D { final float[] h = new float[TC * TC]; int version = 1; }
    private static final Map<Long, D> TILES = new HashMap<>();

    private static long key(int tx, int tz) { return ((long) tx << 32) | (tz & 0xFFFFFFFFL); }

    /** Changes whenever the dents in a tile change (0 when the tile has none): the snow surface rebuilds the tile when it does. */
    public static int version(int tx, int tz) { D d = TILES.get(key(tx, tz)); return d == null ? 0 : d.version; }

    /** The dent height at a point, in blocks (negative: pressed down, positive: heaped). */
    public static float at(double x, double z) {
        double fx = x * R - 0.5, fz = z * R - 0.5; int cx = Mth.floor(fx), cz = Mth.floor(fz); float tx = (float) (fx - cx), tz = (float) (fz - cz);
        return Mth.lerp(tz, Mth.lerp(tx, cell(cx, cz), cell(cx + 1, cz)), Mth.lerp(tx, cell(cx, cz + 1), cell(cx + 1, cz + 1)));
    }
    private static float cell(int cx, int cz) {
        D d = TILES.get(key(Math.floorDiv(cx, TC), Math.floorDiv(cz, TC))); if (d == null) return 0f;
        return d.h[Math.floorMod(cx, TC) + Math.floorMod(cz, TC) * TC];
    }

    /** A foot, a paw or a wheel presses the snow at a point: a round dent of the given radius and depth (blocks), and a ring of heaped snow round it. */
    public static void stamp(double x, double z, float radius, float depth) {
        float outer = radius * 1.7f; int x0 = Mth.floor((x - outer) * R), x1 = Mth.floor((x + outer) * R), z0 = Mth.floor((z - outer) * R), z1 = Mth.floor((z + outer) * R);
        for (int cz = z0; cz <= z1; cz++) for (int cx = x0; cx <= x1; cx++) {
            double px = (cx + 0.5) / R, pz = (cz + 0.5) / R, d = Math.hypot(px - x, pz - z);
            if (d >= outer) continue;
            D t = TILES.computeIfAbsent(key(Math.floorDiv(cx, TC), Math.floorDiv(cz, TC)), k -> new D()); int i = Math.floorMod(cx, TC) + Math.floorMod(cz, TC) * TC;
            if (d < radius) { float v = -depth * (float) (0.5 + 0.5 * Math.cos(Math.PI * d / radius)); if (v < t.h[i]) { t.h[i] = v; t.version++; } }
            else { float v = depth * 0.30f * (float) Math.sin(Math.PI * (d - radius) / (outer - radius)); if (t.h[i] >= 0f && v > t.h[i]) { t.h[i] = v; t.version++; } }
        }
    }

    /** Fresh snow and wind fill the dents in: every few seconds each dent loses a little (about nine minutes to half in calm dry weather, much faster in a snowfall). */
    public static void relax(float rate) {
        for (var it = TILES.values().iterator(); it.hasNext(); ) {
            D d = it.next(); boolean any = false;
            for (int i = 0; i < d.h.length; i++) { float v = d.h[i]; if (v == 0f) continue; v *= rate; if (Math.abs(v) < 0.004f) v = 0f; else any = true; d.h[i] = v; }
            d.version++;
            if (!any) it.remove();
        }
    }
    public static void clear() { TILES.clear(); }
    public static boolean any() { return !TILES.isEmpty(); }
}
