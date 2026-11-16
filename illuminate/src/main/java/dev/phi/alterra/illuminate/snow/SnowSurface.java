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
    private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "snow_grain");
    private static ShaderInstance shader; private static boolean grainMade;

    private static final class Tile { VertexBuffer vb; long sig = Long.MIN_VALUE; boolean empty = true; int cols; }
    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static int cursor;

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

    /** One column into slot c of the sample arrays. */
    private static void column(ClientLevel lv, int x, int z, int c, BlockPos.MutableBlockPos p) {
        if (!lv.hasChunk(x >> 4, z >> 4)) { L[c] = false; S[c] = G[c] = 0; K[c] = 0; return; }
        L[c] = true;
        int top = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), floor = Math.max(lv.getMinBuildHeight(), top - 64);
        G[c] = S[c] = top * 8; K[c] = 0;
        for (int y = top; y >= floor; y--) {   // down from the first air above everything: through air, leaves and plants (snow lies under trees too) to the first thing that is not
            var st = lv.getBlockState(p.set(x, y, z));
            if (st.is(Blocks.SNOW)) { G[c] = y * 8; S[c] = G[c] + st.getValue(SnowLayerBlock.LAYERS); K[c] = 1; return; }
            if (st.is(Blocks.SNOW_BLOCK)) { G[c] = y * 8; S[c] = (y + 1) * 8; K[c] = 1; return; }
            if (st.hasProperty(BlockStateProperties.SNOWY) && st.getValue(BlockStateProperties.SNOWY)) { G[c] = S[c] = (y + 1) * 8; K[c] = 2; return; }   // snowy grass: its top is drawn with the snow texture
            if (!st.getFluidState().isEmpty()) { G[c] = S[c] = (y + 1) * 8; return; }
            if (st.isAir() || st.is(net.minecraft.tags.BlockTags.LEAVES) || !st.blocksMotion()) continue;
            G[c] = S[c] = (y + 1) * 8;   // ground without snow, unless there is snow under it: an overhang (a ledge, a roof, a bough) with snow on the floor below, through air only
            for (int y2 = y - 1; y2 >= Math.max(floor, y - 48); y2--) {
                var s2 = lv.getBlockState(p.set(x, y2, z));
                if (s2.is(Blocks.SNOW)) { G[c] = y2 * 8; S[c] = G[c] + s2.getValue(SnowLayerBlock.LAYERS); K[c] = 1; return; }
                if (s2.is(Blocks.SNOW_BLOCK)) { G[c] = y2 * 8; S[c] = (y2 + 1) * 8; K[c] = 1; return; }
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
        for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) column(lv, bx + di, bz + dj, (PAD + di) + (PAD + dj) * N, p);
        if (K[PAD + PAD * N] == 0) return Float.NaN;
        float fx = (float) (x - bx), fz = (float) (z - bz);
        return Mth.lerp(fz, Mth.lerp(fx, corner(PAD, PAD, 0, 0), corner(PAD, PAD, 1, 0)), Mth.lerp(fx, corner(PAD, PAD, 0, 1), corner(PAD, PAD, 1, 1)));
    }

    // ------------------------------------------------------------------ the mesh
    /** Height of a corner of column c at (a, b) in 0..1: the average of the four columns around it that are within a block of this one (walls and cliffs do not pull it), kept above the ground. */
    private static float corner(int i, int j, int a, int b) {
        int c = i + j * N, base = S[c], sum = 0, n = 0;
        for (int dj = b - 1; dj <= b; dj++) for (int di = a - 1; di <= a; di++) {
            int q = (i + di) + (j + dj) * N;
            if (!L[q] || Math.abs(S[q] - base) > 8) continue;
            sum += S[q]; n++;
        }
        float h = n == 0 ? base : (float) sum / n;
        return Mth.clamp(h, K[c] == 1 ? G[c] + 1 : G[c], S[c] + 8) / 8f;
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
            // corners in the order (0,0) (1,0) (1,1) (0,1): heights and normals (the average of the slopes of the four columns around each corner)
            int[] ca = {0, 1, 1, 0}, cb = {0, 0, 1, 1};
            for (int k = 0; k < 4; k++) {
                hh[k] = corner(i, j, ca[k], cb[k]);
                float sx = 0, sz = 0; int cnt = 0;
                for (int dj = cb[k] - 1; dj <= cb[k]; dj++) for (int di = ca[k] - 1; di <= ca[k]; di++) {
                    int q = (i + di) + (j + dj) * N; if (!L[q] || q % N == 0 || q % N == N - 1 || q / N == 0 || q / N == N - 1) continue;
                    slope(q % N, q / N, sl); sx += sl[0]; sz += sl[1]; cnt++;
                }
                if (cnt > 0) { sx /= cnt; sz /= cnt; }
                float len = (float) Math.sqrt(sx * sx + 1f + sz * sz); nx[k] = -sx / len; nz[k] = -sz / len;
            }
            // top: (x0,z1) (x1,z1) (x1,z0) (x0,z0) is the order that faces up
            int[] order = {3, 2, 1, 0};
            if (fine) {   // where something has stepped: four by four small quads to the block, the dents added to the surface
                int[] na = {0, 1, 1, 0}, nb = {1, 1, 0, 0};
                for (int sb = 0; sb < 4; sb++) for (int sa = 0; sa < 4; sa++) for (int k = 0; k < 4; k++) fineVertex(bb, wx, wz, (sa + na[k]) / 4f, (sb + nb[k]) / 4f, hh, nx, nz, i, j);
            } else for (int o : order) vertex(bb, wx + ca[o], hh[o], wz + cb[o], nx[o], nz[o], cornerLight(i, j, ca[o], cb[o]));
            // skirts where the neighbour has no snow and lies lower: the edge down to its ground
            for (int side = 0; side < 4; side++) {
                int di = side == 0 ? -1 : side == 1 ? 1 : 0, dj = side == 2 ? -1 : side == 3 ? 1 : 0, q = (i + di) + (j + dj) * N;
                if (!L[q]) continue;
                if (K[q] != 0 && (Math.abs(S[q] - S[c]) <= 8 || S[q] >= S[c])) continue;   // a snow neighbour about as high joins smoothly, a higher one closes the gap with its own skirt; a much lower one (a roof's next step, a ledge) needs the wall
                int[] e = side == 0 ? new int[] {3, 0} : side == 1 ? new int[] {1, 2} : side == 2 ? new int[] {0, 1} : new int[] {2, 3};   // the two corners on that edge
                if (S[q] / 8f >= Math.max(hh[e[0]], hh[e[1]])) continue;   // the neighbour is as high: no step to close
                float bottom = Math.max(S[q], G[c]) / 8f, ox = side == 0 ? -1 : side == 1 ? 1 : 0, oz = side == 2 ? -1 : side == 3 ? 1 : 0;
                if (hh[e[0]] <= bottom && hh[e[1]] <= bottom) continue;
                vertex(bb, wx + ca[e[0]], hh[e[0]], wz + cb[e[0]], ox, oz, cornerLight(i, j, ca[e[0]], cb[e[0]]), 0f);
                vertex(bb, wx + ca[e[1]], hh[e[1]], wz + cb[e[1]], ox, oz, cornerLight(i, j, ca[e[1]], cb[e[1]]), 0f);
                vertex(bb, wx + ca[e[1]], bottom, wz + cb[e[1]], ox, oz, cornerLight(i, j, ca[e[1]], cb[e[1]]), 0f);
                vertex(bb, wx + ca[e[0]], bottom, wz + cb[e[0]], ox, oz, cornerLight(i, j, ca[e[0]], cb[e[0]]), 0f);
            }
        }
        return any ? bb.build() : null;   // (a builder that is not built is simply dropped)
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
    private static double tileX, tileZ;

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
        for (int n = 0; n < count && scanned < 14 && built < 3; n++) {
            int idx = (cursor + n) % count, dx = idx % (2 * RADIUS + 1) - RADIUS, dz = idx / (2 * RADIUS + 1) - RADIUS;
            int tx = ctx + dx, tz = ctz + dz; long key = ((long) tx << 32) | (tz & 0xFFFFFFFFL);
            Tile t = TILES.computeIfAbsent(key, k -> new Tile());
            scanned++;
            int dv = SnowDents.version(tx, tz); long sig = sample(lv, tx * T, tz * T) ^ (dv * 0x9E3779B97F4A7C15L);
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
        Minecraft.getInstance().getTextureManager().register(GRAIN, new DynamicTexture(img));
    }
}
