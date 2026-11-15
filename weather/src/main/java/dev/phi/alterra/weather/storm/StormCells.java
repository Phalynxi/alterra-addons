package dev.phi.alterra.weather.storm;

import net.minecraft.util.Mth;

import java.util.List;

/**
 * Regional thunderstorms that travel. Cells are a pure function of the world's game time and place, like {@link Storms} and {@link CloudMap}: the server, every client and the scene
 * shader work out the same cells without anything being sent. The world is a lattice of 1600-block squares; each square and each 30000-tick epoch may give birth to one cell
 * (nearly all do) at a random spot and a random moment, which then drifts east-ish (the way the clouds go) at 0.6 to 1.2 blocks a second, grows, holds and fades over
 * 10 to 22 minutes, 700 to 1800 blocks across. Under a cell it pours, the sky closes, the wind rises and the lightning strikes; a few hundred blocks away it is dry and calm.
 */
public final class StormCells {
    private StormCells() {}
    public static final int LATTICE = 1600; public static final long EPOCH = 30_000L;
    /** A cell as it is at one moment. {@code level} is its strength at the middle (the envelope in time already in), {@code radius} where it has faded to nothing. */
    public record Cell(double x, double z, float radius, float level, double vx, double vz) {}

    /** For tests: a standing cell from the environment (ALTERRA_CELL="x,z,radius,level"), the same in every JVM that reads it. */
    private static final Cell FIXED = fixed();
    private static Cell fixed() {
        String s = System.getenv("ALTERRA_CELL"); if (s == null || s.isBlank()) return null;
        try { String[] p = s.split(","); return new Cell(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), 0, 0); }
        catch (RuntimeException e) { dev.phi.aethryon.Aethryon.LOG.error("ALTERRA_CELL must be x,z,radius,level; ignored", e); return null; }
    }

    private static double rnd(int i, int j, long e, int salt) {
        long h = i * 0x9E3779B97F4A7C15L ^ (j + 0x632BE59BD9B4E019L) * 0xC2B2AE3D27D4EB4FL ^ e * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The cells within {@code range} blocks of a point (their edge counted) at a game time, into {@code out}. Cells that are only starting or ending have a low level. */
    public static void near(long time, double x, double z, double range, List<Cell> out) {
        out.clear();
        if (FIXED != null && Math.hypot(x - FIXED.x(), z - FIXED.z()) < range + FIXED.radius()) out.add(FIXED);
        long e0 = Math.floorDiv(time, EPOCH);
        int ix = (int) Math.floor(x / LATTICE), iz = (int) Math.floor(z / LATTICE);
        for (long e = e0 - 1; e <= e0; e++) for (int i = ix - 2; i <= ix + 2; i++) for (int j = iz - 2; j <= iz + 2; j++) {
            if (rnd(i, j, e, 0) > 0.95) continue;   // this square has no cell this epoch
            long born = e * EPOCH + (long) (rnd(i, j, e, 1) * EPOCH), life = 12_000L + (long) (rnd(i, j, e, 2) * 14_000L), age = time - born;
            if (age < 0 || age > life) continue;
            float radius = (float) (350 + rnd(i, j, e, 3) * 550);
            double ang = (rnd(i, j, e, 6) - 0.5) * 2.1, speed = 0.03 + rnd(i, j, e, 7) * 0.03, vx = Math.cos(ang) * speed, vz = Math.sin(ang) * speed;
            double cx = (i + 0.5 + (rnd(i, j, e, 4) - 0.5) * 0.9) * LATTICE + vx * age, cz = (j + 0.5 + (rnd(i, j, e, 5) - 0.5) * 0.9) * LATTICE + vz * age;
            if (Math.hypot(x - cx, z - cz) > range + radius) continue;
            float up = Mth.clamp((float) age / (0.2f * life), 0f, 1f), down = Mth.clamp((float) (life - age) / (0.25f * life), 0f, 1f), env = (float) Mth.smoothstep(Math.min(up, down));
            out.add(new Cell(cx, cz, radius, (0.6f + (float) rnd(i, j, e, 8) * 0.4f) * env, vx, vz));
        }
    }

    /** How strongly a cell is over a point: its level in the middle, falling to nothing at its radius (a wide core, soft edge). */
    public static float at(Cell c, double x, double z) {
        double d = Math.hypot(x - c.x(), z - c.z()) / c.radius();
        return d >= 1.0 ? 0f : c.level() * (1f - (float) Mth.smoothstep((d - 0.3) / 0.7));
    }

    private static final ThreadLocal<java.util.ArrayList<Cell>> SCRATCH = ThreadLocal.withInitial(java.util.ArrayList::new);

    /** The storm strength over a point, 0 (none) to 1, the strongest cell there. */
    public static float level(long time, double x, double z) {
        var list = SCRATCH.get(); near(time, x, z, 0, list);
        float best = 0f;
        for (Cell c : list) best = Math.max(best, at(c, x, z));
        return best;
    }
}
