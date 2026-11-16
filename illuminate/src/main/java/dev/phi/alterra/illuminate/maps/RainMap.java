package dev.phi.alterra.illuminate.maps;

import dev.phi.prismlib.api.PrismApi;


import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.nio.ShortBuffer;

/**
 * Where the rain can reach: for every block column around the player (128 x 128), the height of the first thing that actually stops rain, leaves not counting (rain drips through them) and logs, stone and roofs counting.
 * It comes from the world's own heightmaps, not from light, so the undersides of leaves, trunks and the ground under a canopy are as wet as the top of the tree, and the inside of a house stays dry.
 * The second channel is what stays wet under a roof: when something is built over a wet column, what was wet below keeps its wetness and dries slowly (faster when it is warm) instead of turning dry at once.
 * The water shader compares each surface with it. Stored as 16-bit values: heights 0 to 65535 for -64 to 448, wetness 0 to 65535 for 0 to 1.
 */
public final class RainMap {
    private static ShortBuffer upload;   // reused between rebuilds
    private RainMap() {}
    public static final int SIZE = 128, UNIT = 8;
    private static int tex; private static long builtAt = -100;
    public static volatile int originX, originZ; public static volatile boolean ready, hasRetained, hasWater;
    private static final short[] data = new short[SIZE * SIZE * 4];
    // kept per world column (wrapping every 128 blocks; the tag says which column an entry belongs to)
    private static final int[] tagX = new int[SIZE * SIZE], tagZ = new int[SIZE * SIZE], lastH = new int[SIZE * SIZE];
    private static final float[] retained = new float[SIZE * SIZE], water = new float[SIZE * SIZE], ground = new float[SIZE * SIZE], flow = new float[SIZE * SIZE];
    private static final boolean[] sink = new boolean[SIZE * SIZE];   // columns topped by real water: puddle water that runs there joins it
    private static final boolean[] tagged = new boolean[SIZE * SIZE];

    private static final float MAX_WATER = 0.30f;   // deep enough to read as a puddle (the shader shows 0.4 of it), still not a pond
    private static final boolean FAST = Boolean.getBoolean("alterra.fast");   // photo tour: no minute of soaking
    private static final float[] shown = new float[SIZE * SIZE], soak = new float[SIZE * SIZE];
    public static int texture() { return tex; }
    /** The water gathered at a block column right now (0 to 1), or 0 where the map does not cover it. */
    /** Water thrown onto land (a splash, a big step in a puddle): wets the columns around it, the most in the middle. Never into water itself, never more than a puddle's worth. */
    public static void splash(double x, double z, double radius, double amount) {
        if (!ready || amount <= 0) return;
        int r = (int) Math.ceil(radius);
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            double d = Math.sqrt(dx * dx + dz * dz); if (d > radius) continue;
            int wx = (int) Math.floor(x) + dx, wz = (int) Math.floor(z) + dz, i = (wx & (SIZE - 1)) + (wz & (SIZE - 1)) * SIZE;
            int lx = wx - originX, lz = wz - originZ;
            if (lx < 0 || lz < 0 || lx >= SIZE || lz >= SIZE || !tagged[i] || tagX[i] != wx || tagZ[i] != wz || sink[lx + lz * SIZE]) continue;
            water[i] = (float) Math.min(MAX_WATER, water[i] + amount * (1 - d / radius));
            hasWater = true;
        }
    }

    public static float puddle(int wx, int wz) {
        if (!ready) return 0f;
        int i = (wx & (SIZE - 1)) + (wz & (SIZE - 1)) * SIZE;
        return tagged[i] && tagX[i] == wx && tagZ[i] == wz ? Math.min(1f, water[i] / 0.4f) : 0f;
    }
    /** How far it is from the last update to the next (0 to 1): the shader blends the puddles from what was shown to what is there now. */
    public static float blend(long gameTime, float pt) { return Math.max(0f, Math.min(1f, (gameTime - builtAt + pt) / 10f)); }

    public static void update(ClientLevel level, double camX, double camZ, float wet, float dry) {
        long gt = level.getGameTime();
        if (gt - builtAt < 10 && gt >= builtAt && ready) return;
        float dt = (gt >= builtAt && builtAt > 0) ? Math.min(2f, (gt - builtAt) / 20f) : 0f;
        builtAt = gt;
        float fall = dt * dry / 150f;   // fully wet to dry in two and a half minutes at the normal pace
        boolean any = false;
        int ox = (int) Math.floor(camX) - SIZE / 2, oz = (int) Math.floor(camZ) - SIZE / 2;
        var probe = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int wx = ox + x, wz = oz + z; int h; float keep = 0f;
            if (!level.hasChunk(wx >> 4, wz >> 4)) h = 448;   // not loaded: treat as roofed
            else {
                h = PrismApi.ground(level, wx, wz);
                int i = (wx & (SIZE - 1)) + (wz & (SIZE - 1)) * SIZE;
                if (!tagged[i] || tagX[i] != wx || tagZ[i] != wz) { tagged[i] = true; tagX[i] = wx; tagZ[i] = wz; lastH[i] = h; retained[i] = 0f; water[i] = 0f; shown[i] = 0f; soak[i] = 0f; }
                else {
                    if (h > lastH[i] && wet > retained[i]) retained[i] = wet;   // just covered: what is below is as wet as the world is
                    if (h != lastH[i]) water[i] = 0f;   // built over or dug out: its puddle is gone
                    lastH[i] = h;
                    if (retained[i] > 0f) retained[i] = Math.max(0f, retained[i] - fall);
                }
                keep = retained[i]; if (keep > 0f) any = true;
            }
            int j = (x + z * SIZE) * 4;
            data[j] = (short) Math.round(Math.max(0, Math.min(65535, (h + 64) / 512.0 * 65535.0)));
            data[j + 1] = (short) Math.round(Math.max(0f, Math.min(1f, keep)) * 65535f);
            boolean intoWater = h < 448 && level.getFluidState(probe.set(wx, h - 1, wz)).is(net.minecraft.tags.FluidTags.WATER);
            sink[x + z * SIZE] = intoWater;
            perm[x + z * SIZE] = h >= 448 ? 0f : permeability(level.getBlockState(probe.set(wx, h - 1, wz)));
            ground[x + z * SIZE] = h + relief(wx, wz) - (intoWater ? 0.6f : 0f) + (h >= 448 ? 1000f : 0f);
        }
        puddles(level, ox, oz, dt, dry);
        hasRetained = any;
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {   // b: the water now, a: what was shown before (the shader blends from one to the other until the next update, so puddles fill smoothly)
            int i = ((ox + x) & (SIZE - 1)) + ((oz + z) & (SIZE - 1)) * SIZE; float cur = Math.min(1f, water[i] / 0.4f);
            data[(x + z * SIZE) * 4 + 2] = (short) Math.round(cur * 65535f);
            data[(x + z * SIZE) * 4 + 3] = (short) Math.round(shown[i] * 65535f);
            shown[i] = cur;
        }
        boolean create = tex == 0;
        if (create) tex = GL11C.glGenTextures();
        GlUpload.bind(GL13C.GL_TEXTURE0 + UNIT, tex);
        GlUpload.reset();
        if (upload == null) upload = org.lwjgl.BufferUtils.createShortBuffer(data.length);
        ShortBuffer buf = upload; buf.clear(); buf.put(data).flip();
        if (create) {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);   // (the shader reads the height exactly with texelFetch, which ignores this; the water is smoothed by it)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA16, SIZE, SIZE, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_SHORT, buf);
        } else GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_SHORT, buf);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        originX = ox; originZ = oz; ready = true;
    }

    /** A little unevenness of the ground inside a block (up to about a tenth of a block), so water gathers in patches on flat ground rather than as a film. */
    private static float relief(int x, int z) {
        double k = 0.0061359232;
        return (float) (0.06 * Math.cos((x * 211.0 + z * 97.0) * k + 1.0) + 0.06 * Math.cos((x * -139.0 + z * 181.0) * k + 2.0) + 0.03 * Math.cos((x * 397.0 + z * -251.0) * k + 0.3));
    }

    /**
     * Puddles as water: rain falls on every open column, the water runs to lower neighbours (by the ground plus the water on it), and it dries off (faster when it is warm, see Drying).
     * So it gathers in dips, against walls and in the low corners, spreads out on flat ground and drains off slopes, and the puddles shrink back after the rain.
     */
    private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};
    private static final float[] d = new float[4];

    private static final float[] perm = new float[SIZE * SIZE];   // how readily the ground under each column drinks rain: 0 (stone, clay) to 1 (sand, gravel)

    private static float permeability(net.minecraft.world.level.block.state.BlockState st) {
        if (st.is(net.minecraft.tags.BlockTags.SAND) || st.is(net.minecraft.world.level.block.Blocks.GRAVEL) || st.is(net.minecraft.tags.BlockTags.SNOW)) return 0.9f;
        if (st.is(net.minecraft.world.level.block.Blocks.MUD) || st.is(net.minecraft.world.level.block.Blocks.CLAY) || st.is(net.minecraft.world.level.block.Blocks.PACKED_MUD) || st.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)) return 0.12f;
        if (st.is(net.minecraft.world.level.block.Blocks.FARMLAND)) return 0.4f;
        if (st.is(net.minecraft.tags.BlockTags.DIRT)) return 0.5f;   // grass, dirt, podzol, moss: they drink, but not at once
        return 0.06f;   // stone, bricks, planks, terracotta: it runs off and stands
    }

    private static void puddles(ClientLevel level, int ox, int oz, float dt, float dry) {
        if (dt <= 0f) return;
        float rain = Math.max(level.getRainLevel(1f), PrismApi.storm().heavy(level));
        float heavy = PrismApi.storm().heavy(level);
        float add = rain * (0.0065f + 0.0040f * heavy) * dt, infil = 0.0012f * dt, evap = 0.0004f * dry * dt;   // per second: rain fills a puddle in about half a minute once the ground is soaked, and it takes a couple of minutes to go   // blocks of water per update: ordinary rain barely out-runs what the ground drinks, a heavy storm does
        float soakUp = rain * dt / (FAST ? 6f : 25f), soakDown = dt / 900f * dry;   // the ground has to be soaked first (a minute or so of steady rain), and dries out again over a quarter of an hour
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int i = ((ox + x) & (SIZE - 1)) + ((oz + z) & (SIZE - 1)) * SIZE;
            if (ground[x + z * SIZE] > 900f) continue;
            soak[i] = Math.max(0f, Math.min(1f, soak[i] + (rain > 0.05f ? soakUp : -soakDown)));
            float sat = Math.max(0f, Math.min(1f, (soak[i] - 0.7f) / 0.3f));
            int c0 = x + z * SIZE; float low = 0f; int nn = 0;
            for (int n = 0; n < 4; n++) { int nx = x + DX[n], nz = z + DZ[n]; if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE || ground[nx + nz * SIZE] > 900f) continue; low += ground[nx + nz * SIZE] - ground[c0]; nn++; }
            float pool = nn == 0 ? 0.1f : Math.max(0.06f, Math.min(1f, 0.10f + 2.4f * (low / nn)));   // only a dip holds water: a flat or a rise hardly does
            float drink = 1.15f - perm[c0];   // sand and gravel take it all; stone and clay let it stand
            water[i] = Math.max(0f, Math.min(MAX_WATER, water[i] + add * sat * pool * drink - infil * (1f + 2.5f * perm[c0]) * (water[i] > 0f ? 1f : 0f) * (1f - 0.8f * sat) - evap));   // a soaked ground hardly drinks any more
        }
        // waves washing up the shore: low land next to water gets wet in pulses as the sea comes in (more in a storm), then it drains and dries like any puddle
        float storm = Math.max(level.getThunderLevel(1f), Math.max(heavy, rain * 0.5f)); long gtm = level.getGameTime();
        float wash = (0.0035f + 0.018f * storm) * dt;
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            int c = x + z * SIZE; if (sink[c] || ground[c] > 900f) continue;
            float wg = Float.NaN;
            for (int n = 0; n < 4; n++) { int nx = x + DX[n], nz = z + DZ[n]; if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue; if (sink[nx + nz * SIZE]) { wg = ground[nx + nz * SIZE]; break; } }
            if (Float.isNaN(wg) || ground[c] - wg > 1.4f) continue;   // only low shore
            int i = ((ox + x) & (SIZE - 1)) + ((oz + z) & (SIZE - 1)) * SIZE;
            float pulse = Math.max(0f, (float) Math.sin(gtm * 0.045 + (ox + x) * 0.8 + (oz + z) * 0.6));   // a wave comes in, and goes out
            water[i] = Math.min(0.09f, water[i] + wash * (0.4f + pulse));
        }
        for (int it = 0; it < 1; it++) {   // the water runs downhill, slowly (faster made the puddles slide about)
            java.util.Arrays.fill(flow, 0f);
            for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
                int c = x + z * SIZE, i = ((ox + x) & (SIZE - 1)) + ((oz + z) & (SIZE - 1)) * SIZE; float w = water[i];
                if (w <= 0f) continue;
                float s = ground[c] + w, give = 0f;
                float tot = 0f;
                for (int n = 0; n < 4; n++) {
                    int nx = x + DX[n], nz = z + DZ[n]; d[n] = 0f;
                    if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE) continue;
                    int nc = nx + nz * SIZE; if (ground[nc] > 900f) continue;
                    float sn = ground[nc] + water[((ox + nx) & (SIZE - 1)) + ((oz + nz) & (SIZE - 1)) * SIZE];
                    if (sn < s) { d[n] = s - sn; tot += d[n]; }
                }
                if (tot <= 0f) continue;
                give = Math.min(w, tot * 0.5f) * 0.35f;
                flow[c] -= give;
                for (int n = 0; n < 4; n++) if (d[n] > 0f) flow[x + DX[n] + (z + DZ[n]) * SIZE] += give * d[n] / tot;
            }
            for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
                int i = ((ox + x) & (SIZE - 1)) + ((oz + z) & (SIZE - 1)) * SIZE;
                water[i] = sink[x + z * SIZE] ? 0f : Math.max(0f, Math.min(MAX_WATER, water[i] + flow[x + z * SIZE]));   // what reaches real water is part of it
            }
        }
        boolean wet = false;   // the map keeps being worked out while any water stands (so splashes and wash-up dry out even when it is not raining)
        for (int z = 0; z < SIZE && !wet; z++) for (int x = 0; x < SIZE; x++) if (water[((ox + x) & (SIZE - 1)) + ((oz + z) & (SIZE - 1)) * SIZE] > 0.004f) { wet = true; break; }
        hasWater = wet;
    }
}
