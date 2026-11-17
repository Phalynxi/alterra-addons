package dev.phi.alterra.illuminate.snow;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.alterra.illuminate.IlluminateConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Snow as its own surface. The layers and snow blocks stay in the world (they are what you stand on and what the snow simulation changes), but they are not drawn near the camera:
 * the block shader drops every snow face within {@link #COVER} blocks, and this class draws the snow instead as one continuous sheet that follows the snow around it: smooth drifts,
 * soft walls to a track, no square pads and no stepped edges, with its own grain texture and shading that shows the relief (the sun's side bright, the far side and steep parts darker).
 * The sheet is built in 16 by 16 block tiles around the camera, each rebuilt only when what is in it changes.
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class SnowSurface {
    private SnowSurface() {}
    /** Blocks around the camera that the sheet stands in for (the block shader drops its snow within this square). */
    public static final int COVER = 48;
    private static final int T = 16, RADIUS = 4, PAD = 2, N = T + 2 * PAD;
    /** Columns closer than this in height (eighths of a block) are one smooth sheet; a bigger step is a step, with its own wall: snow lies in layers, it does not drape over terrain like a cloth. */
    private static final int TOL = 3;
    private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "snow_grain");
    private static ShaderInstance shader; private static boolean grainMade;

    private static final class Tile { VertexBuffer vb; long sig = Long.MIN_VALUE; boolean empty = true; int cols; }
    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static int cursor, passes;

    public static void registerShader(RegisterShadersEvent e) {
        try { e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "prism_snow"), DefaultVertexFormat.BLOCK), s -> shader = s); }
        catch (IOException ex) { throw new IllegalStateException("Illuminate: the snow shader could not be loaded", ex); }
    }

    /** Is the sheet standing in for the layers right now (then the block shader drops the layers' faces)? */
    public static boolean active() { return shader != null && IlluminateConfig.get(IlluminateConfig.CUSTOM_SNOW) && Minecraft.getInstance().level != null; }

    // ------------------------------------------------------------------ sampling
    private static final int[] S = new int[N * N], G = new int[N * N];   // surface and ground, in eighths of a block
    private static final byte[] K = new byte[N * N];                       // 0 no snow, 1 layers or a snow block, 2 snowy ground
    private static final boolean[] L = new boolean[N * N];
    private static final byte[] LB = new byte[N * N], LS = new byte[N * N];   // block light and sky light just above each column's surface

    private static long sample(ClientLevel lv, int x0, int z0) {
        long sig = 1125899906842597L; var p = new BlockPos.MutableBlockPos();
        for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) {
            int c = i + j * N; column(lv, x0 + i - PAD, z0 + j - PAD, c, p); lights(lv, x0 + i - PAD, z0 + j - PAD, c, p);
            sig = L[c] ? sig * 31 + S[c] * 3 + K[c] : sig * 31 + 7;
        }
        return sig;
    }

    private static void lights(ClientLevel lv, int x, int z, int c, BlockPos.MutableBlockPos p) {
        if (!L[c] || K[c] == 0) { LB[c] = LS[c] = 0; return; }
        p.set(x, Math.max(lv.getMinBuildHeight(), (S[c] + 7) / 8 + 1), z);
        LB[c] = (byte) lv.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p); LS[c] = (byte) lv.getBrightness(net.minecraft.world.level.LightLayer.SKY, p);
    }

    /** The bottom (a block's y) of the stack of snow that has its top at y: snow blocks, powder snow and full layers under it count as the same snow (so a wall runs the whole height). */
    private static int stackBottom(ClientLevel lv, BlockPos.MutableBlockPos p, int x, int z, int y, int floor) {
        int b = y;
        while (b - 1 >= floor) {
            var s = lv.getBlockState(p.set(x, b - 1, z));
            if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || (s.is(Blocks.SNOW) && s.getValue(SnowLayerBlock.LAYERS) == 8)) b--; else break;
        }
        return b;
    }

    /** One column into slot c of the sample arrays. */
    private static void column(ClientLevel lv, int x, int z, int c, BlockPos.MutableBlockPos p) {
        if (!lv.hasChunk(x >> 4, z >> 4)) { L[c] = false; S[c] = G[c] = 0; K[c] = 0; return; }
        L[c] = true;
        int top = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), floor = Math.max(lv.getMinBuildHeight(), top - 64);
        G[c] = S[c] = top * 8; K[c] = 0;
        for (int y = Math.min(lv.getMaxBuildHeight() - 1, top + 3); y >= floor; y--) {   // (a little above: powder snow does not stop motion, so the height map goes under it) down from the first air above everything: through air, leaves and plants (snow lies under trees too) to the first thing that is not
            var st = lv.getBlockState(p.set(x, y, z));
            if (st.is(Blocks.SNOW)) { S[c] = y * 8 + st.getValue(SnowLayerBlock.LAYERS); G[c] = stackBottom(lv, p, x, z, y, floor) * 8; K[c] = 1; return; }
            if (st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW)) { S[c] = (y + 1) * 8; G[c] = stackBottom(lv, p, x, z, y, floor) * 8; K[c] = 1; return; }
            if (st.hasProperty(BlockStateProperties.SNOWY) && st.getValue(BlockStateProperties.SNOWY)) { G[c] = S[c] = (y + 1) * 8; K[c] = 2; return; }   // snowy grass: its top is drawn with the snow texture
            if (!st.getFluidState().isEmpty()) { G[c] = S[c] = (y + 1) * 8; return; }
            if (st.isAir() || st.is(net.minecraft.tags.BlockTags.LEAVES) || !st.blocksMotion()) continue;
            G[c] = S[c] = (y + 1) * 8;   // ground without snow, unless there is snow under it: an overhang (a ledge, a roof, a bough) with snow on the floor below, through air only
            for (int y2 = y - 1; y2 >= Math.max(floor, y - 48); y2--) {
                var s2 = lv.getBlockState(p.set(x, y2, z));
                if (s2.is(Blocks.SNOW)) { S[c] = y2 * 8 + s2.getValue(SnowLayerBlock.LAYERS); G[c] = stackBottom(lv, p, x, z, y2, floor) * 8; K[c] = 1; return; }
                if (s2.is(Blocks.SNOW_BLOCK) || s2.is(Blocks.POWDER_SNOW)) { S[c] = (y2 + 1) * 8; G[c] = stackBottom(lv, p, x, z, y2, floor) * 8; K[c] = 1; return; }
                if (s2.hasProperty(BlockStateProperties.SNOWY) && s2.getValue(BlockStateProperties.SNOWY)) { G[c] = S[c] = (y2 + 1) * 8; K[c] = 2; return; }
                if (s2.isAir() || s2.is(net.minecraft.tags.BlockTags.LEAVES) || !s2.blocksMotion()) continue;
                break;
            }
            return;
        }
    }

    /** The height of the snow surface at a point (what the sheet draws there), or NaN where there is no snow or the sheet is not in use. For whatever has to sit on the snow: puffs, prints, spray. */
    public static float heightAt(ClientLevel lv, double x, double z) {
        if (!active()) return Float.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z); var p = new BlockPos.MutableBlockPos();
        for (int dj = -2; dj <= 2; dj++) for (int di = -2; di <= 2; di++) column(lv, bx + di, bz + dj, (PAD + di) + (PAD + dj) * N, p);
        if (K[PAD + PAD * N] == 0) return Float.NaN;
        float fx = (float) (x - bx), fz = (float) (z - bz);
        return Mth.lerp(fz, Mth.lerp(fx, cornerSmooth(PAD, PAD, 0, 0), cornerSmooth(PAD, PAD, 1, 0)), Mth.lerp(fx, cornerSmooth(PAD, PAD, 0, 1), cornerSmooth(PAD, PAD, 1, 1)));
    }

    // ------------------------------------------------------------------ the mesh
    /**
     * Height of a corner of column (i, j) at (a, b) in 0..1. The corner is a point shared by four columns, so every one of them must get the same answer or cracks open between them: the
     * columns are sorted into clusters (each within a block of the highest left in it: a cliff or a wall splits them) and every column takes the average of its own cluster, kept above
     * the ground of all the columns in it. A column on its own side of a wall gets its own height, and the wall is closed by a skirt.
     */
    private static float corner(int i, int j, int a, int b) {
        int[] q = new int[4]; int n = 0;
        for (int dj = b - 1; dj <= b; dj++) for (int di = a - 1; di <= a; di++) { int c = (i + di) + (j + dj) * N; if (L[c]) q[n++] = c; }
        int mine = i + j * N, hi = Integer.MAX_VALUE, sum = 0, cnt = 0; float lo = 0f;
        boolean bare = false; for (int k = 0; k < n; k++) if (K[q[k]] == 0) bare = true;   // next to bare ground the snow tapers down to it (a soft edge, not a lip)
        boolean[] used = new boolean[4];
        while (true) {   // the highest column not yet placed starts a cluster; the ones within a block below it join it
            int top = -1; for (int k = 0; k < n; k++) if (!used[k] && (top < 0 || S[q[k]] > S[q[top]])) top = k;
            if (top < 0) break;
            int ts = 0, tc = 0, thi = Integer.MAX_VALUE; float tlo = 0f; boolean has = false;
            for (int k = 0; k < n; k++) if (!used[k] && S[q[top]] - S[q[k]] <= TOL) {
                used[k] = true; ts += S[q[k]]; tc++; tlo = Math.max(tlo, K[q[k]] == 1 ? G[q[k]] + (bare ? 0.3f : 1f) : G[q[k]]); thi = Math.min(thi, S[q[k]] + 8); if (q[k] == mine) has = true;
            }
            if (has) { sum = ts; cnt = tc; lo = tlo; hi = thi; }
        }
        float h = cnt == 0 ? S[mine] : (float) sum / cnt;
        return Math.max(lo, Math.min(Math.max(hi, lo), h)) / 8f;
    }
    /** Do the four columns around a corner all lie within a block of each other (no wall or cliff between them)? */
    private static boolean single(int i, int j, int a, int b) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, n = 0;
        for (int dj = b - 1; dj <= b; dj++) for (int di = a - 1; di <= a; di++) { int c = (i + di) + (j + dj) * N; if (!L[c]) continue; lo = Math.min(lo, S[c]); hi = Math.max(hi, S[c]); n++; }
        return n > 0 && hi - lo <= TOL;
    }
    /** The corner height rounded off: where there is no wall, a corner takes in its four neighbouring corners (when they are within a block), so edges and steps of the layers roll over into soft shoulders. */
    private static float cornerSmooth(int i, int j, int a, int b) {
        float c0 = corner(i, j, a, b);
        if (!single(i, j, a, b)) return c0;
        int u = i + a, v = j + b; float sum = 4f * c0; int w = 4;
        for (int k = 0; k < 4; k++) {
            int uu = u + (k == 0 ? 1 : k == 1 ? -1 : 0), vv = v + (k == 2 ? 1 : k == 3 ? -1 : 0);
            if (uu < 1 || vv < 1 || uu >= N - 1 || vv >= N - 1 || !single(uu, vv, 0, 0)) continue;
            float c = corner(uu, vv, 0, 0); if (Math.abs(c - c0) > TOL / 8f) continue;
            sum += c; w++;
        }
        return sum / w;
    }
    /** The light at a corner: the average of the block and sky light above the (snowy) columns around it, so light changes smoothly across the sheet. */
    private static int cornerLight(int i, int j, int a, int b) {
        int sb = 0, ss = 0, n = 0;
        for (int dj = b - 1; dj <= b; dj++) for (int di = a - 1; di <= a; di++) { int q = (i + di) + (j + dj) * N; if (!L[q] || K[q] == 0) continue; sb += LB[q]; ss += LS[q]; n++; }
        if (n == 0) { int c = i + j * N; sb = LB[c]; ss = LS[c]; n = 1; }
        return net.minecraft.client.renderer.LightTexture.pack(Math.round((float) sb / n), Math.round((float) ss / n));
    }
    /** The surface's slope at a column, from its neighbours (a cliff does not count). */
    private static void slope(int i, int j, float[] out) {
        int c = i + j * N, base = S[c];
        int e = Mth.clamp(S[c + 1] - base, -8, 8), w = Mth.clamp(base - S[c - 1], -8, 8), s = Mth.clamp(S[c + N] - base, -8, 8), n = Mth.clamp(base - S[c - N], -8, 8);
        out[0] = (e + w) / 16f; out[1] = (s + n) / 16f;   // blocks of rise per block, east and south
    }

    private static MeshData build(ClientLevel lv, int x0, int z0, boolean fine) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        float[] sl = new float[2]; float[] nx = new float[4], nz = new float[4], hh = new float[4];
        boolean any = false;
        for (int j = PAD; j < PAD + T; j++) for (int i = PAD; i < PAD + T; i++) {
            int c = i + j * N; if (K[c] == 0) continue;
            any = true;
            int wx = x0 + i - PAD, wz = z0 + j - PAD;
            int light = cornerLight(i, j, 0, 0);
            if (fine && !exposedAny(i, j, c)) {   // footprints: a fine grid on a top that joins its neighbours all round
                float H = S[c] / 8f; float[] hh2 = {H, H, H, H}, nn = new float[4];
                int[] na = {0, 1, 1, 0}, nb = {1, 1, 0, 0};
                for (int sb = 0; sb < 4; sb++) for (int sa = 0; sa < 4; sa++) for (int k = 0; k < 4; k++) fineVertex(bb, wx, wz, (sa + na[k]) / 4f, (sb + nb[k]) / 4f, hh2, nn, nn, i, j);
            } else roundedColumn(bb, wx, wz, i, j, c, light);
        }
        return any ? bb.build() : null;   // (a builder that is not built is simply dropped)
    }
    private static final int ARC = 3;   // segments in a quarter round
    /** Is the snow beside column c on this side lower (or none): then that edge rolls over and drops. Side: 0 -x, 1 +x, 2 -z, 3 +z. */
    private static boolean exposed(int i, int j, int c, int side) {
        int q = (i + (side == 0 ? -1 : side == 1 ? 1 : 0)) + (j + (side == 2 ? -1 : side == 3 ? 1 : 0)) * N;
        if (!L[q]) return false;
        if (K[q] == 1 && K[c] == 1 && S[c] - S[q] <= 8) return false;   // snow within a block below: it ramps up to meet this one (no round, no wall)
        return K[q] == 0 ? S[q] < S[c] : S[q] < S[c] - TOL;
    }
    /** Does the column next to (i, j) at offset (di, dj) roll over on the same side? */
    private static boolean exposedNb(int i, int j, int side, int di, int dj) { int ii = i + di, jj = j + dj, c = ii + jj * N; if (ii < 1 || jj < 1 || ii >= N - 1 || jj >= N - 1 || !L[c] || K[c] == 0) return false; return exposed(ii, jj, c, side); }
    private static boolean exposedAny(int i, int j, int c) { for (int s = 0; s < 4; s++) if (exposed(i, j, c, s)) return true; return false; }
    /** Where the wall on this side stops: the neighbour's snow top or its ground (never below this column's own ground). */
    private static float wallBottom(int i, int j, int c, int side) {
        int q = (i + (side == 0 ? -1 : side == 1 ? 1 : 0)) + (j + (side == 2 ? -1 : side == 3 ? 1 : 0)) * N;
        return Math.max(S[q], G[c] - 12) / 8f;   // the snow drapes over the edge of the step below it (up to a block and a half), it does not stop where its own snow ends
    }

    /**
     * One column of snow as a rounded block: a flat top that runs on into neighbours of the same height, and on every side where the snow beside it is lower a
     * quarter round rolling over into a wall that drops to the neighbour (corners where two such sides meet get a quarter dome). No sheet is stretched between
     * columns, so nothing tears: steps read as soft, rounded terraces.
     */
    /** Snow one block higher next door (or less, but more than a joined step): this column's top ramps up to meet it at the shared edge (no wall between them). */
    private static boolean rampUp(int c, int q) { return L[q] && K[q] == 1 && K[c] == 1 && S[q] - S[c] > TOL && S[q] - S[c] <= 8; }
    private static final float RAMP = 0.5f;   // how far into the lower column the ramp reaches
    /** The height of column (i, j)'s top at (fx, fz) inside it (0..1 each way): its own snow, raised towards higher snow next door by the ramps. */
    private static float topAt(int i, int j, double fx, double fz) {
        int c = i + j * N; float H = S[c] / 8f, up = 0f;
        for (int side = 0; side < 4; side++) {
            int q = (i + (side == 0 ? -1 : side == 1 ? 1 : 0)) + (j + (side == 2 ? -1 : side == 3 ? 1 : 0)) * N;
            if (!rampUp(c, q)) continue;
            double d = side == 0 ? fx : side == 1 ? 1 - fx : side == 2 ? fz : 1 - fz;
            float u = (float) Mth.clamp(1.0 - d / RAMP, 0.0, 1.0); u = u * u * (3f - 2f * u);
            up = Math.max(up, u * (S[q] - S[c]) / 8f);
        }
        return H + up;
    }
    private static float topW(int i, int j, int wx, int wz, double x, double z) { return topAt(i, j, x - wx, z - wz); }

    /**
     * One column of snow as a rounded block. Its top is one height function (flat, rising in a ramp towards snow up to a block higher next door), so a hillside of
     * one-block steps reads as a smooth slope; where the snow beside it is lower by more than a block (or there is no snow) the edge rolls over in a quarter round
     * and drops as a wall (a quarter dome where two such edges meet). Everything (top, rounds, walls) takes its height from the same function, and along every
     * joined edge the higher side closes any difference with a face, so nothing between columns can stay open.
     */
    private static void roundedColumn(BufferBuilder bb, int wx, int wz, int i, int j, int c, int light) {
        float depth = (S[c] - G[c]) / 8f;
        double dcx = wx + 0.5 - camLodX, dcz = wz + 0.5 - camLodZ; boolean far = dcx * dcx + dcz * dcz > 32.0 * 32.0;   // far off a round edge is a pixel or two: plain blocks there
        float r = far ? 0f : Math.max(0.04f, Math.min(0.32f, depth * 0.85f));
        boolean e0 = exposed(i, j, c, 0), e1 = exposed(i, j, c, 1), e2 = exposed(i, j, c, 2), e3 = exposed(i, j, c, 3);
        float x0 = wx + (e0 ? r : 0f), x1 = wx + 1 - (e1 ? r : 0f), z0 = wz + (e2 ? r : 0f), z1 = wz + 1 - (e3 ? r : 0f);
        boolean ramps = false; for (int s = 0; s < 4; s++) { int q = (i + (s == 0 ? -1 : s == 1 ? 1 : 0)) + (j + (s == 2 ? -1 : s == 3 ? 1 : 0)) * N; if (rampUp(c, q)) ramps = true; }
        // the top: one quad when flat, a 4 by 4 grid where it ramps
        int G4 = ramps ? 3 : 1;
        for (int b = 0; b < G4; b++) for (int a = 0; a < G4; a++) {
            double[][] pts = {{a, b + 1}, {a + 1, b + 1}, {a + 1, b}, {a, b}};
            for (double[] pq : pts) {
                double x = x0 + (x1 - x0) * pq[0] / G4, z = z0 + (z1 - z0) * pq[1] / G4;
                float h = topW(i, j, wx, wz, x, z);
                float sx = (topW(i, j, wx, wz, x + 0.05, z) - topW(i, j, wx, wz, x - 0.05, z)) / 0.1f, sz = (topW(i, j, wx, wz, x, z + 0.05) - topW(i, j, wx, wz, x, z - 0.05)) / 0.1f;
                float len = (float) Math.sqrt(sx * sx + 1f + sz * sz);
                vertex(bb, x, h, z, -sx / len, -sz / len, light);
            }
        }
        // the rounded edges and the walls (their top follows the top's height along the edge)
        for (int side = 0; side < 4; side++) {
            boolean ex = side == 0 ? e0 : side == 1 ? e1 : side == 2 ? e2 : e3; if (!ex) continue;
            float ox = side == 0 ? -1 : side == 1 ? 1 : 0, oz = side == 2 ? -1 : side == 3 ? 1 : 0;
            float bottom = wallBottom(i, j, c, side);
            double ax, az, bx, bz;
            if (side < 2) { double ex0 = side == 0 ? wx + r : wx + 1 - r; ax = ex0; bx = ex0; az = z0; bz = z1; }
            else { double ez0 = side == 2 ? wz + r : wz + 1 - r; az = ez0; bz = ez0; ax = x0; bx = x1; }
            boolean sqA = side < 2 ? (!e2 && !exposedNb(i, j, side, 0, -1)) : (!e0 && !exposedNb(i, j, side, -1, 0));
            boolean sqB = side < 2 ? (!e3 && !exposedNb(i, j, side, 0, 1)) : (!e1 && !exposedNb(i, j, side, 1, 0));
            final int LEN = (sqA || sqB || ramps) ? 3 : 1;
            if (r <= 0f) {
                for (int L2 = 0; L2 < LEN; L2++) {
                    double u0 = L2 / (double) LEN, u1 = (L2 + 1) / (double) LEN;
                    double px0 = Mth.lerp(u0, ax, bx), pz0 = Mth.lerp(u0, az, bz), px1 = Mth.lerp(u1, ax, bx), pz1 = Mth.lerp(u1, az, bz);
                    float t0 = topW(i, j, wx, wz, px0, pz0), t1 = topW(i, j, wx, wz, px1, pz1);
                    if (t0 > bottom || t1 > bottom) { vertex(bb, px0, t0, pz0, ox, oz, light, 0f); vertex(bb, px1, t1, pz1, ox, oz, light, 0f); vertex(bb, px1, bottom, pz1, ox, oz, light, 0f); vertex(bb, px0, bottom, pz0, ox, oz, light, 0f); }
                }
                continue;
            }
            for (int L2 = 0; L2 < LEN; L2++) for (int s = 0; s < ARC; s++) {
                for (int k = 0; k < 4; k++) {
                    int ll = L2 + ((k == 1 || k == 2) ? 1 : 0), ss = s + (k >= 2 ? 1 : 0);
                    float u = ll / (float) LEN, sq = Math.max(sqA ? Math.max(0f, 1f - u * 2.5f) : 0f, sqB ? Math.max(0f, 1f - (1f - u) * 2.5f) : 0f);
                    float th = ss / (float) ARC * Mth.HALF_PI, sp = ss / (float) ARC;
                    double lx = Mth.lerp(u, (float) ax, (float) bx), lz = Mth.lerp(u, (float) az, (float) bz);
                    float H = topW(i, j, wx, wz, lx, lz);
                    float oR = r * Mth.sin(th), hR = H - r + r * Mth.cos(th);
                    float oS = sp < 0.5f ? r * sp * 2f : r, hS = sp < 0.5f ? H : H - r * (sp * 2f - 1f);
                    float o = Mth.lerp(sq, oR, oS), h = Mth.lerp(sq, hR, hS);
                    float ny = Mth.lerp(sq, Mth.cos(th), sp < 0.5f ? 1f : 0f), nh = Mth.lerp(sq, Mth.sin(th), sp < 0.5f ? 0f : 1f);
                    vertex(bb, lx + ox * o, h, lz + oz * o, ox * nh, oz * nh, light, Math.max(0f, ny));
                }
            }
            for (int L2 = 0; L2 < LEN; L2++) {   // the wall below the round
                double u0 = L2 / (double) LEN, u1 = (L2 + 1) / (double) LEN;
                double px0 = Mth.lerp(u0, ax, bx), pz0 = Mth.lerp(u0, az, bz), px1 = Mth.lerp(u1, ax, bx), pz1 = Mth.lerp(u1, az, bz);
                float t0 = topW(i, j, wx, wz, px0, pz0) - r, t1 = topW(i, j, wx, wz, px1, pz1) - r;
                if (t0 > bottom || t1 > bottom) { vertex(bb, px0 + ox * r, t0, pz0 + oz * r, ox, oz, light, 0f); vertex(bb, px1 + ox * r, t1, pz1 + oz * r, ox, oz, light, 0f); vertex(bb, px1 + ox * r, bottom, pz1 + oz * r, ox, oz, light, 0f); vertex(bb, px0 + ox * r, bottom, pz0 + oz * r, ox, oz, light, 0f); }
            }
        }
        // joined sides: wherever this top stands above the neighbour's along the shared edge, a face closes the difference (the neighbour does the same the other way round)
        for (int side = 0; side < 4; side++) {
            boolean ex = side == 0 ? e0 : side == 1 ? e1 : side == 2 ? e2 : e3; if (ex) continue;
            int di = side == 0 ? -1 : side == 1 ? 1 : 0, dj = side == 2 ? -1 : side == 3 ? 1 : 0, q = (i + di) + (j + dj) * N;
            if (!L[q] || K[q] == 0) continue;
            float ox = di, oz = dj;
            double ea0x = side == 0 ? wx : side == 1 ? wx + 1 : x0, ea1x = side == 0 ? wx : side == 1 ? wx + 1 : x1, ea0z = side == 2 ? wz : side == 3 ? wz + 1 : z0, ea1z = side == 2 ? wz : side == 3 ? wz + 1 : z1;
            for (int L2 = 0; L2 < 4; L2++) {
                double u0 = L2 / 4.0, u1 = (L2 + 1) / 4.0;
                double px0 = Mth.lerp(u0, ea0x, ea1x), pz0 = Mth.lerp(u0, ea0z, ea1z), px1 = Mth.lerp(u1, ea0x, ea1x), pz1 = Mth.lerp(u1, ea0z, ea1z);
                float m0 = topW(i, j, wx, wz, px0, pz0), m1 = topW(i, j, wx, wz, px1, pz1);
                float n0 = K[q] == 1 ? topW(i + di, j + dj, wx + di, wz + dj, px0, pz0) : S[q] / 8f, n1 = K[q] == 1 ? topW(i + di, j + dj, wx + di, wz + dj, px1, pz1) : S[q] / 8f;
                if (m0 > n0 + 0.002f || m1 > n1 + 0.002f) { vertex(bb, px0, m0, pz0, ox, oz, light, 0f); vertex(bb, px1, m1, pz1, ox, oz, light, 0f); vertex(bb, px1, Math.min(m1, n1), pz1, ox, oz, light, 0f); vertex(bb, px0, Math.min(m0, n0), pz0, ox, oz, light, 0f); }
            }
        }
        // where a wall rises beside it (snow or ground standing well above this top), the snow piles into the crease: a concave quarter round up against the wall
        if (r > 0f) for (int side = 0; side < 4; side++) {
            int di = side == 0 ? -1 : side == 1 ? 1 : 0, dj = side == 2 ? -1 : side == 3 ? 1 : 0, q = (i + di) + (j + dj) * N;
            if (!L[q]) continue;
            float edgeTop = topW(i, j, wx, wz, wx + 0.5 + di * 0.5, wz + 0.5 + dj * 0.5), hn = S[q] / 8f;
            if (rampUp(c, q) || hn - edgeTop < 0.6f) continue;
            float f = Math.min(0.35f, (hn - edgeTop) * 0.5f);
            float ox = -di, oz = -dj;   // pointing away from the wall, into this column
            double ex0 = side == 0 ? wx : side == 1 ? wx + 1 : x0, ex1 = side == 0 ? wx : side == 1 ? wx + 1 : x1, ez0 = side == 2 ? wz : side == 3 ? wz + 1 : z0, ez1 = side == 2 ? wz : side == 3 ? wz + 1 : z1;
            for (int s = 0; s < ARC; s++) for (int k = 0; k < 4; k++) {
                int ss = s + (k >= 2 ? 1 : 0); boolean end = k == 1 || k == 2;
                float th = ss / (float) ARC * Mth.HALF_PI;
                float dOut = f - f * Mth.cos(th), h = f - f * Mth.sin(th);   // from up the wall (th 0: at the wall, f high) down to the top (th 90: f out, on the top)
                double px = (end ? ex1 : ex0) + ox * dOut, pz = (end ? ez1 : ez0) + oz * dOut;
                float base = topW(i, j, wx, wz, px, pz);
                float nx = ox * Mth.sin(th), nz = oz * Mth.sin(th), ny = Mth.cos(th);
                vertex(bb, px, base + h, pz, nx * 0.7f, nz * 0.7f, light, Math.max(0.3f, ny));
            }
        }
        // corners where two rolling sides meet: a quarter dome, and its wall
        boolean[][] pairs = {{e0, e2}, {e1, e2}, {e1, e3}, {e0, e3}};
        for (int k = 0; k < 4; k++) {
            if (!pairs[k][0] || !pairs[k][1] || r <= 0f) continue;
            float sx = (k == 0 || k == 3) ? -1 : 1, sz = (k == 0 || k == 1) ? -1 : 1;
            double ccx = sx < 0 ? wx + r : wx + 1 - r, ccz = sz < 0 ? wz + r : wz + 1 - r;
            float H = topW(i, j, wx, wz, ccx, ccz);
            for (int a = 0; a < ARC; a++) for (int b = 0; b < ARC; b++) {
                double p0 = a / (double) ARC * Math.PI / 2, p1 = (a + 1) / (double) ARC * Math.PI / 2, q0 = b / (double) ARC * Math.PI / 2, q1 = (b + 1) / (double) ARC * Math.PI / 2;
                double[][] pq = {{p0, q0}, {p1, q0}, {p1, q1}, {p0, q1}};
                for (double[] v : pq) {
                    float nx = (float) (Math.sin(v[0]) * Math.cos(v[1]) * sx), nz = (float) (Math.sin(v[0]) * Math.sin(v[1]) * sz), ny = (float) Math.cos(v[0]);
                    vertex(bb, ccx + nx * r, (float) (H - r + ny * r), ccz + nz * r, nx, nz, light, ny);
                }
            }
            float bottom = Math.max(wallBottom(i, j, c, sx < 0 ? 0 : 1), wallBottom(i, j, c, sz < 0 ? 2 : 3)), wt = H - r;
            if (wt > bottom) for (int b = 0; b < ARC; b++) {
                double q0 = b / (double) ARC * Math.PI / 2, q1 = (b + 1) / (double) ARC * Math.PI / 2;
                float ax2 = (float) (Math.cos(q0) * sx), az2 = (float) (Math.sin(q0) * sz), bx2 = (float) (Math.cos(q1) * sx), bz2 = (float) (Math.sin(q1) * sz);
                vertex(bb, ccx + ax2 * r, wt, ccz + az2 * r, ax2, az2, light, 0f); vertex(bb, ccx + bx2 * r, wt, ccz + bz2 * r, bx2, bz2, light, 0f);
                vertex(bb, ccx + bx2 * r, bottom, ccz + bz2 * r, bx2, bz2, light, 0f); vertex(bb, ccx + ax2 * r, bottom, ccz + az2 * r, ax2, az2, light, 0f);
            }
        }
    }

    /** A vertex of a fine quad inside column (wx, wz) at fractions (fa, fb) of it: the smoothed surface plus the dent, its slope from both, darker where it is pressed. */
    private static void fineVertex(BufferBuilder bb, int wx, int wz, float fa, float fb, float[] hh, float[] nx, float[] nz, int i, int j) {
        float h = Mth.lerp(fb, Mth.lerp(fa, hh[0], hh[1]), Mth.lerp(fa, hh[3], hh[2]));
        float bx = Mth.lerp(fb, Mth.lerp(fa, nx[0], nx[1]), Mth.lerp(fa, nx[3], nx[2])), bz = Mth.lerp(fb, Mth.lerp(fa, nz[0], nz[1]), Mth.lerp(fa, nz[3], nz[2]));
        float by = (float) Math.sqrt(Math.max(1e-3, 1.0 - bx * bx - bz * bz)), sx = -bx / by, sz = -bz / by;
        double X = wx + fa, Z = wz + fb; float dent = SnowDents.at(X, Z);
        sx += (SnowDents.at(X + 0.125, Z) - SnowDents.at(X - 0.125, Z)) / 0.25f; sz += (SnowDents.at(X, Z + 0.125) - SnowDents.at(X, Z - 0.125)) / 0.25f;
        float len = (float) Math.sqrt(sx * sx + 1f + sz * sz); int c = Math.round(255f * Mth.clamp(1f + dent * 3.0f, 0.60f, 1f)), cb = Math.min(255, Math.round(c * 1.03f + 5f));   // pressed snow is darker and bluer
        bb.addVertex((float) (X - tileX), h + dent, (float) (Z - tileZ)).setColor(c, c, cb, 255).setUv((float) (X / 4.0), (float) (Z / 4.0)).setLight(cornerLight(i, j, fa >= 0.5f ? 1 : 0, fb >= 0.5f ? 1 : 0)).setNormal(-sx / len, 1f / len, -sz / len);
    }
    private static void vertex(BufferBuilder bb, double x, float y, double z, float nx, float nz, int light) { vertex(bb, x, y, z, nx, nz, light, -1f); }
    /** ny < 0: an upward-facing top whose normal is computed from the horizontal parts; otherwise the given normal y (0 for a skirt). */
    private static void vertex(BufferBuilder bb, double x, float y, double z, float nx, float nz, int light, float ny) {
        float yy = ny < 0f ? (float) Math.sqrt(Math.max(0.0, 1.0 - nx * nx - nz * nz)) : ny;
        bb.addVertex((float) (x - tileX), y, (float) (z - tileZ)).setColor(255, 255, 255, 255).setUv((float) (x / 4.0), (float) (z / 4.0)).setLight(light).setNormal(nx, yy, nz);
    }
    private static double tileX, tileZ; static double camLodX, camLodZ;

    // ------------------------------------------------------------------ keeping the tiles
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance(); ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) { clear(); return; }
        if (!active()) { if (!TILES.isEmpty()) clear(); return; }
        int ctx = Math.floorDiv(mc.player.getBlockX(), T), ctz = Math.floorDiv(mc.player.getBlockZ(), T);
        TILES.entrySet().removeIf(en -> {   // tiles that are out of range go
            int tx = (int) (en.getKey() >> 32), tz = (int) (long) en.getKey();
            boolean out = Math.abs(tx - ctx) > RADIUS + 1 || Math.abs(tz - ctz) > RADIUS + 1;
            if (out && en.getValue().vb != null) en.getValue().vb.close();
            return out;
        });
        int count = (2 * RADIUS + 1) * (2 * RADIUS + 1), built = 0, scanned = 0;
        passes++;
        for (int n = 0; n < count && scanned < 8 && built < 2; n++) {
            int idx = (cursor + n) % count, dx = idx % (2 * RADIUS + 1) - RADIUS, dz = idx / (2 * RADIUS + 1) - RADIUS;
            int tx = ctx + dx, tz = ctz + dz; long key = ((long) tx << 32) | (tz & 0xFFFFFFFFL);
            Tile t = TILES.computeIfAbsent(key, k -> new Tile());
            if (t.sig != Long.MIN_VALUE && t.cols == 0 && SnowDents.version(tx, tz) == 0 && (passes + idx) % 4 != 0) continue;   // a tile with no snow is looked at a quarter as often
            scanned++;
            int dv = SnowDents.version(tx, tz); long sig = sample(lv, tx * T, tz * T) ^ (dv * 0x9E3779B97F4A7C15L);
            { var cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(); camLodX = cp.x; camLodZ = cp.z;
              double ddx = tx * T + T / 2.0 - cp.x, ddz = tz * T + T / 2.0 - cp.z; long ring = (long) (Math.sqrt(ddx * ddx + ddz * ddz) / 24.0);   // which distance ring the tile is in: crossing one rebuilds it with the right detail
              sig ^= ring * 0xC2B2AE3D27D4EB4FL; }
            if (sig == t.sig) continue;
            t.sig = sig; built++; t.cols = 0; for (int jj = PAD; jj < PAD + T; jj++) for (int ii = PAD; ii < PAD + T; ii++) if (K[ii + jj * N] != 0) t.cols++;
            tileX = tx * T; tileZ = tz * T;
            MeshData md = build(lv, tx * T, tz * T, dv != 0);
            if (md == null) { t.empty = true; if (t.vb != null) { t.vb.close(); t.vb = null; } continue; }
            if (t.vb == null) t.vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            t.vb.bind(); t.vb.upload(md); VertexBuffer.unbind(); t.empty = false;
        }
        cursor = (cursor + scanned) % count;
    }

    @SubscribeEvent public static void left(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
    private static void clear() { for (Tile t : TILES.values()) if (t.vb != null) t.vb.close(); TILES.clear(); }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || !active() || TILES.isEmpty()) return;
        var mc = Minecraft.getInstance(); ClientLevel lv = mc.level; if (lv == null) return;
        if (!grainMade) makeGrain();
        var cam = e.getCamera().getPosition();
        double a = lv.getTimeOfDay(e.getPartialTick().getGameTimeDeltaPartialTick(false)) * Math.PI * 2;
        var u = shader.getUniform("SunDir"); if (u != null) u.set((float) -Math.sin(a), (float) Math.cos(a), 0f);
        RenderSystem.setShaderTexture(0, GRAIN);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableBlend(); RenderSystem.disableCull();
        for (var en : TILES.entrySet()) {
            Tile t = en.getValue(); if (t.vb == null) continue;
            int tx = (int) (en.getKey() >> 32), tz = (int) (long) en.getKey();
            Matrix4f mv = new Matrix4f(e.getModelViewMatrix()).translate((float) (tx * T - cam.x + 0.0), (float) -cam.y, (float) (tz * T - cam.z));
            t.vb.bind(); t.vb.drawWithShader(mv, e.getProjectionMatrix(), shader);
        }
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
    }

    /** The grain: a tile of 64 by 64 white pixels with a fine scatter of lighter and darker ones, drawn at a pixel per sixteenth of a block, so it reads as snow and repeats unseen. */
    private static void makeGrain() {
        grainMade = true;
        var img = new NativeImage(NativeImage.Format.RGBA, 64, 64, false); var r = RandomSource.create(7531);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int v = 238 + r.nextInt(14); float roll = r.nextFloat();
            if (roll > 0.985f) v = 255; else if (roll < 0.03f) v = 224 + r.nextInt(8);
            int rr = v, gg = Math.min(255, v + 1), bb = Math.min(255, v + (roll < 0.03f ? 6 : 2));
            img.setPixelRGBA(x, y, 0xFF000000 | (bb << 16) | (gg << 8) | rr);   // (ABGR)
        }
        var grainTex = new DynamicTexture(img); grainTex.setFilter(true, false);   // soft, not blocky
        Minecraft.getInstance().getTextureManager().register(GRAIN, grainTex);
    }
}
