package dev.phi.alterra.illuminate.maps;

import dev.phi.prismlib.api.WaterAmounts;


import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.nio.FloatBuffer;

/**
 * Water that rises and falls smoothly: the server moves water an eighth of a block at a time, and each step would show as a jump. When a water block's level changes, the
 * difference is remembered per block column and the water shader draws the surface that much off its new height, easing to nothing over a third of a second. Steps that
 * follow each other overlap, so a pit filling or a pool draining looks like one continuous movement. (Client only; a small map around the camera.)
 */
public final class FlowMap {
    private FlowMap() {}
    public static final int SIZE = 64, UNIT = 9;
    private static final float TIME = 0.35f;
    private static int tex; public static volatile int originX, originZ; public static volatile boolean live;
    private static final Long2ObjectOpenHashMap<float[]> moves = new Long2ObjectOpenHashMap<>();   // column -> {offset when it started, start time}
    private static final Long2ObjectOpenHashMap<float[]> sim = new Long2ObjectOpenHashMap<>();     // block -> {amount before, amount now, when it arrived, settled (1)} from the server's simulation

    /** Precise amounts from the server (WaterAmounts): the surface glides from the last amount to this one over a tick. */
    public static synchronized void received(WaterAmounts w) {
        float t = now();
        for (int i = 0; i < w.pos().length; i++) {
            long k = w.pos()[i]; byte b = w.amount()[i];
            float[] e = sim.get(k);
            float shown = e == null ? -1f : shown(e, t);
            if (b < 0) {   // settled: glide to what the block shows, then drop it
                if (e != null) { e[0] = shown; e[1] = Float.NaN; e[2] = t; e[3] = 1f; }
                continue;
            }
            float a = (b & 0xff) / 120f;
            if (e == null) sim.put(k, new float[] {a, a, t, 0f});
            else { e[0] = shown < 0f ? a : shown; e[1] = a; e[2] = t; e[3] = 0f; }
        }
    }
    private static float shown(float[] e, float t) { float k = Math.min(1f, (t - e[2]) / 0.055f); float to = Float.isNaN(e[1]) ? e[0] : e[1]; return e[0] + (to - e[0]) * k; }
    private static final FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(SIZE * SIZE);

    private static double surface(BlockState st, BlockPos p) {
        FluidState fs = st.getFluidState();
        return fs.is(net.minecraft.tags.FluidTags.WATER) ? p.getY() + fs.getOwnHeight() : p.getY();
    }
    private static float now() { return (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f; }
    private static float offset(float[] m, float t) { float k = Math.max(0f, Math.min(1f, (t - m[1]) / TIME)); return m[0] * (1f - k * k * (3f - 2f * k)); }

    /** A block changed on the client: if water rose or fell there, start easing its surface from where it was. */
    public static synchronized void changed(BlockPos p, BlockState before, BlockState after) {
        if (sim.isEmpty()) return;   // easing a column alone opens gaps between it and its neighbours: it is only done while the simulation (whose steps it smooths) is feeding exact heights
        if (sim.containsKey(p.asLong())) return;   // the simulation's precise heights already cover it
        boolean wb = before.getFluidState().is(net.minecraft.tags.FluidTags.WATER), wa = after.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
        if (!wb && !wa) return;
        float d = (float) (surface(before, p) - surface(after, p));
        if (Math.abs(d) < 0.01f || Math.abs(d) > 1.01f) return;
        long key = (((long) p.getX()) << 32) ^ (p.getZ() & 0xffffffffL); float t = now();
        float[] m = moves.get(key);
        float left = m == null ? 0f : offset(m, t);
        moves.put(key, new float[] {Math.max(-1.5f, Math.min(1.5f, left + d)), t});
    }

    // Water under a solid ceiling: a full block of water draws its top at 8/9 and a strip of air shows under the ceiling. Where that water joins other water its top is lifted to the ceiling
    // (a lone block of water under a ceiling stays as it is). Found one chunk per tick, only in sections that hold water.
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.longs.LongArrayList> ceilByChunk = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static int scanCursor;
    public static synchronized void scanCeilings(net.minecraft.client.multiplayer.ClientLevel level, int camX, int camZ) {
        final int R = 3, side = 2 * R + 1, ccx = camX >> 4, ccz = camZ >> 4;
        int i = scanCursor++ % (side * side);
        if (i == 0) ceilByChunk.keySet().removeIf(k -> Math.abs(net.minecraft.world.level.ChunkPos.getX(k) - ccx) > R || Math.abs(net.minecraft.world.level.ChunkPos.getZ(k) - ccz) > R);
        int chx = ccx - R + i % side, chz = ccz - R + i / side;
        long ck = net.minecraft.world.level.ChunkPos.asLong(chx, chz);
        var chunk = level.getChunkSource().getChunkNow(chx, chz);
        if (chunk == null) { ceilByChunk.remove(ck); return; }
        var found = new it.unimi.dsi.fastutil.longs.LongArrayList();
        var p = new BlockPos.MutableBlockPos(); var q = new BlockPos.MutableBlockPos();
        var secs = chunk.getSections();
        for (int si = 0; si < secs.length; si++) {
            var sec = secs[si];
            if (sec.hasOnlyAir() || !sec.maybeHas(s -> s.getFluidState().is(net.minecraft.tags.FluidTags.WATER))) continue;
            int y0 = (level.getMinSection() + si) << 4;
            for (int ly = 0; ly < 16; ly++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                FluidState fs = sec.getBlockState(lx, ly, lz).getFluidState();
                if (!fs.is(net.minecraft.tags.FluidTags.WATER) || fs.getOwnHeight() < 0.88f) continue;
                p.set((chx << 4) + lx, y0 + ly, (chz << 4) + lz);
                BlockState up = level.getBlockState(q.set(p).move(0, 1, 0));
                if (!up.getFluidState().isEmpty() || !up.isFaceSturdy(level, q, net.minecraft.core.Direction.DOWN)) continue;
                boolean joined = false;
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) if (level.getFluidState(q.set(p).move(d)).is(net.minecraft.tags.FluidTags.WATER)) { joined = true; break; }
                if (joined) found.add((((long) p.getX()) << 32) ^ (p.getZ() & 0xffffffffL));
            }
        }
        if (found.isEmpty()) ceilByChunk.remove(ck); else ceilByChunk.put(ck, found);
    }

    public static int texture() { return tex; }

    /** Every frame: the offsets around the camera into the map. */
    public static synchronized void upload(double camX, double camZ) {
        if (moves.isEmpty() && sim.isEmpty() && ceilByChunk.isEmpty() && !live) return;
        float t = now();
        moves.values().removeIf(m -> t - m[1] > TIME);
        int ox = (int) Math.floor(camX) - SIZE / 2, oz = (int) Math.floor(camZ) - SIZE / 2;
        buf.clear();
        for (int i = 0; i < SIZE * SIZE; i++) buf.put(0f);
        boolean any = false;
        // the simulation: the top water spot of each column, drawn at its precise height instead of the block's eighth
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null && !sim.isEmpty()) {
            it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap top = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(); top.defaultReturnValue(Integer.MIN_VALUE);
            java.util.List<Long> done = new java.util.ArrayList<>();
            for (var e : sim.long2ObjectEntrySet()) {
                long k = e.getLongKey(); float[] v = e.getValue();
                if (v[3] > 0f && t - v[2] > 0.25f) { done.add(k); continue; }
                int x = BlockPos.getX(k) - ox, z = BlockPos.getZ(k) - oz;
                if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) continue;
                long col = (((long) x) << 32) | z; int y = BlockPos.getY(k);
                if (y > top.get(col)) top.put(col, y);
            }
            for (long k : done) sim.remove(k);
            var bp = new BlockPos.MutableBlockPos();
            for (var c : top.long2IntEntrySet()) {
                int x = (int) (c.getLongKey() >> 32), z = (int) c.getLongKey(), y = c.getIntValue();
                bp.set(ox + x, y, oz + z);
                float[] v = sim.get(bp.asLong()); if (v == null) continue;
                FluidState fs = level.getFluidState(bp);
                if (!fs.is(net.minecraft.tags.FluidTags.WATER) || level.getFluidState(bp.above()).is(net.minecraft.tags.FluidTags.WATER)) continue;   // nothing drawn here, or not the top
                float settledTo = fs.getOwnHeight() * 9f / 8f;
                if (v[3] > 0f) v[1] = Float.NaN;
                float a = v[3] > 0f ? v[0] + (settledTo - v[0]) * Math.min(1f, (t - v[2]) / 0.25f) : shown(v, t);
                float off = (a - settledTo) * 8f / 9f; if (Float.isFinite(off)) { buf.put(x + z * SIZE, Math.max(-1f, Math.min(1f, off))); any = true; }   // how far the drawn surface is from the precise one
            }
        }
        for (var e : moves.long2ObjectEntrySet()) {
            int x = (int) (e.getLongKey() >> 32) - ox, z = (int) e.getLongKey() - oz;
            if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) continue;
            if (buf.get(x + z * SIZE) == 0f) { buf.put(x + z * SIZE, offset(e.getValue(), t)); any = true; }   // the simulation's own height wins where it has one
        }
        for (var lst : ceilByChunk.values()) for (int j = 0; j < lst.size(); j++) {   // the strip of air under a ceiling is closed
            long k = lst.getLong(j); int x = (int) (k >> 32) - ox, z = (int) k - oz;
            if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) continue;
            if (buf.get(x + z * SIZE) == 0f) { buf.put(x + z * SIZE, 0.107f); any = true; }   // a hair under the ceiling, not exactly at it (the same height fights the ceiling's face)
        }
        for (var lst : ceilByChunk.values()) for (int j = 0; j < lst.size(); j++) {   // and a little way past its edge: the heights are smoothed across the corners between columns, so at the edge of the ceiling a corner would get only half the lift and leave a hairline of air
            long k = lst.getLong(j); int x = (int) (k >> 32) - ox, z = (int) k - oz;
            for (int d = 0; d < 4; d++) {
                int xx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), zz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (xx < 0 || zz < 0 || xx >= SIZE || zz >= SIZE) continue;
                if (buf.get(xx + zz * SIZE) == 0f) buf.put(xx + zz * SIZE, 0.07f);
            }
        }
        buf.rewind();
        boolean fresh = tex == 0;
        if (fresh) tex = GL11C.glGenTextures();
        GlUpload.bind(GL13C.GL_TEXTURE0 + UNIT, tex);
        GlUpload.reset();
        if (fresh) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_R16F, SIZE, SIZE, 0, GL11C.GL_RED, GL11C.GL_FLOAT, buf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11C.GL_RED, GL11C.GL_FLOAT, buf);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        originX = ox; originZ = oz; live = any;
    }
}
