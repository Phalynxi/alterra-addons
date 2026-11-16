package dev.phi.alterra.weather.disaster;

import dev.phi.prismlib.grid.ShallowWater;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * A whirlpool as water on a grid of its own (see {@link ShallowWater}): the water is made to turn round the centre the way a real vortex turns (fastest at the edge of the core, slower
 * outside, nothing at the rim), and the dip in the middle is what the water does about it: the surface falls until its slope holds the turning water together, and rises a little all
 * round. When the whirlpool is over the forcing stops and the water runs back in as waves. The server runs one for what is afloat (boats are held at its surface, swimmers and items are
 * carried by its flow and pulled down where it is deep) and every client runs its own to draw; both start from the same numbers, so they agree closely enough to see and feel.
 */
public final class WhirlpoolSim {
    public final ShallowWater grid;
    public final double x, y, z; public final float radius;
    private float strength, spin, age;
    /** How hard the water is driven, and gravity: together they set how deep the funnel gets (about 7 blocks for a full strength whirlpool of radius 10). */
    private static final float GAIN = 1.75f, GRAVITY = 6f;

    public WhirlpoolSim(Level level, double x, double y, double z, float radius, float strength, float spin) {
        this.x = x; this.y = y; this.z = z; this.radius = radius; this.strength = strength; this.spin = spin;
        int n = Mth.clamp(((int) Math.ceil(2.0 * (radius * 1.9 + 4.0) / 0.5) + 1) & ~1, 48, 200);
        grid = new ShallowWater(n, 0.5f, x, z); grid.gravity = GRAVITY; grid.depth = (float) Mth.clamp(waterDepth(level, x, y, z), 2.0, 8.0);
        grid.forcing = this::force;
    }

    /** How deep the water is under a point of the surface, in blocks (stops at 12: the open sea is deeper than any funnel). */
    static double waterDepth(Level l, double x, double y, double z) {
        var p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y - 0.5), Mth.floor(z)); int d = 0;
        while (d < 12 && l.getFluidState(p).is(FluidTags.WATER)) { d++; p.move(0, -1, 0); }
        return Math.max(2, d);
    }

    /** One tick (a twentieth of a second). strength 0 lets the water settle. */
    public void tick(float strength, float spin) {
        this.strength = strength; this.spin = spin; age += 0.05f; grid.step(0.03f);   // the water moves at a calm pace (a bit slower than real time)
        for (int i = 0; i < grid.eta.length; i++) if (grid.eta[i] > 0f) grid.eta[i] = 0f;   // the water only follows the vortex down: no mound round the rim
    }

    /** Has the water settled after the whirlpool is over (nothing more to draw)? */
    public boolean quiet() { float m = 0f; for (float e : grid.eta) m = Math.max(m, Math.abs(e)); return strength <= 0.001f && m < 0.04f; }

    private void force(ShallowWater g, float dt) {
        float ramp = Math.min(1f, age / 2.5f) , k = Math.min(1f, 3f * dt) * ramp;   // it takes a couple of seconds to get up to speed
        int n = g.n; double reach = radius * 1.7;
        int r = (int) Math.ceil(reach / g.cell), ci = (int) Math.floor((x - g.ox) / g.cell), cj = (int) Math.floor((z - g.oz) / g.cell);
        for (int j = Math.max(1, cj - r); j <= Math.min(n - 2, cj + r); j++) for (int i = Math.max(1, ci - r); i <= Math.min(n - 2, ci + r); i++) {
            double rx = g.worldX(i) - x, rz = g.worldZ(j) - z, d = Math.sqrt(rx * rx + rz * rz);
            if (d < 0.2 || d > reach) continue;
            double sp = Vortex.speed(d, radius, strength) * 20.0 * GAIN, ux = rx / d, uz = rz / d;   // blocks per second
            double inward = sp * (0.25 + 0.5 * Math.max(0.0, 1.0 - d / (radius * 0.75)));   // the drain: water is drawn in, most near the core
            double tx = -uz * spin * sp - ux * inward, tz = ux * spin * sp - uz * inward;
            int c = i + j * n; g.u[c] += (float) ((tx - g.u[c]) * k); g.w[c] += (float) ((tz - g.w[c]) * k);
        }
    }
}
