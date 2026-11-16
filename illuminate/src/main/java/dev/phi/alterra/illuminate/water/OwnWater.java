package dev.phi.alterra.illuminate.water;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.alterra.illuminate.maps.FlowMap;
import dev.phi.prismlib.api.PrismApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.io.IOException;

/**
 * Our own water surface: a fixed grid around the camera (two points per block) that the GPU lifts to the smooth height of the water (FlowMap's height map, read with
 * a Catmull-Rom curve, so slopes curve instead of stepping), with a soft shore where the water ends. The CPU only keeps the height map; nothing is rebuilt per frame.
 * The look: the real slope of the surface with ripples that follow the wind, the sky mirrored by Fresnel, the sun's glint, and the colour by how deep the water is.
 * Step 1 of the own water (the falls as curved sheets and hiding the game's own water come next); off unless OWN_WATER is set (-Dalterra.ownwater=true) while it is built.
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class OwnWater {
    private OwnWater() {}
    public static final boolean ON = Boolean.getBoolean("alterra.ownwater");
    /** Is our surface drawing the water around the camera now (then the game's own water is not drawn there)? */
    public static boolean drawing() { var mc = Minecraft.getInstance(); return ON && shader != null && FlowMap.live && mc.level != null && mc.gameRenderer.getMainCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.WATER; }
    private static boolean warned; private static long logAt;
    private static ShaderInstance shader, fallShader; private static VertexBuffer grid; private static int depthTex, depthFbo, dw, dh;

    public static void registerShader(RegisterShadersEvent e) {
        try { e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "prism_water"), DefaultVertexFormat.POSITION), s -> shader = s);
              e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "prism_fall"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> fallShader = s); }
        catch (IOException ex) { throw new IllegalStateException("Illuminate: the water surface shader could not be loaded", ex); }
    }

    private static void makeGrid() {
        int n = FlowMap.SIZE * 2;   // in half blocks
        int inner0 = n / 4, inner1 = n - n / 4;   // the middle half (64 blocks across): two points per block; outside: one point every two blocks
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            boolean in = x >= inner0 && x < inner1 && z >= inner0 && z < inner1;
            if (in) { bb.addVertex(x, 0f, z + 1); bb.addVertex(x + 1, 0f, z + 1); bb.addVertex(x + 1, 0f, z); bb.addVertex(x, 0f, z); continue; }
            if ((x & 3) != 0 || (z & 3) != 0) continue;   // coarse cells start every 4 half blocks
            int x2 = x + 4, z2 = z + 4;
            // (a coarse cell that touches the fine middle is cut to it, so the two meet on a shared edge)
            bb.addVertex(x, 0f, z2); bb.addVertex(x2, 0f, z2); bb.addVertex(x2, 0f, z); bb.addVertex(x, 0f, z);
        }
        grid = new VertexBuffer(VertexBuffer.Usage.STATIC); grid.bind(); grid.upload(bb.buildOrThrow()); VertexBuffer.unbind();
    }

    /** The scene's depth (without the water) into a texture of our own, to tell how deep the water is. */
    private static void copyDepth(com.mojang.blaze3d.pipeline.RenderTarget main) {
        if (depthTex == 0 || dw != main.width || dh != main.height) {
            if (depthTex == 0) { depthTex = GL11C.glGenTextures(); depthFbo = GL30C.glGenFramebuffers(); }
            dw = main.width; dh = main.height;
            GlStateManager._bindTexture(depthTex);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_DEPTH_COMPONENT, dw, dh, 0, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (java.nio.FloatBuffer) null);   // the same (unsized) format as the game's own depth: a depth copy only works between matching formats
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST); GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GlStateManager._bindTexture(0);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, depthFbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, depthTex, 0);
            GL11C.glDrawBuffer(GL11C.GL_NONE); GL11C.glReadBuffer(GL11C.GL_NONE);
        }
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, depthFbo);
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {}
        GL30C.glBlitFramebuffer(0, 0, dw, dh, 0, 0, dw, dh, GL11C.GL_DEPTH_BUFFER_BIT, GL11C.GL_NEAREST);
        int err = GL11C.glGetError();
        if (err != GL11C.GL_NO_ERROR && !warned) { warned = true; org.slf4j.LoggerFactory.getLogger("Illuminate").warn("Illuminate: copying the scene depth for the water failed (GL error {}): the water cannot tell how deep it is", err); }
        main.bindWrite(false);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (!ON || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || shader == null || !FlowMap.live || FlowMap.heightTexture() == 0) return;
        var mc = Minecraft.getInstance(); var lv = mc.level; if (lv == null) return;
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER) return;   // (from below: later)
        if (grid == null) makeGrid();
        var cam = e.getCamera().getPosition(); var main = mc.getMainRenderTarget();
        copyDepth(main);
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double a = lv.getTimeOfDay(pt) * Math.PI * 2; float sx = (float) -Math.sin(a), sy = (float) Math.cos(a);
        float day = Mth.clamp(sy * 2f + 0.3f, 0.12f, 1f);
        var sky = lv.getSkyColor(cam, pt); var w = PrismApi.wind().at(lv, cam.x, cam.z);
        int tint = net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(lv, net.minecraft.core.BlockPos.containing(cam));
        set4("Grid", (float) (FlowMap.originX - cam.x), (float) (FlowMap.originZ - cam.z), FlowMap.SIZE, 0f);
        set4("Cam", (float) (cam.x % 4096.0), (float) cam.y, (float) (cam.z % 4096.0), (float) (FlowMap.surfaceBaseY - cam.y));
        set4("SunW", sy > 0 ? sx : -sx, Math.abs(sy), 0f, day);
        set3("SkyH", (float) Math.min(1, sky.x * 0.8 + 0.18), (float) Math.min(1, sky.y * 0.8 + 0.2), (float) Math.min(1, sky.z * 0.8 + 0.22));
        set3("SkyZ", (float) sky.x, (float) sky.y, (float) sky.z);
        set3("Tint", ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f);
        set4("Wave", PrismApi.clock().seconds(), (float) w.x, (float) w.z, Math.min(1f, (float) w.length()));
        set4("Screen", main.width, main.height, 0.05f, mc.gameRenderer.getDepthFar());
        shader.setSampler("Heights", FlowMap.heightTexture()); shader.setSampler("SceneDepth", depthTex);
        float[] wh = dev.phi.alterra.illuminate.maps.WhirlMap.INFO;
        set4("WhirlInfo", wh[0], wh[1], wh[2], wh[3] > 0.5f && dev.phi.alterra.illuminate.maps.WhirlMap.texture() != 0 ? 1f : 0f);
        shader.setSampler("Whirl", dev.phi.alterra.illuminate.maps.WhirlMap.texture() != 0 ? dev.phi.alterra.illuminate.maps.WhirlMap.texture() : FlowMap.heightTexture());
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableCull();
        grid.bind(); grid.drawWithShader(e.getModelViewMatrix(), e.getProjectionMatrix(), shader); VertexBuffer.unbind();
        if (Boolean.getBoolean("alterra.photos") && System.currentTimeMillis() - logAt > 5000) { logAt = System.currentTimeMillis(); org.slf4j.LoggerFactory.getLogger("Illuminate").info("[hunt] own water drawn: grid at {} {}, base {}, falls {}", FlowMap.originX, FlowMap.originZ, FlowMap.surfaceBaseY, FlowMap.fallCount); }
        drawFalls(cam, day, tint);
        RenderSystem.enableCull(); RenderSystem.disableBlend();
    }

    /** The falls: a sheet from each lip, leaving it on a ballistic curve (out as it drops), streaked, with foam at the foot. Built each frame (a few dozen quads at most). */
    private static void drawFalls(net.minecraft.world.phys.Vec3 cam, float day, int tint) {
        float[] f = FlowMap.falls; int n = Math.min(FlowMap.fallCount, f.length / 6); if (n == 0 || fallShader == null) return;
        var bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        final int SEG = 8;
        for (int k = 0; k < n; k++) {
            int o = k * 6; float bx = f[o], bz = f[o + 1], dx = f[o + 2], dz = f[o + 3], top = f[o + 4], bot = f[o + 5], drop = top - bot;
            // the lip: the edge of block (bx, bz) on the side the water leaves; across it, from one corner to the other
            float cx = bx + 0.5f + dx * 0.5f, cz = bz + 0.5f + dz * 0.5f, ax = dz != 0 ? 1f : 0f, az = dx != 0 ? 1f : 0f;
            float reach = Math.min(1.6f, 0.35f + (float) Math.sqrt(drop) * 0.35f);   // how far out it is thrown by the time it lands
            for (int sgi = 0; sgi < SEG; sgi++) for (int q = 0; q < 4; q++) {
                int ss = sgi + (q >= 2 ? 1 : 0); float side = (q == 1 || q == 2) ? 0.5f : -0.5f, v = ss / (float) SEG;
                float out = reach * (float) Math.sqrt(v) * 0.9f + 0.05f, y = top - drop * v * v * 0.35f - drop * v * 0.65f;   // out fast at first, then mostly straight down
                double px = cx + dx * out + ax * side - cam.x, pz = cz + dz * out + az * side - cam.z;
                bb.addVertex((float) px, (float) (y - cam.y), (float) pz).setUv((ax != 0 ? bx : bz) + side + 0.5f, v).setColor(255, 255, 255, 230);
            }
        }
        var mesh = bb.build(); if (mesh == null) return;
        var info = fallShader.getUniform("Info"); if (info != null) info.set(PrismApi.clock().seconds(), day, 0f, 0f);
        var tu = fallShader.getUniform("Tint"); if (tu != null) tu.set(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f);
        RenderSystem.setShader(() -> fallShader); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
    }

    private static void set4(String n, float a, float b, float c, float d) { var u = shader.getUniform(n); if (u != null) u.set(a, b, c, d); }
    private static void set3(String n, float a, float b, float c) { var u = shader.getUniform(n); if (u != null) u.set(a, b, c); }
}
