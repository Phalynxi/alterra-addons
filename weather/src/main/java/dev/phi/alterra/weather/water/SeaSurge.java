package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.net.SeaSync;

/**
 * The client's copy of the storm surge: the last two squares the server sent, blended over half a second so the water rises and falls smoothly.
 * The water shader draws the sea this much higher, and the local player swims and floats against the same height (see {@link Surge}).
 */
public final class SeaSurge {
    private SeaSurge() {}
    private static final int N = SeaSync.N, CELL = SeaGrid.CELL;
    private static SeaSync prev, cur;
    private static long received;
    private static volatile boolean active;

    public static synchronized void received(SeaSync p) {
        if (p.h().length != N * N) return;
        prev = cur == null ? p : cur; cur = p; received = System.nanoTime();
        active = any(prev) || any(cur);
    }

    private static boolean any(SeaSync s) { for (byte b : s.h()) if (b != 0) return true; return false; }

    /** Whether there is any surge to draw (it lapses if the server stops sending, as when leaving a world). */
    public static boolean active() {
        if (active && System.nanoTime() - received > 3_000_000_000L) active = false;
        return active;
    }

    /** The surge at a point, in blocks. */
    public static synchronized float height(double x, double z) {
        if (!active() || cur == null) return 0f;
        float t = Math.min(1f, (System.nanoTime() - received) / 5.0e8f);
        return sample(prev, x, z) * (1f - t) + sample(cur, x, z) * t;
    }

    private static float sample(SeaSync s, double x, double z) {
        double fx = x / CELL - 0.5 - s.cx(), fz = z / CELL - 0.5 - s.cz();
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz); double tx = fx - x0, tz = fz - z0;
        double a = cell(s, x0, z0) + (cell(s, x0 + 1, z0) - cell(s, x0, z0)) * tx, b = cell(s, x0, z0 + 1) + (cell(s, x0 + 1, z0 + 1) - cell(s, x0, z0 + 1)) * tx;
        double edge = Math.min(Math.min(fx, fz), Math.min(N - 1 - fx, N - 1 - fz));   // fade out over the last few cells, so the square has no edge
        return (float) ((a + (b - a) * tz) * Math.max(0.0, Math.min(1.0, edge / 4.0)));
    }

    private static double cell(SeaSync s, int x, int z) { return x < 0 || z < 0 || x >= N || z >= N ? 0.0 : s.h()[x + z * N] / 100.0; }
}
