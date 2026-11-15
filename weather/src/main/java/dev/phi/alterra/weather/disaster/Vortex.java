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

    // what the water sim reads each tick: x, z, radius, strength, spin, the height of the surface, how deep the funnel is at the core
    private static volatile double[][] ACTIVE = new double[0][];

    /** Called every tick with the events of a level: remember the whirlpools for the water sim. */
    static void refresh(List<Disasters.Event> events) {
        int n = 0; for (var e : events) if (e.type == Disasters.VORTEX) n++;
        double[][] a = new double[n][]; int i = 0;
        for (var e : events) if (e.type == Disasters.VORTEX) a[i++] = new double[] {e.x, e.z, e.radius, e.strength, e.spin, e.y, depth(e.radius, e.strength)};
        ACTIVE = a;
    }

    /** How deep the funnel is at its core, in blocks: up to seven, less for a small or a weak whirlpool. */
    public static double depth(double radius, double strength) { return Math.min(7.0, radius * 0.8) * strength; }

    /** The height the water surface is held at in a funnel at distance d from its centre (a paraboloid: steep in the core, flattening towards the rim). */
    static double target(double surface, double d, double radius, double depth) {
        double rf = radius * 0.9; if (d >= rf) return surface;
        double t = 1.0 - d / rf; return surface - depth * t * t;
    }

    /** Is this cell inside a funnel, above the held surface (so water must not be in it, and must not flow into it)? Asked by the water sim. */
    public static boolean carved(BlockPos p) {
        var a = ACTIVE; if (a.length == 0) return false;
        for (double[] v : a) {
            double rx = p.getX() + 0.5 - v[0], rz = p.getZ() + 0.5 - v[1], d2 = rx * rx + rz * rz, rf = v[2] * 0.9;
            if (d2 >= rf * rf || v[6] < 0.3) continue;
            if (p.getY() >= target(v[5] - 0.11, Math.sqrt(d2), v[2], v[6]) - 0.0001 && p.getY() <= v[5]) return true;
        }
        return false;
    }

    /** Dig the funnel: every column of the funnel is cleared of water above its held surface, and a cell the surface passes through keeps only the water that fits under it. */
    static void carve(ServerLevel l, Disasters.Event e) {
        double depth = depth(e.radius, e.strength); if (depth < 0.3) return;
        double rf = e.radius * 0.9, top = e.y - 0.11; int yTop = (int) Math.floor(e.y - 1);
        int r = (int) Math.ceil(rf); var p = new BlockPos.MutableBlockPos();
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            double cx = Math.floor(e.x) + dx + 0.5 - e.x, cz = Math.floor(e.z) + dz + 0.5 - e.z, d = Math.sqrt(cx * cx + cz * cz);
            if (d >= rf) continue;
            double target = target(top, d, e.radius, depth);
            for (int y = yTop; y >= yTop - (int) Math.ceil(depth) - 1; y--) {
                p.set(Math.floor(e.x) + dx, y, Math.floor(e.z) + dz);
                if (!l.isLoaded(p)) break;
                var fs = l.getFluidState(p); if (!fs.is(FluidTags.WATER)) continue;
                if (y >= target) { FiniteWater.write(l, p, 0); WaterSim.add(l, p, -1f); }   // air: the water here is gone (what is in the funnel is lost, as down a drain)
                else if (y + 1 > target) {   // the surface passes through this cell: it holds only what fits under it
                    int parts = (int) Math.round((target - y) * 8.0);
                    if (fs.getAmount() > parts) { FiniteWater.write(l, p, Math.max(0, parts)); WaterSim.add(l, p, (parts - fs.getAmount()) / 8f); }
                }
            }
        }
    }

    /** The whirlpool is over: wake the cells of the funnel so the surrounding water flows back in. */
    static void release(ServerLevel l, Disasters.Event e) {
        double rf = e.radius * 0.9; int r = (int) Math.ceil(rf), yTop = (int) Math.floor(e.y - 1), n = (int) Math.ceil(depth(e.radius, 1.0)) + 1; var p = new BlockPos.MutableBlockPos();
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            if (dx * dx + dz * dz > rf * rf) continue;
            for (int y = yTop; y >= yTop - n; y--) { p.set(Math.floor(e.x) + dx, y, Math.floor(e.z) + dz); if (l.isLoaded(p)) WaterSim.wake(l, p); }
        }
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
        double depth = depth(e.radius, e.strength);
        AABB box = new AABB(e.x - reach, e.y - depth - 3, e.z - reach, e.x + reach, e.y + 3, e.z + reach);
        for (Entity en : l.getEntities((Entity) null, box, x -> !x.isSpectator() && !x.isPassenger() && !(x instanceof Player p && p.getAbilities().flying) && (x.isInWater() || x instanceof Boat || x instanceof ItemEntity))) {
            double dx = en.getX() - e.x, dz = en.getZ() - e.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < 0.05 || d > reach) continue;
            double sp = speed(d, e.radius, e.strength), ux = dx / d, uz = dz / d, tx = -uz * e.spin, tz = ux * e.spin;
            double inward = sp * (0.25 + 0.5 * Math.max(0.0, 1.0 - d / (rc * 2.5)));
            double wantX = tx * sp - ux * inward, wantZ = tz * sp - uz * inward;
            if (en instanceof Boat b && b.getControllingPassenger() == null) {   // an empty boat has its own movement set to nothing every tick by the game: the water carries it, so it is moved here
                double dip = 1.2 * e.strength * Math.pow(Math.max(0.0, 1.0 - Math.min(1.0, d / (e.radius * 1.25))), 2);   // the surface dips into the core (the scene shader draws the same dip)
                double surface = e.y - 0.46 - dip;   // an empty boat has no float code running: it is held at the surface here
                Vec3 step = new Vec3(wantX * 0.6, (surface - b.getY()) * 0.3, wantZ * 0.6);
                b.move(net.minecraft.world.entity.MoverType.SELF, step); b.setYRot(b.getYRot() + (float) (e.spin * sp * 70.0));
                continue;
            }
            Vec3 m = en.getDeltaMovement(); double k = en instanceof Boat ? 0.14 : 0.2;   // a driven boat is steered by the player's own game, which gets the push in the motion packet
            double my = m.y;
            if (d < rc * 1.2 && !(en instanceof Boat)) my -= 0.045 * e.strength * (1.0 - d / (rc * 1.2));   // the core pulls swimmers and items down
            en.setDeltaMovement(m.x + (wantX - m.x) * k, my, m.z + (wantZ - m.z) * k); en.hurtMarked = true;
        }
        if (e.age % 2 == 0) carve(l, e);   // and the funnel itself goes down: real air in the water
    }
}
