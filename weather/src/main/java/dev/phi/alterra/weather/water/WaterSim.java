package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.net.WaterSync;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Water simulated as a real, continuous amount: every water spot that is moving holds a precise amount (0 to 1 block) and a flow towards each neighbour that keeps its
 * momentum from tick to tick (a pipe model), so water sloshes, levels out as one surface, pours down, and is pushed up by the pressure of the water around it.
 * Spots that have settled go quiet and cost nothing; anything that disturbs water (a block broken, water poured, rain) wakes it.
 * <p>
 * The blocks are a rough copy, rewritten only when a spot's amount crosses an eighth: swimming, saving and players without this addon use them. Players with it are sent
 * the precise amounts around them (WaterSync), and the water shader draws the true surface, so draining and filling move smoothly.
 * The sea and rivers (and springs, and the coast while a surge holds it) are endless; drains swallow water (FiniteWater has those rules).
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class WaterSim {
    private WaterSim() {}
    private static final float G = 0.6f, DAMP = 0.94f, FALL = 1.0f, QUIET = 0.0015f, MIN = 0.004f;
    private static final int BUDGET = 24000, QUIET_TICKS = 30, SYNC_RADIUS = 56;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final class Sim {
        final Long2FloatOpenHashMap amount = new Long2FloatOpenHashMap();   // the precise amount of every awake spot
        final Map<Long, float[]> flow = new HashMap<>();                     // its flow to N, E, S, W (blocks per tick), kept from tick to tick
        final Long2IntOpenHashMap calm = new Long2IntOpenHashMap();          // ticks it has hardly changed
        final LongOpenHashSet wake = new LongOpenHashSet();                  // spots to add before the next step
        final Long2FloatOpenHashMap sent = new Long2FloatOpenHashMap();      // what the players were last told
        final LongOpenHashSet changed = new LongOpenHashSet();               // spots whose amount moved this tick (for the players)
    }
    private static final Map<ResourceKey<Level>, Sim> SIMS = new HashMap<>();
    private static Sim sim(ServerLevel l) { return SIMS.computeIfAbsent(l.dimension(), k -> new Sim()); }

    /** Something happened at or next to this spot: if there is water there (or it can take water from a neighbour), it takes part again. */
    public static void wake(Level l, BlockPos p) {
        if (!(l instanceof ServerLevel s)) return;
        Sim m = sim(s); m.wake.add(p.asLong());
        for (Direction d : Direction.values()) m.wake.add(p.relative(d).asLong());
    }

    /** Add (or with a negative amount take) water at a spot, in blocks; it spreads from there. */
    public static void add(ServerLevel l, BlockPos p, float blocks) {
        Sim m = sim(l); long k = p.asLong();
        if (!m.amount.containsKey(k)) m.amount.put(k, held(l, p));
        m.amount.put(k, Math.max(0f, m.amount.get(k) + blocks)); m.calm.remove(k);
        wake(l, p);
    }

    /** How much water a spot holds in the world right now (from its block). */
    public static float held(Level l, BlockPos p) { return FiniteWater.parts(l.getFluidState(p)) / 8f; }

    /** Can the water here go anywhere: it is not full, or something beside or below it can take water. */
    private static boolean canMove(ServerLevel l, Sim m, BlockPos p) {
        if (held(l, p) < 0.999f) return true;
        BlockPos b = p.below();
        if (takes(l, b) && amountOf(l, m, b.asLong(), b) < 0.999f) return true;
        for (Direction d : SIDES) { BlockPos n = p.relative(d); if (takes(l, n) && amountOf(l, m, n.asLong(), n) < 0.999f) return true; }
        return false;
    }

    private static boolean takes(ServerLevel l, BlockPos p) { return l.isLoaded(p) && FiniteWater.holds(l.getBlockState(p)) && !l.getBlockState(p).is(WaterBlocks.DRAIN.get()) && !dev.phi.alterra.weather.disaster.Vortex.carved(p); }   // (not into a whirlpool's funnel)

    private static float amountOf(ServerLevel l, Sim m, long k, BlockPos p) { return m.amount.containsKey(k) ? m.amount.get(k) : held(l, p); }

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) { SIMS.clear(); }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !FiniteWater.on()) return;
        Sim m = SIMS.get(l.dimension());
        if (m == null || (m.amount.isEmpty() && m.wake.isEmpty())) return;
        // wake: spots with water, or empty spots that can take water from a wet neighbour
        var p = new BlockPos.MutableBlockPos();
        for (long k : m.wake) {
            p.set(k);
            if (m.amount.containsKey(k)) {   // already moving: if the block was changed from outside (a bucket, a block put there, a sponge), believe the block
                if (!l.isLoaded(p)) continue;   // (the sim's own writes schedule fluid ticks that land here: those do not keep it from settling)
                BlockState st = l.getBlockState(p);
                if (st.is(Blocks.WATER) && !st.getFluidState().isSource() && st.getFluidState().getValue(net.minecraft.world.level.material.FlowingFluid.FALLING)) continue;   // a falling column is how the sim draws pouring water: its block level says nothing (believing it put water back into every thin trickle)
                int blockParts = st.is(Blocks.WATER) ? FiniteWater.parts(st.getFluidState()) : 0, simParts = Math.round(m.amount.get(k) * 8f);
                if (!takes(l, p) && !st.is(Blocks.WATER)) { m.amount.remove(k); m.flow.remove(k); m.calm.remove(k); }
                else if (blockParts != simParts && Math.abs(blockParts - simParts) > 1) { m.amount.put(k, blockParts / 8f); m.calm.remove(k); }
                continue;
            }
            if (!l.isLoaded(p)) continue;
            BlockState st = l.getBlockState(p);
            if (st.is(Blocks.WATER)) { if (canMove(l, m, p)) m.amount.put(k, held(l, p)); }   // settled water (full, nothing open around it) stays asleep: the world's lakes do not all start moving when their chunks load
            else if (takes(l, p)) {
                boolean wetNear = false;
                for (Direction d : Direction.values()) if (d != Direction.DOWN && l.getFluidState(p.relative(d)).is(FluidTags.WATER) && l.getBlockState(p.relative(d)).is(Blocks.WATER)) { wetNear = true; break; }
                if (wetNear) m.amount.put(k, 0f);
            }
        }
        m.wake.clear();
        step(l, m);
        sync(l, m);
    }

    /** The surface a spot's water stands at: the top of the water above it if it is full and has water on top (the same body), else its floor plus its amount. */
    private static float head(ServerLevel l, Sim m, BlockPos p, float a) {
        if (a < 0.999f) return p.getY() + a;
        var q = new BlockPos.MutableBlockPos(p.getX(), p.getY() + 1, p.getZ());
        for (int i = 0; i < 24; i++, q.move(0, 1, 0)) {
            long k = q.asLong(); float b = amountOf(l, m, k, q);
            if (b < 0.999f) return q.getY() + b;
        }
        return q.getY();
    }

    private static final boolean DEBUG = System.getProperty("alterra.waterdebug") != null;
    private static float lostDown, lostSea, lostDrain, upPushed;

    private static void step(ServerLevel l, Sim m) {
        if (DEBUG && l.getGameTime() % 40 == 0) {
            double tot = 0; float lo = 9f; int low = 0;
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = x0, y1 = x1, z0 = x0, z1 = x1; java.util.TreeMap<Integer, Integer> ys = new java.util.TreeMap<>(); int fresh = 0;
            for (var en : m.amount.long2FloatEntrySet()) {
                tot += en.getFloatValue(); lo = Math.min(lo, en.getFloatValue()); if (en.getFloatValue() < 0.99f) low++;
                long k = en.getLongKey(); int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); z0 = Math.min(z0, z); z1 = Math.max(z1, z); ys.merge(y, 1, Integer::sum);
                if (m.calm.get(k) == 0) fresh++;
            }
            if (!m.amount.isEmpty()) org.slf4j.LoggerFactory.getLogger("WaterSim").info("[water] box x {}..{} y {}..{} z {}..{} by y {} moving {}", x0, x1, y0, y1, z0, z1, ys, fresh);
            org.slf4j.LoggerFactory.getLogger("WaterSim").info("[water] awake {} total {} lowest {} under-full {} | lost: sea {} drain {} | pushed up {}", m.amount.size(), String.format("%.3f", tot), lo, low, lostSea, lostDrain, upPushed);
        }
        LongArrayList keys = new LongArrayList(m.amount.keySet());
        if (keys.size() > BUDGET) keys.size(BUDGET);   // more than this moving at once: the rest waits a tick
        Long2FloatOpenHashMap delta = new Long2FloatOpenHashMap();
        Long2FloatOpenHashMap incoming = new Long2FloatOpenHashMap();   // what each spot is already getting this step: nothing is given more than it has room for
        Long2FloatOpenHashMap heads = new Long2FloatOpenHashMap();
        var p = new BlockPos.MutableBlockPos(); var n = new BlockPos.MutableBlockPos();
        for (long k : keys) { p.set(k); heads.put(k, head(l, m, p, m.amount.get(k))); }
        it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap riverTop = new it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap();
        java.util.function.LongPredicate river = key -> {   // is this spot river water (in a channel, under the river's surface)? A river at its level is in balance with itself: it does not move within itself
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key); long col = ((long) x << 32) ^ (z & 0xffffffffL);
            float s = riverTop.containsKey(col) ? riverTop.get(col) : dev.phi.alterra.weather.compat.AlterraRivers.surface(l, x, z);
            riverTop.put(col, s); return !Float.isNaN(s) && y < s;
        };
        for (long k : keys) {
            p.set(k);
            boolean endless = FiniteWater.endless(l, p);
            float a = endless ? 1f : m.amount.get(k);
            if (a <= 0f && !endless) continue;
            float out = 0f;
            // down first: water falls into whatever room there is below
            boolean inRiver = river.test(k);
            n.set(p.getX(), p.getY() - 1, p.getZ()); long kd = n.asLong(); float down = 0f;
            if (takes(l, n) && !(inRiver && river.test(kd))) {
                boolean sea = FiniteWater.endless(l, n);
                float below = sea ? 1f : amountOf(l, m, kd, n);
                down = sea ? (endless ? 0f : Math.min(a, 0.05f)) : Math.max(0f, Math.min(a, Math.min(1f - below - incoming.get(kd), FALL)));   // onto the sea it soaks in slowly
            } else if (l.getBlockState(n).is(WaterBlocks.DRAIN.get()) && !endless) down = Math.min(a, 0.25f);   // a drain under it
            // sideways: a flow that keeps its momentum and grows with the difference in surface height
            float[] f = m.flow.computeIfAbsent(k, x -> new float[4]);
            float h = heads.get(k);
            for (int i = 0; i < 4; i++) {
                n.set(p).move(SIDES[i]);
                if (l.getBlockState(n).is(WaterBlocks.DRAIN.get())) { f[i] = endless ? 0f : 0.12f; continue; }
                if (!takes(l, n)) { f[i] = 0f; continue; }
                long kn = n.asLong();
                if (inRiver && river.test(kn)) { f[i] = 0f; continue; }   // river to river: in balance, nothing moves (only water leaving the river, through a bank that was opened, does)
                float hn = heads.containsKey(kn) ? heads.get(kn) : n.getY() + amountOf(l, m, kn, n);
                float diff = h - hn;
                if (amountOf(l, m, kn, n) >= 0.999f) diff = 0f;   // a full block takes nothing more (water moves along the surface, it is never forced in)
                f[i] = diff <= 0.0005f ? 0f : Math.min(f[i] * DAMP + G * diff * 0.25f, diff * 0.5f);   // never against the slope, never more than evens it out (no overshoot, so no water pushed up into the air)
                if (!endless) f[i] = Math.max(0f, f[i] + dev.phi.alterra.weather.disaster.Vortex.flowBias(p, i));   // a whirlpool carries the water round it
            }
            for (int i = 0; i < 4; i++) {   // limit each flow to the room left in the spot it goes to (unless real pressure pushes it in)
                if (f[i] <= 0f) continue;
                n.set(p).move(SIDES[i]);
                if (l.getBlockState(n).is(WaterBlocks.DRAIN.get()) || FiniteWater.endless(l, n)) continue;
                long kn = n.asLong(); float an = amountOf(l, m, kn, n);
                f[i] = Math.min(f[i], Math.max(0f, 1f - an - incoming.get(kn)));
            }
            float sideTotal = f[0] + f[1] + f[2] + f[3];
            float avail = endless ? Float.MAX_VALUE : a - down;
            float scale = sideTotal > avail && sideTotal > 0f ? avail / sideTotal : 1f;
            for (int i = 0; i < 4; i++) f[i] *= scale;
            // what it gives
            if (down > 0f) { if (!FiniteWater.endless(l, n.set(p.getX(), p.getY() - 1, p.getZ())) && !l.getBlockState(n).is(WaterBlocks.DRAIN.get())) { delta.addTo(kd, down); incoming.addTo(kd, down); } else if (DEBUG) { if (l.getBlockState(n).is(WaterBlocks.DRAIN.get())) lostDrain += down; else lostSea += down; } out += down; }
            for (int i = 0; i < 4; i++) {
                if (f[i] <= 0f) continue;
                n.set(p).move(SIDES[i]);
                if (!l.getBlockState(n).is(WaterBlocks.DRAIN.get()) && !FiniteWater.endless(l, n)) { delta.addTo(n.asLong(), f[i]); incoming.addTo(n.asLong(), f[i]); } else if (DEBUG) lostSea += f[i];
                out += f[i];
            }
            if (!endless) delta.addTo(k, -out);
        }
        // apply: amounts change, anything over full is pushed up into the spot above (pressure), new spots join, and calm ones go quiet
        LongOpenHashSet wakeNext = new LongOpenHashSet();
        Long2FloatOpenHashMap up = new Long2FloatOpenHashMap();   // what is pushed up out of spots that came out over full (applied after, not while reading the changes)
        // Alterra's rivers are fed from upstream: water in a river channel below the river's surface comes back at a limited rate (take some out and it refills over about a second),
        // water above it flows away like any other. Finite, but a river keeps running.
        for (long k : keys) {
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k); long col = ((long) x << 32) ^ (z & 0xffffffffL);
            float s = riverTop.containsKey(col) ? riverTop.get(col) : dev.phi.alterra.weather.compat.AlterraRivers.surface(l, x, z);
            riverTop.put(col, s);
            if (Float.isNaN(s)) continue;
            float target = Math.max(0f, Math.min(1f, s - y)); if (target <= 0f) continue;
            float now = m.amount.get(k) + delta.get(k);
            if (now < target) delta.addTo(k, Math.min(0.06f, target - now));
        }
        for (var en : delta.long2FloatEntrySet()) {
            long k = en.getLongKey(); p.set(k);
            float before = amountOf(l, m, k, p), after = before + en.getFloatValue();
            if (after > 1f) { if (DEBUG) upPushed += after - 1f; after = 1f; }   // cannot happen with the room limits; counted if it ever does
            after = Math.max(0f, after);
            m.amount.put(k, after);
            if (Math.abs(after - before) > QUIET) {   // it moved: so may the water around it (a pond drains through, not only where it was cut open)
                m.calm.remove(k);
                if (Math.abs(after - before) > 0.01f) for (Direction d : Direction.values()) wakeNext.add(BlockPos.offset(k, d));
            } else m.calm.addTo(k, 1);
        }
        for (var en : up.long2FloatEntrySet()) {
            long k = en.getLongKey(); p.set(k);
            m.amount.put(k, Math.min(1f, amountOf(l, m, k, p) + en.getFloatValue())); m.calm.remove(k);
        }
        for (long k : keys) if (!delta.containsKey(k)) m.calm.addTo(k, 1);
        for (long k : wakeNext) if (!m.amount.containsKey(k)) m.wake.add(k);
        // write the rough copy into the blocks, and let the settled go
        LongArrayList sleep = new LongArrayList();
        for (var en : m.amount.long2FloatEntrySet()) {
            long k = en.getLongKey(); p.set(k); float a = en.getFloatValue();
            if (FiniteWater.endless(l, p)) { sleep.add(k); continue; }
            int parts = a < MIN ? 0 : Math.max(1, Math.min(8, Math.round(a * 8f)));
            BlockState st = l.getBlockState(p);
            n.set(p.getX(), p.getY() - 1, p.getZ());
            boolean falling = parts > 0 && m.calm.get(k) < QUIET_TICKS && takes(l, n) && amountOf(l, m, n.asLong(), n) < 0.98f;   // pouring down into room below
            if (st.is(Blocks.WATER) || st.isAir() || FiniteWater.holds(st)) {
                if (parts > 0 || st.is(Blocks.WATER)) FiniteWater.write(l, p, parts, falling);
            }
            if (Math.abs(a - m.sent.getOrDefault(k, -1f)) > 0.002f) m.changed.add(k);
            if (m.calm.get(k) >= QUIET_TICKS || a < MIN) sleep.add(k);
        }
        for (long k : sleep) {
            p.set(k); float a = m.amount.get(k);
            if (a < MIN && !FiniteWater.endless(l, p) && l.getBlockState(p).is(Blocks.WATER)) FiniteWater.write(l, p, 0);
            m.amount.remove(k); m.flow.remove(k); m.calm.remove(k); m.changed.add(k);
        }
    }

    /** Tell the players with this addon the precise amounts that changed near them. */
    private static void sync(ServerLevel l, Sim m) {
        if (m.changed.isEmpty()) return;
        for (ServerPlayer pl : l.players()) {
            if (!pl.connection.hasChannel(WaterSync.TYPE)) continue;
            LongArrayList ks = new LongArrayList(); it.unimi.dsi.fastutil.bytes.ByteArrayList vs = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
            for (long k : m.changed) {
                int x = BlockPos.getX(k), z = BlockPos.getZ(k);
                if (Math.abs(x - pl.getBlockX()) > SYNC_RADIUS || Math.abs(z - pl.getBlockZ()) > SYNC_RADIUS) continue;
                float a = m.amount.containsKey(k) ? m.amount.get(k) : -1f;   // -1: settled, use the block again
                ks.add(k); vs.add((byte) (a < 0f ? -1 : Math.round(Math.min(1f, a) * 120f)));
                if (ks.size() >= 4000) break;
            }
            if (!ks.isEmpty()) PacketDistributor.sendToPlayer(pl, new WaterSync(ks.toLongArray(), vs.toByteArray()));
        }
        for (long k : m.changed) { if (m.amount.containsKey(k)) m.sent.put(k, m.amount.get(k)); else m.sent.remove(k); }
        m.changed.clear();
    }
}
