package dev.phi.alterra.weather.water;

import net.minecraft.world.phys.Vec3;

/**
 * Foam on the water, drawn by the water shader as part of the surface (not as particles): patches where a splash landed, along the foot of a waterfall and in the churned trail behind something fast.
 * They are kept as a short list of (position, age, size) that the shader turns into blocky flecks that thin out and fade over a few seconds.
 */
public final class Foam {
    private Foam() {}
    public static final int MAX = 40; static final double LIFE = 4.5;
    private static final double[] ex = new double[MAX], ez = new double[MAX], et = new double[MAX]; private static final float[] er = new float[MAX];
    private static int count, head;

    public static void add(double x, double z, double radius) {
        if (SubLevels.inside(x, z)) return;
        double t0 = Ripples.now();
        for (int i = 0; i < count; i++) { double dx = ex[i] - x, dz = ez[i] - z; if (t0 - et[i] < 0.8 && dx * dx + dz * dz < 1.0) return; }   // one patch per spot, not a pile
        ex[head] = x; ez[head] = z; et[head] = Ripples.now(); er[head] = (float) Math.max(0.15, Math.min(2.5, radius)); head = (head + 1) % MAX; count = Math.min(MAX, count + 1);
    }

    public static int snapshot(float[] out, Vec3 cam) {
        double t = Ripples.now(); int n = 0;
        for (int i = 0; i < count; i++) {
            double age = t - et[i];
            if (age > LIFE) continue;
            out[n * 4] = (float) (ex[i] - cam.x); out[n * 4 + 1] = (float) (ez[i] - cam.z); out[n * 4 + 2] = (float) age; out[n * 4 + 3] = er[i]; n++;
        }
        return n;
    }
}
