package dev.phi.alterra.illuminate.water;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.prismlib.api.PrismApi;
import dev.phi.prismlib.api.WaterSurface;
import dev.phi.prismlib.grid.ShallowWater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;

/**
 * Water that is simulated on a grid of its own (a whirlpool today) drawn as its own surface: a mesh over the grid's cells, lifted and lowered by the simulation, lit by the slope of the
 * surface, mirroring the sky more and more at a low angle, with foam where the water is steep or piles up. The water blocks' own faces are not drawn inside the disc it stands in for
 * (the block shader drops them there: see {@link #discs}).
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class GridWater {
    private GridWater() {}
    private static ShaderInstance shader;
    private static final float[] SLOPE = new float[2];

    public static void registerShader(RegisterShadersEvent e) {
        try { e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "prism_water"), DefaultVertexFormat.BLOCK), s -> shader = s); }
        catch (IOException ex) { throw new IllegalStateException("Illuminate: the grid water shader could not be loaded", ex); }
    }

    /** The discs the block shader must not draw water in, for the shader's whirlpool uniforms: x and z relative to the camera, radius, 0. Returns how many (at most four). */
    public static int discs(float[] out, Vec3 cam) {
        java.util.Arrays.fill(out, 0f); int n = 0;
        for (WaterSurface s : PrismApi.waterSurfaces()) {
            if (n >= 4) break; ShallowWater g = s.grid();
            out[n * 4] = (float) (g.ox + g.n * g.cell / 2.0 - cam.x); out[n * 4 + 1] = (float) (g.oz + g.n * g.cell / 2.0 - cam.z); out[n * 4 + 2] = s.radius(); out[n * 4 + 3] = 0f; n++;
        }
        return n;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || shader == null) return;
        List<WaterSurface> list = PrismApi.waterSurfaces(); if (list.isEmpty()) return;
        var mc = Minecraft.getInstance(); var level = mc.level; if (level == null) return;
        var cam = e.getCamera().getPosition(); float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double ang = level.getTimeOfDay(pt) * Math.PI * 2; var sky = level.getSkyColor(cam, pt);
        int wc = level.getBiome(BlockPos.containing(cam)).value().getWaterColor();
        float wr = ((wc >> 16 & 255) / 255f), wg = ((wc >> 8 & 255) / 255f), wb = ((wc & 255) / 255f);
        float[] tint = {0.22f + 0.2f * wr, 0.62f + 0.12f * wg, 0.72f + 0.1f * wb};   // clear and turquoise (the biome tints it a little): the look of the rest of the water
        set(shader, "SunDir", (float) -Math.sin(ang), (float) Math.cos(ang), 0f);
        set(shader, "Zenith", (float) sky.x, (float) sky.y, (float) sky.z);
        set(shader, "Horizon", (float) Math.min(1, sky.x * 0.8 + 0.16), (float) Math.min(1, sky.y * 0.8 + 0.18), (float) Math.min(1, sky.z * 0.8 + 0.2));
        set(shader, "WaterColor", tint[0], tint[1], tint[2]);
        time = (level.getGameTime() + pt) / 20.0;
        var tu = shader.getUniform("Time"); if (tu != null) tu.set((float) time);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        var stack = RenderSystem.getModelViewStack(); stack.pushMatrix(); stack.set(new Matrix4f(e.getModelViewMatrix())); RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(() -> shader);
        for (WaterSurface s : list) draw(s, cam, level);
        stack.popMatrix(); RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.disableBlend();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
    }

    private static void set(ShaderInstance s, String name, float a, float b, float c) { var u = s.getUniform(name); if (u != null) u.set(a, b, c); }

    private static void draw(WaterSurface s, Vec3 cam, net.minecraft.client.multiplayer.ClientLevel level) {
        ShallowWater g = s.grid(); int n = g.n; double cx = g.ox + n * g.cell / 2.0, cz = g.oz + n * g.cell / 2.0, rad = s.radius() + 1.2;
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(cx, s.y() + 1, cz));
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        int lo = Math.max(1, (int) Math.floor((cx - rad - g.ox) / g.cell)), hi = Math.min(n - 3, (int) Math.ceil((cx + rad - g.ox) / g.cell));
        int lj = Math.max(1, (int) Math.floor((cz - rad - g.oz) / g.cell)), hj = Math.min(n - 3, (int) Math.ceil((cz + rad - g.oz) / g.cell));
        boolean any = false;
        for (int j = lj; j <= hj; j++) for (int i = lo; i <= hi; i++) {
            if (Math.hypot(g.worldX(i) - cx, g.worldZ(j) - cz) > rad || g.solid[i + j * n]) continue;
            int[] di = {0, 1, 1, 0}, dj = {0, 0, 1, 1};
            for (int k = 0; k < 4; k++) any |= vertex(bb, g, s, i + di[k], j + dj[k], cx, cz, rad, cam, light);
        }
        if (any) BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /** One corner of the surface: a cell centre of the grid, lifted by its height, its normal from the slope, foam from the steepness and from water piling up. */
    private static double time;
    private static boolean vertex(BufferBuilder bb, ShallowWater g, WaterSurface s, int i, int j, double cx, double cz, double rad, Vec3 cam, int light) {
        int c = i + j * g.n; double x = g.worldX(i), z = g.worldZ(j);
        float eta = g.eta[c], sx = (g.eta[c + 1] - g.eta[c - 1]) / (2f * g.cell), sz = (g.eta[c + g.n] - g.eta[c - g.n]) / (2f * g.cell);
        float conv = -((g.u[c + 1] - g.u[c - 1]) + (g.w[c + g.n] - g.w[c - g.n])) / (2f * g.cell);   // water piling up (flow converging)
        float steep = (float) Math.hypot(sx, sz), speed = (float) Math.min(1.0, Math.hypot(g.u[c], g.w[c]) / 8.0);
        float foam = Math.max(0f, Math.min(1f, (steep - 0.22f) * 2.2f + conv * 0.08f + (steep > 0.05f ? 0.05f * speed : 0f)));
        float len = (float) Math.sqrt(sx * sx + 1f + sz * sz);
        double d = Math.hypot(x - cx, z - cz); float edge = (float) Math.max(0.0, Math.min(1.0, (rad - d) / 1.2));
        // the foam's pattern is wound into a spiral by turning it faster nearer the middle (the water's own turning rate there), and turns slowly as a whole
        double rx = x - cx, rz = z - cz, r = Math.max(0.5, Math.hypot(rx, rz)), vt = (-rz * g.u[c] + rx * g.w[c]) / r, omega = vt / r;
        double th = omega * 1.2 + Math.signum(vt) * 0.25 * time, cs = Math.cos(-th), sn = Math.sin(-th);
        float uu = (float) (cx + rx * cs - rz * sn), vv = (float) (cz + rx * sn + rz * cs);
        bb.addVertex((float) (x - cam.x), (float) (s.y() + eta - cam.y), (float) (z - cam.z))
                .setColor((int) (foam * 255), (int) (speed * 255), 255, (int) (edge * 255)).setUv((float) (uu % 1024.0), (float) (vv % 1024.0)).setLight(light).setNormal(-sx / len, 1f / len, -sz / len);
        return true;
    }
}
