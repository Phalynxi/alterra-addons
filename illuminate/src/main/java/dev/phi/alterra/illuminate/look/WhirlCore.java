package dev.phi.alterra.illuminate.look;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.prismlib.api.Funnel;
import dev.phi.prismlib.api.PrismApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.io.IOException;

/**
 * The core of a whirlpool: a spinning funnel of water from the surface down to the bottom, narrowing as it goes (the shape a real free vortex takes: its radius falls off
 * roughly as one over the depth), with spiral streaks winding down it. Seen from above it is a hole you look down into; from under water, a pale spinning column.
 * The water surface itself dips into it (the grid water, WhirlMap); this is the part the surface cannot show (it has a vertex only every block).
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class WhirlCore {
    private WhirlCore() {}
    private static ShaderInstance shader;
    private static final int SEG = 28, RINGS = 26;

    public static void registerShader(RegisterShadersEvent e) {
        try { e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "prism_whirl"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s); }
        catch (IOException ex) { throw new IllegalStateException("Illuminate: the whirlpool shader could not be loaded", ex); }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || shader == null) return;
        var mc = Minecraft.getInstance(); var lv = mc.level; if (lv == null) return;
        var cam = e.getCamera().getPosition();
        float t = PrismApi.clock().seconds();
        boolean any = false;
        for (Funnel f : PrismApi.funnels()) {
            if (f.type() != Funnel.Type.WHIRLPOOL || f.strength() < 0.05f) continue;
            double dxc = f.x() - cam.x, dzc = f.z() - cam.z; if (dxc * dxc + dzc * dzc > 160 * 160) continue;
            // the bottom under the centre
            var p = new BlockPos.MutableBlockPos(Mth.floor(f.x()), Mth.floor(f.y()) - 1, Mth.floor(f.z()));
            int n = 0; while (n < 80 && lv.getFluidState(p).is(FluidTags.WATER)) { p.move(0, -1, 0); n++; }
            double top = f.y() + 0.05, bottom = p.getY() + 1.0, depth = top - bottom;
            if (depth < 1.5) continue;
            if (!any) {
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
                RenderSystem.setShader(() -> shader); any = true;
            }
            float r0 = Math.max(1.2f, f.radius() * 0.42f), spin = f.spin() >= 0 ? 1f : -1f;
            var info = shader.getUniform("Info");
            if (info != null) info.set(t, spin, Mth.clamp((float) Math.cos(lv.getTimeOfDay(1f) * Math.PI * 2) * 2f + 0.5f, 0.15f, 1f), mc.gameRenderer.getMainCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.WATER ? 1f : 0f);
            var bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int a8 = Math.round(Mth.clamp(f.strength(), 0f, 1f) * 255f);
            for (int ri = 0; ri < RINGS; ri++) for (int si = 0; si < SEG; si++) {
                for (int k = 0; k < 4; k++) {
                    int rr = ri + (k >= 2 ? 1 : 0), ss = si + (k == 1 || k == 2 ? 1 : 0);
                    float v = rr / (float) RINGS, h = (float) (v * depth);
                    float rad = r0 / (1f + h / (r0 * 0.9f)) + 0.18f;   // narrows like a real vortex core: about one over the depth, to a thin thread at the bottom
                    double ang = ss / (double) SEG * Math.PI * 2 + spin * (t * 1.4 + h * 0.30);   // the whole funnel turns, and twists as it goes down
                    float ox = (float) Math.cos(ang), oz = (float) Math.sin(ang);
                    double x = f.x() + ox * rad - cam.x, y = top - h - cam.y, z = f.z() + oz * rad - cam.z;
                    bb.addVertex((float) x, (float) y, (float) z).setUv(ss / (float) SEG, v).setColor(Math.round((ox * 0.5f + 0.5f) * 255f), 128, Math.round((oz * 0.5f + 0.5f) * 255f), a8);
                }
            }
            var mesh = bb.build(); if (mesh != null) BufferUploader.drawWithShader(mesh);
        }
        if (any) { RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); }
    }
}
