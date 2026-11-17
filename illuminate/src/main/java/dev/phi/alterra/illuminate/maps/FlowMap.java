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
    public static final int SIZE = 128, UNIT = 9;
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
    private static final FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(SIZE * SIZE * 2);   // r: how far the drawn surface is from the block's, g: 1 at a fall (the game draws it there)

    private static double surface(BlockState st, BlockPos p) {
        FluidState fs = st.getFluidState();
        return fs.is(net.minecraft.tags.FluidTags.WATER) ? p.getY() + fs.getOwnHeight() : p.getY();
    }
    private static float now() { return (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f; }
    private static float offset(float[] m, float t) { float k = Math.max(0f, Math.min(1f, (t - m[1]) / TIME)); return m[0] * (1f - k * k * (3f - 2f * k)); }

    /** A block changed on the client: if water rose or fell there, start easing its surface from where it was. */
    public static synchronized void changed(BlockPos p, BlockState before, BlockState after) {
        dirty.add((((long) p.getX()) << 32) ^ (p.getZ() & 0xffffffffL));
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

    public static int texture() { return tex; }

    // ------------------------------------------------------------------ the smooth surface: every water column around the camera
    private static final float[] shownH = new float[SIZE * SIZE];      // the surface drawn, eased towards the target every frame
    private static final long[] shownKey = new long[SIZE * SIZE];      // which world column a slot holds (the map is anchored to the world, wrapped)
    private static final float[] tgt = new float[SIZE * SIZE], blk = new float[SIZE * SIZE];   // this frame: the target height and what the blocks themselves draw (NaN: no water)
    private static float lastT = -1f;
    private static final long[] colKey = new long[SIZE * SIZE]; private static final int[] colY = new int[SIZE * SIZE]; private static final float[] colOwn = new float[SIZE * SIZE];
    private static int scanRow; private static final boolean[] colFall = new boolean[SIZE * SIZE], fallCol = new boolean[SIZE * SIZE]; private static final float[] colFallTop = new float[SIZE * SIZE], colFallBot = new float[SIZE * SIZE]; private static final it.unimi.dsi.fastutil.longs.LongOpenHashSet dirty = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    /** For the own water surface: per column the drawn surface height (relative to surfaceBaseY) and 1 where there is water, 0 where not. */
    private static final FloatBuffer hbuf = org.lwjgl.BufferUtils.createFloatBuffer(SIZE * SIZE * 4);   // r: height, g: water, b: foam (where falls land), a: unused
    private static final float[] foam = new float[SIZE * SIZE];
    private static int htex; public static volatile double surfaceBaseY;
    public static int heightTexture() { return htex; }
    /** The falls found this frame: per edge x, z (the block the water leaves), dx, dz (the way it falls), top and bottom heights (world). */
    public static volatile float[] falls = new float[0]; public static volatile int fallCount;
    private static float[] fallBuf = new float[6 * 256];

    /**
     * Every frame: the drawn water surface for every column near the camera. The target is the true height (the simulation's exact amount where it has one, else
     * the block's), smoothed over neighbouring water so flowing steps become slopes; the drawn surface eases to it within a few frames, so a change shows at once and
     * smoothly, never in the game's eighth-of-a-block steps. The shader moves each water top vertex by (drawn - block) here: where two columns meet at one height the
     * flowing side face between them closes up on its own.
     */
    public static synchronized void upload(double camX, double camZ) {
        var level = net.minecraft.client.Minecraft.getInstance().level; if (level == null) return;
        float t = now(), dt = lastT < 0f ? 0f : Math.min(0.1f, t - lastT); lastT = t;
        int ox = (int) Math.floor(camX) - SIZE / 2, oz = (int) Math.floor(camZ) - SIZE / 2;
        var bp = new BlockPos.MutableBlockPos();
        // the columns are kept per world position (wrapped): a column new to the map is read at once, the rest a quarter of the rows each frame (and at once where a block changed)
        scanRow = (scanRow + 1) & 3;
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int wx = ox + x, wz = oz + z, i = x + z * SIZE, slot = Math.floorMod(wx, SIZE) + Math.floorMod(wz, SIZE) * SIZE;
            long key = (((long) wx) << 32) ^ (wz & 0xffffffffL);
            boolean fresh = colKey[slot] != key || dirty.remove(key);
            if (fresh || (z & 3) == scanRow) {
                colKey[slot] = key;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                bp.set(wx, y, wz); FluidState fs = level.getFluidState(bp);
                colFall[slot] = false;
                if (!fs.is(net.minecraft.tags.FluidTags.WATER)) { colY[slot] = Integer.MIN_VALUE; }
                else if (!fs.isSource() && fs.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING)) {   // falling water is not a surface: it is drawn as a fall, from its top down to where it lands
                    colY[slot] = Integer.MIN_VALUE; colFall[slot] = true; colFallTop[slot] = y + 1;
                    var q = new BlockPos.MutableBlockPos(wx, y - 1, wz); int n = 0;
                    while (n < 96 && level.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER) && !level.getFluidState(q).isSource() && level.getFluidState(q).getValue(net.minecraft.world.level.material.FlowingFluid.FALLING)) { q.move(0, -1, 0); n++; }
                    colFallBot[slot] = q.getY() + 1 + (level.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER) ? 0.85f : 0f);
                }
                else { colY[slot] = y; colOwn[slot] = fs.getOwnHeight(); }
            }
            int y = colY[slot];
            fallCol[i] = colFall[slot];
            if (y == Integer.MIN_VALUE) { tgt[i] = Float.NaN; blk[i] = Float.NaN; continue; }
            float own = colOwn[slot], h = y + own;
            if (!sim.isEmpty()) { float[] s = sim.get(BlockPos.asLong(wx, y, wz)); if (s != null && s[3] < 0.5f) h = y + shown(s, t) * 8f / 9f; }   // the simulation's exact amount
            blk[i] = y + own; tgt[i] = h;
        }
        // smooth over neighbouring water of about the same level (a pool and the river falling into it stay apart): steps of flowing water become a slope
        buf.clear(); for (int i = 0; i < SIZE * SIZE * 2; i++) buf.put(0f);
        hbuf.clear(); for (int i = 0; i < SIZE * SIZE * 4; i++) hbuf.put(0f);
        java.util.Arrays.fill(foam, 0f);
        double baseY = Math.floor(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y);
        boolean any = false; float ease = Math.min(1f, dt * 14f); int fc = 0;
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int i = x + z * SIZE; if (fallCol[i]) { int sl = Math.floorMod(ox + x, SIZE) + Math.floorMod(oz + z, SIZE) * SIZE; if (fc < 256) { int o = fc * 6; fallBuf[o] = ox + x; fallBuf[o + 1] = oz + z; fallBuf[o + 2] = 0; fallBuf[o + 3] = 0; fallBuf[o + 4] = colFallTop[sl]; fallBuf[o + 5] = colFallBot[sl]; fc++; } any = true; continue; }
            float h0 = tgt[i]; if (Float.isNaN(h0)) continue;
            float sum = h0 * 2f, w = 2f;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue; int xx = x + dx, zz = z + dz; if (xx < 0 || zz < 0 || xx >= SIZE || zz >= SIZE) continue;
                float hn = tgt[xx + zz * SIZE]; if (Float.isNaN(hn) || Math.abs(hn - h0) > 3.2f) continue;   // steps of a cascade (up to three blocks) are smoothed into one slope
                float k = (dx == 0 || dz == 0) ? 1f : 0.6f; sum += hn * k; w += k;
            }
            float want = sum / w;
            int wx = ox + x, wz = oz + z, slot = Math.floorMod(wx, SIZE) + Math.floorMod(wz, SIZE) * SIZE; long key = (((long) wx) << 32) ^ (wz & 0xffffffffL);
            if (shownKey[slot] != key || Float.isNaN(shownH[slot])) { shownKey[slot] = key; shownH[slot] = Math.min(want, (float) Math.floor(h0) + 0.02f); }   // new water rises from its floor
            shownH[slot] += (want - shownH[slot]) * ease;
            hbuf.put(i * 4, (float) (shownH[slot] - baseY)); hbuf.put(i * 4 + 1, 1f);
            float off = shownH[slot] - blk[i];
            if (Math.abs(off) > 0.002f) { buf.put(i * 2, Math.max(-1.5f, Math.min(1.5f, off))); any = true; }
        }
        sim.values().removeIf(v -> v[3] > 0f && t - v[2] > 0.25f);
        falls = java.util.Arrays.copyOf(fallBuf, fc * 6); fallCount = fc;
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
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RG16F, SIZE, SIZE, 0, GL30C.GL_RG, GL11C.GL_FLOAT, buf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL30C.GL_RG, GL11C.GL_FLOAT, buf);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        // foam where falls land: strongest at the impact, fading over about three blocks
        for (int k = 0; k < fc; k++) {
            int fx = (int) fallBuf[k * 6] - ox, fz = (int) fallBuf[k * 6 + 1] - oz; float drop = fallBuf[k * 6 + 4] - fallBuf[k * 6 + 5], power = Math.min(1f, 0.35f + drop * 0.08f);
            for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) {
                int xx = fx + dx, zz = fz + dz; if (xx < 0 || zz < 0 || xx >= SIZE || zz >= SIZE) continue;
                float d = (float) Math.sqrt(dx * dx + dz * dz), f = power * Math.max(0f, 1f - d / 3.5f);
                int ii = xx + zz * SIZE; foam[ii] = Math.max(foam[ii], f);
            }
        }
        for (int ii = 0; ii < SIZE * SIZE; ii++) if (foam[ii] > 0f) hbuf.put(ii * 4 + 2, foam[ii]);
        // the height map for the own water surface (32-bit: absolute heights would lose detail in 16)
        hbuf.rewind(); boolean hfresh = htex == 0;
        if (hfresh) htex = GL11C.glGenTextures();
        GlUpload.bind(GL13C.GL_TEXTURE0 + UNIT, htex);
        if (hfresh) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F, SIZE, SIZE, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, hbuf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11C.GL_RGBA, GL11C.GL_FLOAT, hbuf);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        surfaceBaseY = baseY;
        originX = ox; originZ = oz; live = any;
    }
}
