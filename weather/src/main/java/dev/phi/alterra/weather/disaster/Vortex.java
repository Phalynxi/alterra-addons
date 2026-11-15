package dev.phi.alterra.weather.disaster;

import dev.phi.alterra.weather.water.FiniteWater;
import dev.phi.alterra.weather.water.WaterSim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A whirlpool, as a real force field on the water. The water turns round the centre the way a real vortex does (fastest at the edge of a core, slower the further out, nothing at the
 * rim), is drawn inwards and, in the core, down. It acts on everything afloat: swimmers, boats (which turn with it), items. It pushes the water itself too: finite water (ponds, lakes
 * and the like, anything the water sim handles) is carried round the swirl and drains away in the core; the open sea is endless, so there only the surface shows it (see the scene shaders).
 * Natural whirlpools and ones put down by a player or another mod are the same thing: an event of type VORTEX (see {@link Disasters#addVortex}).
 */
public final class Vortex {
    private Vortex() {}

    /** Tangential speed in blocks per tick at distance d from the centre of a vortex of radius R and strength s (0 to 1). A Rankine vortex: solid-body turning inside the core, 1/d outside, fading out at the rim. */
    public static double speed(double d, double R, float s) {
        double rc = R * 0.3, v = 0.30 * s, base = d < rc ? v * d / rc : v * rc / d;
        double t = Math.max(0.0, Math.min(1.0, (d - R) / (0.7 * R)));
        return base * (1.0 - t * t * (3.0 - 2.0 * t));
    }

    // what the water sim reads each tick: x, z, radius, strength, spin
    private static volatile double[][] ACTIVE = new double[0][];

    /** Called every tick with the events of a level: remember the whirlpools for the water sim. */
    static void refresh(List<Disasters.Event> events) {
        int n = 0; for (var e : events) if (e.type == Disasters.VORTEX) n++;
        double[][] a = new double[n][]; int i = 0;
        for (var e : events) if (e.type == Disasters.VORTEX) a[i++] = new double[] {e.x, e.z, e.radius, e.strength, e.spin};
        ACTIVE = a;
    }

    /** How much extra water the swirl carries from the cell at p towards side dir (0 north, 1 east, 2 south, 3 west), per tick, for the water sim. */
    public static float flowBias(BlockPos p, int dir) {
        var a = ACTIVE; if (a.length == 0) return 0f;
        double dx = dir == 1 ? 1 : dir == 3 ? -1 : 0, dz = dir == 2 ? 1 : dir == 0 ? -1 : 0, best = 0;
        for (double[] v : a) {
            double rx = p.getX() + 0.5 - v[0], rz = p.getZ() + 0.5 - v[1], d = Math.sqrt(rx * rx + rz * rz);
            if (d < 0.5 || d > v[2] * 1.7) continue;
            double sp = speed(d, v[2], (float) v[3]), tx = -rz / d * v[4], tz = rx / d * v[4];
            best += Math.max(0.0, tx * dx + tz * dz) * sp * 0.6 + Math.max(0.0, -(rx / d) * dx - (rz / d) * dz) * sp * 0.15;   // round, and a little inwards
        }
        return (float) Math.min(0.08, best);
    }

    /** One tick of one whirlpool on everything in and on the water around it. */
    static void apply(ServerLevel l, Disasters.Event e) {
        double reach = e.radius * 1.7, rc = e.radius * 0.3;
        AABB box = new AABB(e.x - reach, e.y - 6, e.z - reach, e.x + reach, e.y + 3, e.z + reach);
        for (Entity en : l.getEntities((Entity) null, box, x -> !x.isSpectator() && !x.isPassenger() && !(x instanceof Player p && p.getAbilities().flying) && (x.isInWater() || x instanceof Boat || x instanceof ItemEntity))) {
            double dx = en.getX() - e.x, dz = en.getZ() - e.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < 0.05 || d > reach) continue;
            double sp = speed(d, e.radius, e.strength), ux = dx / d, uz = dz / d, tx = -uz * e.spin, tz = ux * e.spin;
            double inward = sp * (0.25 + 0.5 * Math.max(0.0, 1.0 - d / (rc * 2.5)));
            double wantX = tx * sp - ux * inward, wantZ = tz * sp - uz * inward;
            Vec3 m = en.getDeltaMovement(); double k = en instanceof Boat ? 0.12 : 0.2;
            double my = m.y;
            if (d < rc * 1.2 && !(en instanceof Boat)) my -= 0.045 * e.strength * (1.0 - d / (rc * 1.2));   // the core pulls swimmers and items down
            en.setDeltaMovement(m.x + (wantX - m.x) * k, my, m.z + (wantZ - m.z) * k); en.hurtMarked = true;
            if (en instanceof Boat) en.setYRot(en.getYRot() + (float) (e.spin * sp * 60.0));   // a boat turns with the water
        }
        if (e.age % 4 == 0) {   // the core takes water out of finite water (the sea is endless and does not notice)
            int r = (int) Math.ceil(rc);
            var p = new BlockPos.MutableBlockPos();
            for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz > rc * rc) continue;
                p.set(Math.floor(e.x) + dx, e.y - 1, Math.floor(e.z) + dz);
                if (!l.isLoaded(p) || !l.getFluidState(p).is(FluidTags.WATER) || FiniteWater.endless(l, p)) continue;
                WaterSim.add(l, p, -0.05f * e.strength);
            }
        }
    }
}
