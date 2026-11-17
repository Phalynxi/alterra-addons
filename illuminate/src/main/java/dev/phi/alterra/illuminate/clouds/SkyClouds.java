package dev.phi.alterra.illuminate.clouds;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.alterra.illuminate.IlluminateConfig;
import dev.phi.alterra.illuminate.look.PostFx;
import dev.phi.prismlib.api.PrismApi;
import dev.phi.prismlib.api.StormCell;
import dev.phi.prismlib.world.Clouds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The sky's clouds, as real geometry: no ray marching, so they cost little, look right from any height (on the ground, inside the layer, above it) and are hidden behind mountains by the depth buffer.
 * The cover is the same layered noise the server rains by (Prism Lib's {@code Clouds}), read once per cloud cell when the mesh is built (every few seconds, or when the camera has moved a long way, or
 * the rain changes), never per pixel. The mesh moves with the wind as a whole.
 * <ul><li><b>Volumetric</b>: every cloud is a heap of soft sphere-like puffs (each with its own cauliflower edge, lit by the sun on its own sphere), flat-bottomed, tall and heaped or low and flat by region; near
 * clouds are made of many small puffs, far ones of fewer big ones. A storm cell has a cumulonimbus: a tall dark column with an anvil, a dark deck under it, drawn where the cell really is.</li>
 * <li><b>Vanilla+</b>: the game's blocky clouds: rounded blocks, joined where cloud runs on, as thick as the cloud is.</li></ul>
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class SkyClouds {
    private SkyClouds() {}
    private static ShaderInstance puffShader;
    private static final float BASE = 250f;   // the clouds' bottom: Alterra's land reaches far higher than vanilla's clouds

    public static void registerShaders(RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "prism_cloud_puff"), DefaultVertexFormat.BLOCK), s -> puffShader = s);
        } catch (IOException ex) { throw new IllegalStateException("Illuminate: the cloud shaders could not be loaded", ex); }
    }

    /** Are our clouds drawn (then the game's own are left out)? */
    public static boolean active() {
        var mc = Minecraft.getInstance(); var lv = mc.level;
        return puffShader != null && lv != null && IlluminateConfig.get(IlluminateConfig.CLOUDS) && !Float.isNaN(lv.effects().getCloudHeight())
                && mc.options.getCloudsType() != net.minecraft.client.CloudStatus.OFF;
    }

    // ------------------------------------------------------------------ the meshes
    /** Distance bands, near to far: cell size, from, to (blocks). Each band is its own mesh, drawn far to near (so the soft blending is in the right order), rebuilt on its own schedule:
     *  the near bands often, the far ones hardly ever (they only slide with the view, which is all the eye can tell at that distance). One band is rebuilt per frame at most. */
    private static final double[][] BANDS = {{112, 0, 2800}, {384, 1800, 8000}, {1280, 5500, 16000}};   // two lattices only: a cloud keeps its shape all the way out to 5 km, and the one change of lattice is far away, faded
    private static final double[] MOVE = {200, 600, 1600};
    private static final long[] AGE = {40000, 90000, 150000};
    private static final int NB = BANDS.length;
    private static final int SECT = 16;   // wedges of sky a band is split into: only those in view are drawn
    /** A band's puffs, split into wedges of the sky round the band's centre (and one part for everything near that, drawn always). */
    private static final class Mesh { final VertexBuffer[] parts = new VertexBuffer[SECT + 1]; void close() { for (var p : parts) if (p != null) p.close(); } }
    private static void close(Mesh m) { if (m != null) m.close(); }
    private static final Mesh[] bandVb = new Mesh[NB + 1]; private static final VertexBuffer[] towerVb = new VertexBuffer[NB];   // [NB] is the cirrus
    private static float camAz, halfAz; private static boolean allSectors;   // which way the camera looks (azimuth as atan2(x, z)) and how wide it sees, this frame
    private static final double[] bandX = new double[NB + 1], bandZ = new double[NB + 1]; private static final long[] bandAt = new long[NB + 1]; private static final boolean[] bandBuilt = new boolean[NB + 1];
    private static VertexBuffer cumulusVb;   // vanilla+ blocks
    /** A rebuilt mesh does not replace the old one at once: both are drawn for a moment, the old fading out as the new fades in, so nothing jumps. */
    private static final Mesh[] prevVb = new Mesh[NB + 1]; private static final VertexBuffer[] prevTower = new VertexBuffer[NB];
    private static final double[] prevX = new double[NB + 1], prevZ = new double[NB + 1]; private static final long[] fadeStart = new long[NB + 1];
    private static double prevTowerX, prevTowerZ; private static long towerFade;
    private static final float XFADE_MS = 1200f;   // a rebuilt band fades in over the old one: the clouds change smoothly, never in a jump
    private static float fadeT(long start, long now) { return start == 0 ? 1f : Mth.clamp((now - start) / XFADE_MS, 0f, 1f); }
    private static void swapBand(int i, long now) { close(prevVb[i]); prevVb[i] = bandVb[i]; prevX[i] = bandX[i]; prevZ[i] = bandZ[i]; bandVb[i] = null; fadeStart[i] = prevVb[i] != null ? now : 0; }
    private static void swapTowers(long now) { boolean any = false; for (int i = 0; i < NB; i++) { close(prevTower[i]); prevTower[i] = towerVb[i]; towerVb[i] = null; any |= prevTower[i] != null; } prevTowerX = towerX; prevTowerZ = towerZ; towerFade = any ? now : 0; }
    private static double buildQx, buildQz, towerX, towerZ; private static long builtAt, towerAt; private static float builtRain = -1f, builtThunder = -1f; private static int builtStyle = -1, towerSig;
    private static final double CIRRUS_CELL = 900.0;
    private record Job(List<Puff> puffs, double x, double z) {}
    /**
     * Sunset and sunrise as the clock has them: {intensity, redness}. From about 11500 the clouds warm to pale gold, turn orange by 12300 and red by 13000, stay red a while and are gone by about 14500;
     * the morning is the same in the other direction (about 21500 to 1000).
     */
    private static float[] twilight(long dayTime) {
        float t = (float) (dayTime % 24000L);
        if (t > 18000f) t = 36000f - t;   // the morning is the evening run backwards
        else if (t < 1000f) t = 36000f - (t + 24000f);
        float warm = smooth(11500f, 12500f, t) * (1f - smooth(13500f, 14300f, t));
        float red = smooth(12000f, 13000f, t);
        float dim = 1f - 0.82f * smooth(12700f, 14000f, t);   // the red clouds darken as the light goes: dark rose, then night
        return new float[] {warm, red, dim};
    }

    /** How deep inside a cloud the camera is (0 to 1, blended slowly): the scene pass draws the mist from it. */
    private static final boolean FORCE_IN = Boolean.getBoolean("alterra.forceInCloud");   // (a test switch: pretend to be deep inside a cloud)
    public static float inside() { return FORCE_IN ? 1f : inCloud; }
    private static volatile List<Puff> nearPuffs = List.of(); private static double nearX, nearZ; private static float inCloud;   // the nearest band's puffs (to tell when the camera is inside a cloud)
    /** How deep in cloud the camera is (0 clear, 1 thick): from the nearest band's puffs, each an ellipsoid. */
    private static float cloudAt(double qx, double camY, double qz) {
        double ox = nearX - qx, oz = nearZ - qz; float d = 0f;
        for (Puff p : nearPuffs) {
            double dx = (p.x + ox) / p.sx, dz = (p.z + oz) / p.sx, dy = (p.y - camY) / p.sy, r2 = dx * dx + dy * dy + dz * dz;
            if (r2 < 1.0) d = Math.max(d, (float) (1.0 - r2) * p.alpha);
        }
        return Math.min(1f, d * 2.2f);
    }
    @SuppressWarnings("unchecked") private static final java.util.concurrent.CompletableFuture<Job>[] jobs = new java.util.concurrent.CompletableFuture[NB + 1];
    private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread th = new Thread(r, "Illuminate clouds"); th.setDaemon(true); th.setPriority(Thread.MIN_PRIORITY); return th; });
    private static void start(int band, double sx, double sz, float evo, float rain, float thunder, float storm0, List<StormCell> cells, double driftX, double driftZ, long now) {
        List<StormCell> cs = List.copyOf(cells);
        jobs[band] = java.util.concurrent.CompletableFuture.supplyAsync(() -> new Job(puffsFor(band, sx, sz, evo, rain, thunder, storm0, cs, driftX, driftZ), sx, sz), WORKER);
        bandAt[band] = now;   // (so it is not picked again while it runs)
    }
    private static double buildCamY; private static boolean wasAbove; private static List<StormCell> cellsCache; private static long cellsAt;   // the camera height the meshes are sorted for (blending order is by true 3D distance), and which side of the layer it was

    private static final class Puff { float x, y, z, sx, sy, shade, dark, seed, alpha; double d2; }

    private static float smooth(float a, float b, float x) { float t = Mth.clamp((x - a) / (b - a), 0f, 1f); return t * t * (3f - 2f * t); }

    private static void close(VertexBuffer vb) { if (vb != null) vb.close(); }
    private static void clear() {
        close(cumulusVb); cumulusVb = null;
        for (int i = 0; i <= NB; i++) { close(bandVb[i]); bandVb[i] = null; close(prevVb[i]); prevVb[i] = null; bandBuilt[i] = false; }
        for (int i = 0; i < NB; i++) { close(towerVb[i]); towerVb[i] = null; close(prevTower[i]); prevTower[i] = null; }
        for (int i = 0; i <= NB; i++) jobs[i] = null;
        builtStyle = -1; towerSig = 0;
    }
    @SubscribeEvent public static void left(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }

    private static float influence(List<StormCell> cells, double wx, double wz) { float s = 0f; for (var c : cells) s = Math.max(s, c.at(wx, wz)); return s; }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active()) return;
        var mc = Minecraft.getInstance(); ClientLevel lv = mc.level; if (lv == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        var cam = e.getCamera().getPosition();
        double ct = PostFx.cloudClock(lv, pt); long cgt = (long) Math.floor(ct); float cpt = (float) (ct - cgt);
        double driftX = PrismApi.clouds().driftX(cgt, cpt), driftZ = PrismApi.clouds().driftZ(cgt, cpt) + 7.92, evo = PrismApi.clouds().evolve(cgt, cpt);
        double qx = cam.x + driftX, qz = cam.z + driftZ;   // the camera in the clouds' own, drifting space
        int style = 1;
        float rain = Math.min(1f, PrismApi.rainAt(lv, cam.x, cam.z)), thunder = lv.getThunderLevel(pt);
        float storm0 = Mth.clamp(rain * 0.6f + thunder * 0.5f, 0f, 1f);
        long now = System.currentTimeMillis();
        buildCamY = cam.y;
        boolean above = cam.y > BASE + 60.0;
        if (above != wasAbove) { wasAbove = above; for (int i = 0; i <= NB; i++) bandAt[i] = 0; towerAt = 0; }   // crossed the layer: the order the puffs blend in flips, rebuild (faded)
        if (cellsCache == null || now - cellsAt > 2000) { cellsCache = List.copyOf(PrismApi.storm().cells(lv, cam.x, cam.z, 20000)); cellsAt = now; }   // the storms change slowly: asked for every two seconds, not every frame
        List<StormCell> cells = cellsCache;
        boolean changed = style != builtStyle || Math.abs(rain - builtRain) > 0.35f || Math.abs(thunder - builtThunder) > 0.4f;
        if (style != builtStyle) clear();
        else if (changed) for (int i = 0; i <= NB; i++) bandAt[i] = 0;   // the weather changed: every band is due, rebuilt one a frame and faded in
        if (changed) { builtStyle = style; builtRain = rain; builtThunder = thunder; }
        {
            // rebuild at most one band per frame (the most overdue), all of them the first time
            // the bands are laid out round a centre that only moves in steps of 200 blocks, so the layout of the clouds stays put while you walk (it is rebuilt, faded in slowly, when the centre steps)
            double sx = Math.round(qx / 200.0) * 200.0, sz = Math.round(qz / 200.0) * 200.0;
            int pick = -1; double worst = 1.0; boolean first = !bandBuilt[0];
            for (int i = 0; i <= NB; i++) {
                int bi = Math.min(i, NB - 1);
                double urgency = !bandBuilt[i] ? 1e9 : fadeT(fadeStart[i], now) < 1f ? 0.0 :   // never rebuild while the last change is still fading in
                     (bandX[i] != sx || bandZ[i] != sz) ? 2.0 : (now - bandAt[i]) / (double) AGE[bi];
                if (first && !bandBuilt[i] && jobs[i] == null) start(i, sx, sz, (float) evo, rain, thunder, storm0, cells, driftX, driftZ, now);
                else if (urgency > worst || pick < 0 && urgency >= 1.0) { worst = urgency; pick = i; }
            }
            if (pick >= 0 && jobs[pick] == null) start(pick, sx, sz, (float) evo, rain, thunder, storm0, cells, driftX, driftZ, now);
            for (int i = 0; i <= NB; i++) {   // finished jobs: their puffs into a mesh (the slow part, the noise and the puffs, ran on a worker thread)
                var j = jobs[i]; if (j == null || !j.isDone()) continue;
                jobs[i] = null;
                try { var res = j.join(); if (Boolean.getBoolean("alterra.photos")) org.slf4j.LoggerFactory.getLogger("Illuminate").info("[hunt] cloud band {}: {} puffs", i, res.puffs.size()); if (i == 0) { nearPuffs = List.copyOf(res.puffs); nearX = res.x; nearZ = res.z; } swapBand(i, now); bandVb[i] = upload(res.puffs); bandAt[i] = now; bandX[i] = res.x; bandZ[i] = res.z; bandBuilt[i] = true; }
                catch (Exception ex) { org.slf4j.LoggerFactory.getLogger("Illuminate").warn("Illuminate: a cloud band could not be built", ex); bandAt[i] = now; bandBuilt[i] = true; }
            }
        }

        double a = lv.getTimeOfDay(pt) * Math.PI * 2; var sun = new Vector3f((float) -Math.sin(a), (float) Math.cos(a), 0f);
        float day = smooth(-0.10f, 0.35f, sun.y);
        var cc = lv.getCloudColor(pt); var sky = lv.getSkyColor(cam, pt);
        float[] hz = dev.phi.alterra.illuminate.look.SkyGlow.horizon(lv, sky, 1f, cpt);
        float fade = 1f - Math.min(1f, PrismApi.storm().fog() * 1.15f);   // hidden in a sandstorm or blizzard
        Matrix4f base = new Matrix4f(e.getModelViewMatrix()), proj = e.getProjectionMatrix();
        float far = mc.gameRenderer.getDepthFar(), rmax = Math.max(64f, far * 0.92f);   // from the renderer, not the matrix: view bobbing is folded into the projection matrix and would swing it
        Vector3f sunV = base.transformDirection(new Vector3f(sun)).normalize();
        RenderSystem.enableDepthTest(); RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        ShaderInstance sh = puffShader;
        RenderSystem.depthMask(false);   // puffs are soft: they do not write depth
        var ru = sh.getUniform("RMax"); if (ru != null) ru.set(rmax);
        uniform3(sh, "SunW", sun.x, sun.y, sun.z);
        { float[] glw = dev.phi.alterra.illuminate.look.SkyGlow.glow(lv, cpt); var gu = sh.getUniform("GlowCol"); if (gu != null) gu.set(glw[0], glw[1], glw[2], glw[3] * IlluminateConfig.get(IlluminateConfig.SUNSET_STRENGTH).floatValue()); }
        uniform3(sh, "CloudColor", (float) cc.x, (float) cc.y, (float) cc.z); uniform3(sh, "Horizon", hz[0], hz[1], hz[2]);
        var pu = sh.getUniform("Params"); if (pu != null) { float[] tw = twilight(lv.getDayTime()); float ss = IlluminateConfig.get(IlluminateConfig.SUNSET_STRENGTH).floatValue(); pu.set(day, tw[0] * ss, tw[1], tw[2]); }
        var main = mc.getMainRenderTarget();
        if (style == 1) {
            RenderSystem.enableBlend(); RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);   // premultiplied, straight into the scene at full size
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
            float want = cloudAt(qx, cam.y, qz); inCloud += (want - inCloud) * 0.035f;   // a slow blend: you drift into a cloud, it never switches on
            {   // what the camera can see: its heading, and how wide it sees (more when it looks steeply up or down)
                float pitch = (float) Math.toRadians(e.getCamera().getXRot()), tanX = 1f / Math.max(0.2f, e.getProjectionMatrix().m00());
                camAz = (float) -Math.toRadians(e.getCamera().getYRot());
                allSectors = Math.abs(pitch) > 1.05f;
                halfAz = (float) Math.min(Math.PI, Math.atan((tanX + 0.45f) / Math.max(0.2f, Math.cos(pitch))));
            }
            for (int i = NB; i >= 0; i--) {   // cirrus first (the farthest), then the bands far to near (storms are the same clouds, gathered and darkened)
                if (i > 0 && inCloud > 0.6f) continue;   // inside a thick cloud nothing farther can be seen: not drawn (they come back as you leave it)
                float tb = fadeT(fadeStart[i], now);
                if (prevVb[i] != null) { if (tb < 1f) drawMesh(prevVb[i], prevX[i] - qx, prevZ[i] - qz, base, cam.y, proj, sh, fade * (1f - tb)); else { close(prevVb[i]); prevVb[i] = null; } }
                drawMesh(bandVb[i], bandX[i] - qx, bandZ[i] - qz, base, cam.y, proj, sh, fade * tb);
            }
            VertexBuffer.unbind();
        }
        VertexBuffer.unbind();
        if (style == 1 && inCloud > 0.01f && !IlluminateConfig.get(IlluminateConfig.SCENE_LOOK)) {   // inside a cloud (without the scene pass, which draws the mist itself): everything goes soft and pale, more the deeper in
            float g = (float) Math.min(1.0, cc.x * (0.75 + 0.25 * day)) * (1f - 0.68f * storm0);   // inside a storm cloud it is dark grey, not white
            RenderSystem.disableDepthTest(); RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
            var bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f id = new Matrix4f(); int a8 = Math.round(Math.min(0.92f, inCloud * 0.92f) * fade * 255f);
            int r8 = Math.round(g * 0.93f * 255f), g8 = Math.round(g * 0.95f * 255f), b8 = Math.round(Math.min(1f, g * 1.0f) * 255f);
            bb.addVertex(id, -1f, -1f, 0f).setColor(r8, g8, b8, a8); bb.addVertex(id, 1f, -1f, 0f).setColor(r8, g8, b8, a8);
            bb.addVertex(id, 1f, 1f, 0f).setColor(r8, g8, b8, a8); bb.addVertex(id, -1f, 1f, 0f).setColor(r8, g8, b8, a8);
            Matrix4f keepP = new Matrix4f(RenderSystem.getProjectionMatrix()); var keepS = RenderSystem.getVertexSorting();
            RenderSystem.setProjectionMatrix(new Matrix4f(), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            var mvs = RenderSystem.getModelViewStack(); mvs.pushMatrix(); mvs.identity(); RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.buildOrThrow());
            mvs.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(keepP, keepS);
            RenderSystem.enableDepthTest();
        }
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
    }

    private static void draw(VertexBuffer vb, double dx, double dz, Matrix4f base, double camY, Matrix4f proj, ShaderInstance sh, float alpha) {
        if (vb == null || alpha <= 0.003f) return;
        var fu = sh.getUniform("Fade"); if (fu != null) fu.set(alpha);
        Matrix4f mv = new Matrix4f(base).translate((float) dx, (float) -camY, (float) dz);
        vb.bind(); vb.drawWithShader(mv, proj, sh);
    }

    // ------------------------------------------------------------------ the half-size cloud picture (soft clouds lose nothing at half size; the GPU shades a quarter of the pixels)
    private static final float SCALE = 0.66f;
    private static int fbo, colTex, depTex, fw, fh;
    private static void target(int w, int h) {
        if (fbo != 0 && fw == w && fh == h) return;
        if (fbo == 0) { fbo = org.lwjgl.opengl.GL30C.glGenFramebuffers(); colTex = org.lwjgl.opengl.GL11C.glGenTextures(); depTex = org.lwjgl.opengl.GL11C.glGenTextures(); }
        fw = w; fh = h;
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(colTex);
        org.lwjgl.opengl.GL11C.glTexImage2D(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30C.GL_RGBA16F, w, h, 0, org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_FLOAT, (java.nio.FloatBuffer) null);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_LINEAR);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_LINEAR);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(depTex);
        org.lwjgl.opengl.GL11C.glTexImage2D(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30C.GL_DEPTH_COMPONENT32F, w, h, 0, org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT, org.lwjgl.opengl.GL11C.GL_FLOAT, (java.nio.FloatBuffer) null);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
        org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(0);
        org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, fbo);
        org.lwjgl.opengl.GL30C.glFramebufferTexture2D(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, colTex, 0);
        org.lwjgl.opengl.GL30C.glFramebufferTexture2D(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, depTex, 0);
        int st = org.lwjgl.opengl.GL30C.glCheckFramebufferStatus(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER);
        if (st != org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE) org.slf4j.LoggerFactory.getLogger("Illuminate").warn("Illuminate: the cloud picture could not be made (status {})", st);
    }
    /** A screen-filling quad with the cloud picture, premultiplied, over whatever is bound. */
    private static void composite() {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader); RenderSystem.setShaderTexture(0, colTex);
        RenderSystem.enableBlend(); RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        var bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f id = new Matrix4f();
        bb.addVertex(id, -1f, -1f, 0f).setUv(0f, 0f); bb.addVertex(id, 1f, -1f, 0f).setUv(1f, 0f); bb.addVertex(id, 1f, 1f, 0f).setUv(1f, 1f); bb.addVertex(id, -1f, 1f, 0f).setUv(0f, 1f);
        Matrix4f keepP = new Matrix4f(RenderSystem.getProjectionMatrix()); var keepS = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f(), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var mvs = RenderSystem.getModelViewStack(); mvs.pushMatrix(); mvs.identity(); RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.buildOrThrow());
        mvs.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(keepP, keepS);
        RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest();
    }

    private static void uniform3(ShaderInstance s, String name, float a, float b, float c) { var u = s.getUniform(name); if (u != null) u.set(a, b, c); }

    private static float coverAt(double cx, double cz, float evo, float rain, float thunder, List<StormCell> cells, double driftX, double driftZ) {
        float raw = Clouds.fbm((float) (cx * 0.0065), (float) (cz * 0.0065), evo) + 0.16f * rain + 0.22f * thunder + 0.55f * influence(cells, cx - driftX, cz - driftZ);
        return smooth(0.47f, 0.62f, raw);
    }

    /** Cloud kinds, chosen by region: how tall, how wide, how flat-bottomed, at what height. */
    private static final int CUMULUS = 0, STRATO = 1, ALTO = 2;

    /** One band of the drifting clouds (or, for index NB, the cirrus) into its mesh. Coordinates are relative to the camera in cloud space at build time. */
    private static List<Puff> puffsFor(int band, double qx, double qz, float evo, float rain, float thunder, float storm0, List<StormCell> cells, double driftX, double driftZ) {
        List<Puff> puffs = new ArrayList<>(4000);
        if (band == NB) { cirrus(puffs, qx, qz, evo); }
        else {
            double cell = BANDS[band][0], inner = BANDS[band][1], outer = BANDS[band][2];
            float sc = (float) (cell / 64.0), taller = (float) Math.sqrt(sc);   // far clouds stand taller so they still read at a distance
            int ix0 = (int) Math.floor((qx - outer) / cell), ix1 = (int) Math.floor((qx + outer) / cell), iz0 = (int) Math.floor((qz - outer) / cell), iz1 = (int) Math.floor((qz + outer) / cell);
            for (int cz = iz0; cz <= iz1; cz++) for (int cx = ix0; cx <= ix1; cx++) {
                double mx = (cx + 0.5) * cell, mz = (cz + 0.5) * cell, d = Math.max(Math.abs(mx - qx), Math.abs(mz - qz));
                if (d > outer || d < inner) continue;
                // the shape and size of a cloud come from the weather-free cover, so a storm arriving never makes the clouds that are there swell; the weather only fills in clouds where the sky was empty (and darkens them)
                float amt = (IlluminateConfig.get(IlluminateConfig.CLOUD_AMOUNT).floatValue() - 1f) * 0.12f;   // more cover: the clouds form at a lower threshold
                float cb = smooth(0.47f - amt, 0.62f - amt, Clouds.fbm((float) (mx * 0.0065), (float) (mz * 0.0065), evo));
                float c = cb >= 0.05f ? cb : coverAt(mx, mz, evo, rain, thunder, cells, driftX, driftZ);
                if (c < 0.05f) continue;
                var r = RandomSource.create(cx * 73856093L ^ cz * 19349663L ^ (band == 1 ? 0 : band + 1) * 83492791L);   // bands 0 and 1 share a cell size: same clouds either side of 500
                float kind = Clouds.noise((float) (mx * 0.00045), (float) (mz * 0.00045), 9.1f);
                int type = kind < 0.34f ? STRATO : kind < 0.72f ? CUMULUS : ALTO;
                float nearK = 1f - smooth(400f, 2600f, (float) Math.hypot(mx - qx, mz - qz));   // rain darkens the clouds overhead, not every cloud in the world (that made all of them look stormy while you stood in a storm)
                float stormHere = Mth.clamp(storm0 * nearK + influence(cells, mx - driftX, mz - driftZ) * 0.9f, 0f, 1f);
                float tall = 0.55f + 0.9f * Clouds.noise((float) (mx * 0.0011), (float) (mz * 0.0011), 3.7f);
                float r0 = (float) (cell * (0.50 + 0.35 * c));
                float thick, ratio, y0, spread; int n;
                switch (type) {
                    case STRATO -> { thick = (14f + 34f * c) * taller; ratio = 0.72f; y0 = BASE - 4f; spread = 0.95f; n = 3; }
                    case ALTO -> { thick = (8f + 14f * c) * taller; ratio = 0.62f; y0 = BASE + 170f; spread = 0.55f; n = 4; }
                    default -> { thick = (16f + 96f * tall * c * c) * taller; ratio = 0.78f; y0 = BASE + 4f; spread = 1f; n = 4; }
                }
                if (band >= 1) n = Math.max(2, n * 3 / 5);   // far: fewer puffs per cell
                if (stormHere > 0.5f && type == ALTO) y0 = BASE + 30f;   // under a storm the mid-level deck sinks into the cloud
                // Every cell always has the same puffs at the same places (from its own seed); more cover makes more of them appear and grow, less makes them shrink away.
                // So a cloud changes shape gradually as the weather evolves, and a rebuild only moves things a little.
                for (int k = 0; k < n; k++) {
                    float rj = r.nextFloat(), rx = r.nextFloat(), rz = r.nextFloat(), rs = r.nextFloat(), rh = r.nextFloat(), rseed = r.nextFloat();
                    float thr = 0.05f + 0.50f * k / n, w = smooth(thr, thr + 0.25f, c);
                    if (w < 0.02f) continue;
                    // what cannot be seen is not built (the GPU only shades what shows): close by, from above the layer the lower puffs lie hidden under the tops, from below the tops lie hidden above the bases
                    if (band == 0 && type == CUMULUS && n > 3) {
                        double hd2 = ((cx + 0.5) * cell - qx) * ((cx + 0.5) * cell - qx) + ((cz + 0.5) * cell - qz) * ((cz + 0.5) * cell - qz);
                        float uk = (k + 0.55f) / n;
                        if (buildCamY > BASE + 60 && hd2 < 1400.0 * 1400.0 && uk < 0.42f) continue;
                        if (buildCamY < BASE - 10 && hd2 < 500.0 * 500.0 && uk > 0.72f) continue;
                    }
                    float u = (k + 0.3f + 0.5f * rj) / n;
                    Puff p = new Puff();
                    p.x = (float) ((cx + 0.5 + (rx - 0.5) * 0.9) * cell - qx); p.z = (float) ((cz + 0.5 + (rz - 0.5) * 0.9) * cell - qz);
                    p.y = y0 + u * thick * (0.55f + 0.45f * rh);
                    p.sx = r0 * spread * (float) Math.pow(w, 0.6) * (type == CUMULUS ? 0.62f + 0.55f * u * (1.6f - u) : 0.95f - 0.2f * u * u) * (0.8f + 0.4f * rs); p.sy = p.sx * (u < 0.25f ? ratio * 0.45f : ratio);
                    p.y = Math.max(p.y, y0 + p.sy * 0.85f);   // every puff rests on the cloud base: a flat bottom, nothing hangs below it
                    p.shade = type == CUMULUS ? 0.48f + 0.52f * u : 0.60f + 0.32f * u; p.dark = stormHere * (1f - 0.35f * u); p.seed = rseed; p.alpha = (type == ALTO ? 0.8f : 0.92f) * Math.min(1f, w * 2f) * (1f - smooth((float) (band < NB - 1 ? outer - 1000.0 : outer * 0.82), (float) outer, (float) d)) * (band == 0 ? 1f : smooth((float) inner, (float) (inner + (band == 1 ? 900.0 : 1500.0)), (float) d));
                    p.d2 = (double) p.x * p.x + (double) p.z * p.z + (p.y - buildCamY) * (p.y - buildCamY);
                    puffs.add(p);
                }
            }
        }
        return puffs;
    }

    /** Cirrus: thin high streaks, stretched along the wind, over everything (no storm, no shade). */
    private static void cirrus(List<Puff> puffs, double qx, double qz, float evo) {
        double couter = 14000.0;
        int cx0 = (int) Math.floor((qx - couter) / CIRRUS_CELL), cx1 = (int) Math.floor((qx + couter) / CIRRUS_CELL), cz0 = (int) Math.floor((qz - couter) / CIRRUS_CELL), cz1 = (int) Math.floor((qz + couter) / CIRRUS_CELL);
        for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
            double mx = (cx + 0.5) * CIRRUS_CELL, mz = (cz + 0.5) * CIRRUS_CELL;
            float cv = Clouds.fbm((float) (mx * 0.0016 + 40.0), (float) (mz * 0.0042 + 17.0), evo * 0.4f);   // stretched in z: streaks
            if (cv < 0.52f) continue;
            var r = RandomSource.create(cx * 668265263L ^ cz * 374761393L);
            Puff p = new Puff(); p.x = (float) ((cx + r.nextFloat()) * CIRRUS_CELL - qx); p.z = (float) ((cz + r.nextFloat()) * CIRRUS_CELL - qz);
            p.y = BASE + 700f + r.nextFloat() * 200f; p.sx = (float) (CIRRUS_CELL * (0.45 + 0.5 * r.nextFloat())); p.sy = p.sx * 0.13f;
            p.shade = 1f; p.dark = 0f; p.seed = r.nextFloat(); p.alpha = 0.30f * smooth(0.52f, 0.62f, cv); p.d2 = (double) p.x * p.x + (double) p.z * p.z + (p.y - buildCamY) * (p.y - buildCamY); puffs.add(p);
        }
    }

    private static void puff(List<Puff> out, double x, float y, double z, float sx, float syRatio, float shade, float dark, RandomSource r) {
        Puff p = new Puff(); p.x = (float) x; p.y = y; p.z = (float) z; p.sx = sx; p.sy = sx * syRatio; p.shade = Mth.clamp(shade, 0f, 1f); p.dark = Mth.clamp(dark, 0f, 1f); p.seed = r.nextFloat(); p.alpha = 0.92f;
        p.d2 = (double) p.x * p.x + (double) p.z * p.z + (p.y - buildCamY) * (p.y - buildCamY); out.add(p);
    }

    /** The puffs into a mesh, the farthest first (they are blended over each other). Coordinates are relative to the build's camera; y is absolute. */
    private static Mesh upload(List<Puff> puffs) {
        if (puffs.isEmpty()) return null;
        puffs.sort(Comparator.comparingDouble((Puff p) -> -p.d2));
        Mesh mesh = new Mesh();
        for (int s = 0; s <= SECT; s++) {
            final int sec = s;
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK); boolean any = false;
            for (Puff p : puffs) {
                int ps = Math.hypot(p.x, p.z) < 1200.0 ? SECT : Math.min(SECT - 1, (int) ((Math.atan2(p.x, p.z) + Math.PI) / (2 * Math.PI) * SECT));
                if (ps != sec) continue; any = true;
                for (int k = 0; k < 4; k++) bb.addVertex(p.x, p.y, p.z).setColor(255, 255, 255, Math.round(p.alpha * 255f)).setUv(p.sx, p.sy).setLight(0).setNormal(p.shade * 2f - 1f, p.dark * 2f - 1f, p.seed * 2f - 1f);
            }
            if (!any) continue;
            MeshData md = bb.build(); if (md == null) continue;
            var vb = new VertexBuffer(VertexBuffer.Usage.STATIC); vb.bind(); vb.upload(md); VertexBuffer.unbind(); mesh.parts[sec] = vb;
        }
        return mesh;
    }
    /** Draws a band: the part near the centre always, the wedges only where the camera can see them. */
    private static void drawMesh(Mesh m, double dx, double dz, Matrix4f base, double camY, Matrix4f proj, ShaderInstance sh, float alpha) {
        if (m == null) return;
        draw(m.parts[SECT], dx, dz, base, camY, proj, sh, alpha);
        for (int s = 0; s < SECT; s++) {
            if (m.parts[s] == null) continue;
            if (!allSectors) {
                double c = (s + 0.5) / SECT * 2 * Math.PI - Math.PI, d = Math.abs(((c - camAz + 3 * Math.PI) % (2 * Math.PI)) - Math.PI);
                if (d > halfAz + Math.PI / SECT + 0.1) continue;
            }
            draw(m.parts[s], dx, dz, base, camY, proj, sh, alpha);
        }
    }
}
