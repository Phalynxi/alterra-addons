package dev.phi.alterra.weather.snow;

import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.aethryon.api.Disturbance;
import dev.phi.aethryon.api.DisturbanceListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * How snow answers a disturbance (see Aethryon's Disturbance): whatever sends one, from a foot to a rotor, works the same on the snow.
 * A step or a wheel presses the layers down (more with weight) and the snow it pushes aside is heaped up at the edges of the track, as in real snow; the wash of a rotor
 * lifts layers and carries them downwind; a blast shakes loose snow off steep slopes (Avalanche). Mass is kept: what is taken from one column is put somewhere else.
 */
public final class SnowDisturbance implements DisturbanceListener {
    @Override
    public void on(Level level, Disturbance d) {
        if (!(level instanceof ServerLevel l) || !HeavyConfig.get(HeavyConfig.SNOW_TRACKS)) return;
        switch (d.kind()) {
            case STEP, WHEEL, HULL -> press(l, d);
            case WASH -> blow(l, d);
            case BLAST -> Avalanche.release(l, BlockPos.containing(d.x(), d.y(), d.z()), Math.max(4, Math.round(d.radius())));
            default -> { }
        }
    }

    private static boolean snow(BlockState st) { return st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK); }

    /** The block of the snow in a column (the layer sits in the first air above the ground, a snow block is the top itself). */
    private static BlockPos top(ServerLevel l, int x, int z) {
        int h = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos p = new BlockPos(x, h, z);
        if (snow(l.getBlockState(p))) return p;
        BlockPos b = p.below();
        return snow(l.getBlockState(b)) ? b : null;
    }

    /** Press down every column the disturbance touches: deeper with energy, and the displaced snow goes to the sides of the track. */
    private static void press(ServerLevel l, Disturbance d) {
        int r = (int) Math.ceil(d.radius());
        double len = Math.sqrt(d.dx() * d.dx() + d.dz() * d.dz()), px = len > 1e-4 ? -d.dz() / len : 0, pz = len > 1e-4 ? d.dx() / len : 0;   // sideways to the way it moves
        int deep = 1 + (int) (d.energy() * 3.2f);   // layers pressed down at the centre: a foot one, a heavy wheel three or four
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            double dist = Math.sqrt(dx * dx + dz * dz); if (dist > d.radius() + 0.4) continue;
            int x = (int) Math.floor(d.x()) + dx, z = (int) Math.floor(d.z()) + dz;
            if (!l.hasChunkAt(new BlockPos(x, (int) d.y(), z))) continue;
            BlockPos p = top(l, x, z); if (p == null) continue;
            BlockState st = l.getBlockState(p);
            int n = st.is(Blocks.SNOW) ? st.getValue(SnowLayerBlock.LAYERS) : 8;
            int take = (int) Math.round(deep * (1.0 - 0.6 * dist / Math.max(0.5, d.radius() + 0.4)));   // shallower towards the edge of the track
            take = Math.min(take, n - 1); if (take <= 0) continue;   // never the last layer: the ground stays covered
            if (st.is(Blocks.SNOW_BLOCK) && d.energy() < 0.3f) continue;   // a person does not dent a block of packed snow, a vehicle does
            l.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, n - take), 3);
            // the snow it pushed aside piles up beside the track (half of it; the rest is packed into the track)
            int heap = take / 2 + (take > 1 ? 0 : (l.random.nextBoolean() ? 1 : 0));
            if (len > 1e-4 && heap > 0 && d.energy() >= 0.25f) {
                int side = l.random.nextBoolean() ? 1 : -1, sx = (int) Math.round(x + px * side * (d.radius() + 1)), sz = (int) Math.round(z + pz * side * (d.radius() + 1));
                for (int h = 0; h < heap; h++) SnowPhysics.give(l, sx, sz, l.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz));
            }
        }
    }

    /** The air of a rotor or a thruster lifts snow and carries it the way the air goes: a layer or two off the columns under it, put down further downwind. */
    private static void blow(ServerLevel l, Disturbance d) {
        int r = (int) Math.ceil(d.radius()); double len = Math.sqrt(d.dx() * d.dx() + d.dz() * d.dz());
        double ux = len > 1e-4 ? d.dx() / len : 0, uz = len > 1e-4 ? d.dz() / len : 0;
        int tries = 2 + (int) (d.energy() * 8);
        for (int i = 0; i < tries; i++) {
            double a = l.random.nextDouble() * Math.PI * 2, rr = Math.sqrt(l.random.nextDouble()) * d.radius();
            int x = (int) Math.floor(d.x() + Math.cos(a) * rr), z = (int) Math.floor(d.z() + Math.sin(a) * rr);
            if (!l.hasChunkAt(new BlockPos(x, (int) d.y(), z))) continue;
            BlockPos p = top(l, x, z); if (p == null || !l.getBlockState(p).is(Blocks.SNOW)) continue;   // blocks of snow stay: only loose layers fly
            int dist = 3 + l.random.nextInt(3 + r);
            double vx = ux, vz = uz;
            if (len <= 1e-4) { double ox = x + 0.5 - d.x(), oz = z + 0.5 - d.z(), ol = Math.sqrt(ox * ox + oz * oz); if (ol < 1e-3) continue; vx = ox / ol; vz = oz / ol; }   // no direction: away from the centre
            int tx = (int) Math.round(x + vx * dist), tz = (int) Math.round(z + vz * dist);
            if (SnowPhysics.give(l, tx, tz, l.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz)) && SnowPhysics.take(l, p)) continue;
        }
    }
}
