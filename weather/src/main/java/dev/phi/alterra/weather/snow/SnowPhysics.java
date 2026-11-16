package dev.phi.alterra.weather.snow;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.config.HeavyConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Snow as a loose pile with sleeping columns. Anything that changes the snow in a column (a track, the wind, an avalanche, a layer that moved) wakes that column and its four neighbours.
 * Every second tick a budget of awake columns near the players is relaxed: a layer moves to the lowest neighbour when the surface drops by more than the angle of repose
 * (8 eighths of a block per block, 45 degrees: snow is not a liquid, it holds the walls of a track and only a steep heap slumps). A column that cannot move goes back to sleep; one that moved wakes its neighbours,
 * so a heap collapses outwards like a flow and then rests. Nothing is scanned that has not changed.
 * Piles level out, drifts smooth, and snow is left lying
 * where it was heaped; a steep slope holds more than it should, which is what {@link Avalanche} lets go of.
 * It only happens on about level ground (drifts even out) and where deep snow hangs over a one-block step (it slumps off); snow on steeper ground holds, as it does in life,
 * until something sets it off. Heights are counted in eighths of a block: the top of the snow in a column, or the ground where there is none.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class SnowPhysics {
    private SnowPhysics() {}
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int REPOSE = 8, LEDGE = 16, BUDGET = 24;   // eighths: slides above REPOSE, but not off a drop of more than a block and a half (that is a cliff, not a slope)

    /** What the top of a column is: the position of its topmost snow (a layer or a block), or null where there is none. */
    static BlockPos snowAt(ServerLevel l, int x, int z, int top) {
        BlockPos p = new BlockPos(x, top, z);
        if (l.getBlockState(p).is(Blocks.SNOW)) return p;   // a single layer does not block motion, so it sits in the first air
        BlockPos b = p.below(); BlockState st = l.getBlockState(b);
        return st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) ? b : null;
    }

    /** The surface of a column in eighths of a block (snow included). */
    static int units(ServerLevel l, int x, int z, int top) {
        BlockPos s = snowAt(l, x, z, top);
        if (s == null) return top * 8;
        BlockState st = l.getBlockState(s);
        return s.getY() * 8 + (st.is(Blocks.SNOW) ? st.getValue(SnowLayerBlock.LAYERS) : 8);
    }

    private static final java.util.Map<net.minecraft.world.entity.LivingEntity, Float> STEPPED = new java.util.WeakHashMap<>();

    /** Tracks: whatever walks across layers of snow presses them down, a layer a step (never below one layer; blocks of snow are not dented). Players and mobs near players. */
    private static void tracks(ServerLevel l) {
        for (ServerPlayer pl : l.players()) {
            if (pl.isSpectator()) continue;
            for (var le : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, pl.getBoundingBox().inflate(24, 8, 24), x -> x.onGround() && !x.isSpectator() && !x.isInWater() && !x.isPassenger() && !(x instanceof net.minecraft.world.entity.player.Player pp && pp.getAbilities().flying))) {
                float walked = le.walkDist, was = STEPPED.getOrDefault(le, walked);
                if (Math.abs(walked - was) > 8f) { STEPPED.put(le, walked); continue; }
                if (Math.abs(walked - was) < 0.55f) continue;
                STEPPED.put(le, walked);
                float mass = Math.min(1f, le.getBbWidth() * le.getBbHeight() * 0.18f);   // a person 0.3, a cow 0.6, a horse about 0.7
                dev.phi.prismlib.api.PrismApi.disturb(l, new dev.phi.prismlib.api.Disturbance(dev.phi.prismlib.api.Disturbance.Kind.STEP, le.getX(), le.getY(), le.getZ(), Math.max(0.3f, le.getBbWidth() * 0.5f), mass, le.getDeltaMovement().x, 0, le.getDeltaMovement().z));
            }
        }
        if (STEPPED.size() > 600) STEPPED.clear();
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel t && HeavyConfig.get(HeavyConfig.SNOW_TRACKS) && t.getGameTime() % 2 == 0) tracks(t);
        if (!(e.getLevel() instanceof ServerLevel l) || !HeavyConfig.get(HeavyConfig.SNOW_SETTLES)) return;
        if (l.getGameTime() % 5 != 0) return;   // a slump is slow and rare, never a visible flow
        var awake = AWAKE.get(l); if (awake == null || awake.isEmpty()) return;
        for (int n = 0; n < BUDGET && !awake.isEmpty(); n++) {
            long k = awake.removeFirstLong(); int x = (int) k, z = (int) (k >> 32);
            if (!near(l, x, z) || !l.hasChunkAt(new BlockPos(x, 0, z))) continue;   // too far from anyone: it sleeps until something wakes it again
            relax(l, x, z);
        }
    }

    private static final java.util.Map<ServerLevel, it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet> AWAKE = new java.util.WeakHashMap<>();
    private static final int MAX_AWAKE = 40_000;

    /** Wake a column (and let it settle again if nothing is wrong with it). */
    public static void wake(ServerLevel l, int x, int z) {
        var s = AWAKE.computeIfAbsent(l, k -> new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet());
        if (s.size() >= MAX_AWAKE) s.removeFirstLong();
        s.add((x & 0xFFFFFFFFL) | ((long) z << 32));
    }
    /** A column and its four neighbours: what a change in one column can disturb. */
    static void wakeAround(ServerLevel l, int x, int z) { wake(l, x, z); wake(l, x + 1, z); wake(l, x - 1, z); wake(l, x, z + 1); wake(l, x, z - 1); }

    private static boolean near(ServerLevel l, int x, int z) {
        for (ServerPlayer pl : l.players()) { double dx = pl.getX() - x, dz = pl.getZ() - z; if (dx * dx + dz * dz < 112 * 112) return true; }
        return false;
    }

    /** One column: if its snow stands more than the repose angle above a neighbour (the steepest one), a layer moves there; true if it did. */
    static boolean relax(ServerLevel l, int x, int z) {
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos src = snowAt(l, x, z, top);
        if (src == null) return false;
        int here = units(l, x, z, top), srcGround = src.getY() * 8, depth = here - srcGround, best = 0, bx = 0, bz = 0, btop = 0;
        for (Direction d : SIDES) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (!l.hasChunkAt(new BlockPos(nx, top, nz))) continue;
            int ntop = l.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz), diff = here - units(l, nx, nz, ntop);
            BlockPos ns = snowAt(l, nx, nz, ntop); int step = srcGround - (ns != null ? ns.getY() * 8 : ntop * 8);   // how far the ground itself drops to that neighbour
            boolean flat = Math.abs(step) <= 2, slump = step > 2 && step <= 8 && depth >= 6;   // level ground: drifts even out; one step down: only deep snow slumps off the edge; anything steeper holds (until an avalanche)
            if ((flat || slump) && diff > REPOSE && diff <= LEDGE && diff > best) { best = diff; bx = nx; bz = nz; btop = ntop; }
        }
        if (best == 0) return false;   // at rest: it sleeps
        return give(l, bx, bz, btop) && take(l, src);   // (added first: if there is no room, nothing is taken)
    }

    /** Take one eighth off a pile (false if it cannot be). */
    static boolean take(ServerLevel l, BlockPos p) {
        BlockState st = l.getBlockState(p);
        if (st.is(Blocks.SNOW_BLOCK)) { l.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7), 3); wakeAround(l, p.getX(), p.getZ()); return true; }
        if (!st.is(Blocks.SNOW)) return false;
        int n = st.getValue(SnowLayerBlock.LAYERS);
        l.setBlock(p, n <= 1 ? Blocks.AIR.defaultBlockState() : st.setValue(SnowLayerBlock.LAYERS, n - 1), 3);
        wakeAround(l, p.getX(), p.getZ());
        return true;
    }

    /** Add one eighth to a column (false if there is no room). */
    static boolean give(ServerLevel l, int x, int z, int top) {
        BlockPos s = snowAt(l, x, z, top);
        if (s != null) {
            BlockState st = l.getBlockState(s);
            if (st.is(Blocks.SNOW) && st.getValue(SnowLayerBlock.LAYERS) < 8) {
                int n = st.getValue(SnowLayerBlock.LAYERS) + 1;
                l.setBlock(s, n >= 8 ? Blocks.SNOW_BLOCK.defaultBlockState() : st.setValue(SnowLayerBlock.LAYERS, n), 3);
                wakeAround(l, x, z);
                return true;
            }
            top = s.getY() + 1;   // a full block: start a new layer on top
        }
        BlockPos p = new BlockPos(x, top, z); BlockState at = l.getBlockState(p);
        if (!(at.isAir() || at.canBeReplaced()) || !at.getFluidState().isEmpty()) return false;
        BlockState put = Blocks.SNOW.defaultBlockState();
        if (!put.canSurvive(l, p)) return false;
        l.setBlock(p, put, 3);
        wakeAround(l, x, z);
        return true;
    }
}
