package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.config.HeavyConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Water as a real amount instead of vanilla's endless sources: every water block holds 1 to 8 parts (8 is a full block). It falls first (filling what is below), then spreads
 * sideways a part at a time to anything lower, until it is level; a lone part looks for a drop to fall down. Two sources never make a third, so water poured out runs off and
 * thins out, a pit fills up slowly, and a wide hollow only gets a shallow layer.
 * <p>
 * The sea and rivers stay endless (water connected straight up to the open surface below sea level): they always stay full, they fill what is dug next to them, and water that runs into them is gone into them. While a storm
 * surge stands high, the water it brought ashore (Flood) is endless too, so the coast floods; afterwards it runs back off into the sea.
 * <p>
 * Shallow water out in the open slowly dries up; water over something hot (magma, lava, fire, a campfire) steams, bubbles and boils away, and scalds whoever is in it.
 * It is all ordinary water blocks, so everyone sees it, with or without shaders. Switched by the server setting finiteWater.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class FiniteWater {
    private FiniteWater() {}

    public static boolean on() { return HeavyConfig.get(HeavyConfig.FINITE_WATER); }

    /** How many parts of water are at a place (0 to 8); a waterlogged block counts as full. */
    public static int parts(FluidState fs) { return fs.isEmpty() || !fs.is(FluidTags.WATER) ? 0 : fs.isSource() ? 8 : fs.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING) ? 4 : Math.min(8, fs.getAmount()); }   // a falling column is drawn full but holds about half

    /** The sea and rivers below sea level, and the coast while a surge holds it: endless water. */
    /** A spring: a full block of water set into rock (solid above it and on three sides or more), like the waterfalls in the mountains. It keeps running. */
    public static boolean spring(Level l, BlockPos p) {
        if (!l.getBlockState(p).is(Blocks.WATER) || !l.getFluidState(p).isSource() || !l.getBlockState(p.above()).isSolidRender(l, p.above())) return false;
        int walls = 0;
        for (Direction d : SIDES) if (l.getBlockState(p.relative(d)).isSolidRender(l, p.relative(d))) walls++;
        return walls >= 3;
    }

    public static boolean endless(Level l, BlockPos p) {
        if (spring(l, p)) return true;
        int sea = l.getSeaLevel();
        if (p.getY() < sea) {
            var b = l.getBiome(p);
            // the sea and rivers; and in any biome (coasts, island jungles, wetlands, the beach) water standing at sea level open to the sky: the water table holds it up there,
            // so it neither drains nor spreads out (finite, it crept over the coast lowlands and kept thousands of spots awake)
            if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_RIVER) || l.getFluidState(p).is(FluidTags.WATER)) {
                // only the sea itself: water all the way up to the open surface (not a cave under the sea floor, not a pool dug beside it)
                var q = new BlockPos.MutableBlockPos(p.getX(), p.getY() + 1, p.getZ());
                for (; q.getY() < sea; q.move(0, 1, 0)) if (!l.getFluidState(q).is(FluidTags.WATER)) return l instanceof ServerLevel s && Flood.surging(s, p);
                return l.getBlockState(q).isAir() || l.getFluidState(q).is(FluidTags.WATER);
            }
        }
        return l instanceof ServerLevel s && Flood.surging(s, p);
    }

    /** Can water go here: air, water, or something it washes away (grass, flowers). */
    public static boolean holds(BlockState st) { return st.isAir() || st.is(Blocks.WATER) || (st.canBeReplaced(Fluids.FLOWING_WATER) && !st.liquid() && st.getFluidState().isEmpty()); }

    private static BlockState block(int parts) { return parts >= 8 ? Blocks.WATER.defaultBlockState() : Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8 - parts); }

    /** Put this many parts of water at a place (0 takes the water away). Plants there are washed away (they drop). */
    public static void set(Level l, BlockPos p, int parts) {
        if (l instanceof ServerLevel s && on()) { WaterSim.add(s, p, parts / 8f - WaterSim.held(l, p)); return; }   // the simulation moves it from here
        BlockState cur = l.getBlockState(p);
        if (parts <= 0) { if (cur.is(Blocks.WATER)) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3); return; }
        if (!cur.isAir() && !cur.is(Blocks.WATER)) Block.dropResources(cur, l, p, cur.hasBlockEntity() ? l.getBlockEntity(p) : null);
        l.setBlock(p, block(parts), 3);
        l.scheduleTick(p, parts >= 8 ? Fluids.WATER : Fluids.FLOWING_WATER, 5);
    }

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** The simulation's rough copy into the block: this many eighths (0 clears it), without waking vanilla's own spreading. Plants there are washed away. */
    public static void write(Level l, BlockPos p, int parts) { write(l, p, parts, false); }
    /** falling: water that is pouring down, drawn as a full column (as vanilla draws falling water) so a stream does not break into floating pieces. */
    public static void write(Level l, BlockPos p, int parts, boolean falling) {
        BlockState cur = l.getBlockState(p);
        if (parts <= 0) { if (cur.is(Blocks.WATER)) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16); return; }
        BlockState want = falling ? Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8) : block(parts);
        if (cur == want) return;
        if (!cur.isAir() && !cur.is(Blocks.WATER)) Block.dropResources(cur, l, p, cur.hasBlockEntity() ? l.getBlockEntity(p) : null);
        l.setBlock(p, want, 2 | 16);
    }

    /** One step of a water block (called instead of vanilla's spreading, on the server). */
    public static void tick(Level l, BlockPos p, FluidState fs) {
        BlockState here = l.getBlockState(p);
        if (!here.is(Blocks.WATER)) return;   // waterlogged blocks hold their water and do not pour it out
        boolean endless = endless(l, p);
        int v = endless ? 8 : parts(fs), start = v;
        // 0. drains: water on a drain or against one runs away into it
        if (!endless) {
            if (l.getBlockState(p.below()).is(WaterBlocks.DRAIN.get())) v = Math.max(0, v - 2);
            for (Direction d : SIDES) if (v > 0 && l.getBlockState(p.relative(d)).is(WaterBlocks.DRAIN.get())) v--;
            if (v != start) { set(l, p, v); return; }
        }
        // 1. down: fill what is below as far as it goes
        BlockPos b = p.below(); BlockState bs = l.getBlockState(b);
        if (holds(bs)) {
            if (endless(l, b) && parts(l.getFluidState(b)) == 8) { if (!endless) v--; }   // resting on the sea: it soaks into it a part at a time (so a lot poured in can still pile up for a while)
            else {
                int vb = parts(l.getFluidState(b)), mv = Math.min(8 - vb, v);
                if (mv > 0) { set(l, b, vb + mv); if (!endless) v -= mv; }
            }
        }
        // 2. sideways: a part to each neighbour that is lower by two or more (or lower at all, if it can fall from there, or if water above presses down on this one)
        boolean pressed = l.getFluidState(p.above()).is(FluidTags.WATER);
        if (v > 0) {
            RandomSource r = l.random; int o = r.nextInt(4);
            for (int k = 0; k < 4 && v > 0; k++) {
                Direction d = SIDES[(o + k) % 4]; BlockPos n = p.relative(d); BlockState ns = l.getBlockState(n);
                if (!holds(ns)) continue;
                int vn = parts(l.getFluidState(n));
                boolean drop = vn == 0 && holds(l.getBlockState(n.below())) && parts(l.getFluidState(n.below())) < 8;
                if (endless(l, n) && vn == 8) { if (!endless && v > 0 && p.getY() >= l.getSeaLevel()) v--; continue; }   // running off into the sea
                if (v - vn >= 2 || (pressed && v - vn >= 1 && !l.getFluidState(n.above()).is(FluidTags.WATER)) || (drop && v >= 1 && (v > 1 || bs.isAir() == false))) {
                    set(l, n, vn + 1); if (!endless) v--;
                }
            }
        }
        if (!endless && v != start) set(l, p, v);
        else if (endless && start != 8) set(l, p, 8);
    }

    // ---------------------------------------------------------------- drying, boiling
    private static boolean hot(BlockState st) {
        return st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.LAVA) || st.is(BlockTags.FIRE) || (st.is(BlockTags.CAMPFIRES) && st.getOptionalValue(net.minecraft.world.level.block.CampfireBlock.LIT).orElse(false));
    }
    /** Is the water at this place heated: something hot right under it or beside it. */
    public static boolean heated(LevelAccessor l, BlockPos p) {
        if (hot(l.getBlockState(p.below()))) return true;
        for (Direction d : SIDES) if (hot(l.getBlockState(p.relative(d)))) return true;
        return false;
    }

    private static boolean icy(BlockState st) { return st.is(BlockTags.ICE) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW); }
    /** Is it freezing here: a biome cold enough for snow, or ice or snow right next to it. */
    public static boolean freezing(Level l, BlockPos p) {
        if (heated(l, p)) return false;
        if (l.getBiome(p).value().coldEnoughToSnow(p)) return true;
        for (Direction d : Direction.values()) if (icy(l.getBlockState(p.relative(d)))) return true;
        return false;
    }

    /** Now and then (a random tick): water in the cold turns to slush (shallow) or ice (a full block), shallow water in the open dries up, heated water boils away. */
    public static void randomTick(Level l, BlockPos p, FluidState fs, RandomSource r) {
        if (!(l instanceof ServerLevel s) || !l.getBlockState(p).is(Blocks.WATER) || endless(l, p)) return;
        int v = parts(fs);
        if (!l.getFluidState(p.above()).is(FluidTags.WATER) && freezing(l, p) && r.nextInt(2) == 0) {
            if (v >= 8) l.setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());   // ice melts back into a full block of water
            else l.setBlockAndUpdate(p, WaterBlocks.SLUSH.get().defaultBlockState().setValue(WaterBlocks.Slush.PARTS, v));
            return;
        }
        if (heated(l, p)) {
            s.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.02);
            l.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.6f);
            if (!l.getFluidState(p.above()).is(FluidTags.WATER)) set(l, p, v - 1);
            return;
        }
        if (v <= 2 && !l.isRainingAt(p.above()) && l.canSeeSky(p.above()) && r.nextInt(3) == 0) set(l, p, v - 1);
    }

    /** Client: heated water steams and bubbles at the top. */
    public static void animate(Level l, BlockPos p, FluidState fs, RandomSource r) {
        if (!heated(l, p) || l.getFluidState(p.above()).is(FluidTags.WATER)) return;
        double top = p.getY() + fs.getHeight(l, p);
        if (r.nextInt(2) == 0) l.addParticle(ParticleTypes.BUBBLE_POP, p.getX() + r.nextDouble(), top, p.getZ() + r.nextDouble(), 0, 0.02, 0);
        if (r.nextInt(3) == 0) l.addParticle(ParticleTypes.CLOUD, p.getX() + r.nextDouble(), top + 0.1, p.getZ() + r.nextDouble(), 0, 0.03, 0);
        if (r.nextInt(30) == 0) l.playLocalSound(p.getX() + 0.5, top, p.getZ() + 0.5, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.4f, 1.4f, false);
    }

    /** Boiling water scalds: a heart every second while you are in it. */
    @SubscribeEvent
    public static void scald(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof LivingEntity le) || le.level().isClientSide || le.tickCount % 20 != 0 || !le.isInWater() || !on()) return;
        BlockPos p = le.blockPosition();
        if (le.level().getFluidState(p).is(FluidTags.WATER) && heated(le.level(), p)) le.hurt(le.damageSources().hotFloor(), 2.0f);
    }

    /** Take a bucket's worth (8 parts) from the water connected to this place, nearest first; nothing is taken unless there is enough. */
    public static boolean takeBucket(LevelAccessor l, BlockPos start) {
        if (l instanceof Level lv && endless(lv, start)) return true;   // the sea gives a bucket and stays as it is
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>(); java.util.List<BlockPos> found = new java.util.ArrayList<>(); java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        q.add(start); seen.add(start); int total = 0;
        while (!q.isEmpty() && total < 8 && found.size() < 400) {   // through the water only, nearest first
            BlockPos c = q.poll();
            if (!l.getBlockState(c).is(Blocks.WATER)) continue;
            found.add(c); total += parts(l.getFluidState(c));
            for (Direction d : Direction.values()) { BlockPos n = c.relative(d); if (seen.add(n) && l.getBlockState(n).is(Blocks.WATER)) q.add(n); }
        }
        if (total < 4) return false;   // half a bucket's worth is enough (some of a spilled bucket dries up)
        int need = 8;
        for (BlockPos c : found) {
            int v = parts(l.getFluidState(c)), take = Math.min(v, need); need -= take;
            l.setBlock(c, v - take <= 0 ? Blocks.AIR.defaultBlockState() : block(v - take), 3);   // straight into the blocks, so the bucket gets it now
            if (l instanceof ServerLevel s) WaterSim.wake(s, c);
            if (need == 0) break;
        }
        return true;
    }

}
