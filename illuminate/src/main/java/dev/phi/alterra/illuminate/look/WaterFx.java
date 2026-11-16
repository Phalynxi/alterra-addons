package dev.phi.alterra.illuminate.look;

import dev.phi.prismlib.api.PrismApi;
import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.alterra.illuminate.IlluminateConfig;
import dev.phi.alterra.illuminate.maps.FlowMap;
import dev.phi.alterra.illuminate.maps.SceneCopy;
import dev.phi.alterra.illuminate.maps.SeaState;
import dev.phi.alterra.illuminate.maps.ShoreMap;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The look of water and lava: rippling light (caustics) on the bottom of shallow water, a lace of foam along the shore, and a flickering glow on lava and the ground
 * beside it. Drawn as a few thousand flat, textured squares over the water and the ground, from a list of the places around the player that is rebuilt a few times a second.
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class WaterFx {
    private WaterFx() {}
    private static final ResourceLocation CAUSTICS = ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "textures/environment/caustics.png"),
            FOAM = ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "textures/environment/foam_strip.png"), GLOW = ResourceLocation.fromNamespaceAndPath(Illuminate.ID, "textures/environment/glow.png");
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private record Spot(int x, int z, double y, float strength, int dx, int dz) {}   // dx, dz: for foam, the way to the land
    private record Foam(int x, int z, double y, double floor, int bits) {}   // a water cell next to the shore: which of its 8 neighbours are land (bit 0 west, 1 east, 2 north, 3 south, 4 NW, 5 NE, 6 SW, 7 SE)
    private static final List<Foam> foams = new ArrayList<>();
    private static final List<Spot> floor = new ArrayList<>(), shore = new ArrayList<>(), lava = new ArrayList<>();
    private static long builtAt = -100;
    private static final int RADIUS = 22;

    private static void rebuild(net.minecraft.client.multiplayer.ClientLevel level, BlockPos centre) {
        List<Spot> f = new ArrayList<>(), sh = new ArrayList<>(), lv = new ArrayList<>();
        java.util.Map<Long, Double> landTop = new java.util.HashMap<>(); List<double[]> waters = new ArrayList<>();
        var pos = new BlockPos.MutableBlockPos(); var n = new BlockPos.MutableBlockPos();
        boolean caustics = IlluminateConfig.get(IlluminateConfig.CAUSTICS), foam = IlluminateConfig.get(IlluminateConfig.FOAM), glow = IlluminateConfig.get(IlluminateConfig.LAVA_GLOW);
        for (int x = centre.getX() - RADIUS; x <= centre.getX() + RADIUS; x++) for (int z = centre.getZ() - RADIUS; z <= centre.getZ() + RADIUS; z++) {
            if ((x - centre.getX()) * (x - centre.getX()) + (z - centre.getZ()) * (z - centre.getZ()) > RADIUS * RADIUS || !level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            pos.set(x, y, z);
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER)) {
                double surface = y + fluid.getHeight(level, pos);
                if (foam) {   // the floor under this column, for the foam that goes down through the water
                    int ffy = y - 1;
                    while (ffy > y - 6 && level.getFluidState(n.set(x, ffy, z)).is(FluidTags.WATER)) ffy--;
                    waters.add(new double[] {x, z, surface, ffy + 1.0});
                }
                if (caustics) {   // the bottom under shallow water, lit by the sky
                    int fy = y - 1;
                    while (fy > y - 12 && level.getFluidState(n.set(x, fy, z)).is(FluidTags.WATER)) fy--;
                    int depth = y - fy;
                    if (depth <= 10 && !level.getBlockState(n.set(x, fy, z)).getCollisionShape(level, n).isEmpty()) {
                        int sky = level.getBrightness(LightLayer.SKY, n.set(x, fy + 1, z));
                        f.add(new Spot(x, z, fy + 1.0, (1f - depth / 11f) * (sky / 15f), 0, 0));
                    }
                }
                if (false) for (Direction d : Direction.Plane.HORIZONTAL) {   // land next to the water, about as high as it
                    n.set(x + d.getStepX(), y, z + d.getStepZ());
                    if (level.getFluidState(n).is(FluidTags.WATER)) continue;
                    boolean land = !level.getBlockState(n).getCollisionShape(level, n).isEmpty() || level.getBlockState(n).isAir() && !level.getBlockState(n.below()).getCollisionShape(level, n.below()).isEmpty();
                    if (land) sh.add(new Spot(x, z, surface + 0.02, 1f, d.getStepX(), d.getStepZ()));
                }
            } else if (foam && !fluid.is(FluidTags.LAVA) && !level.getBlockState(pos).isAir()) {
                landTop.put(((long) x << 32) ^ (z & 0xffffffffL), y + 1.0);
            } else if (glow && fluid.is(FluidTags.LAVA)) {
                lv.add(new Spot(x, z, y + fluid.getHeight(level, pos) + 0.03, 1f, 0, 0));
            }
        }
        List<Foam> fm = new ArrayList<>();
        int[][] around = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        if (foam) for (double[] w : waters) {   // which neighbours are land at about the water's height: the shader draws a thin line along those edges
            int x = (int) w[0], z = (int) w[1], bits = 0;
            for (int i = 0; i < 8; i++) {
                Double top = landTop.get((((long) (x + around[i][0])) << 32) ^ ((z + around[i][1]) & 0xffffffffL));
                if (top != null && Math.abs(top - w[2]) <= 1.3) bits |= 1 << i;
            }
            if (bits != 0) fm.add(new Foam(x, z, w[2], w[3], bits));
        }
        foams.clear(); foams.addAll(fm);
        byte[] cells = new byte[ShoreMap.SIZE * ShoreMap.SIZE]; int ox = centre.getX() - ShoreMap.SIZE / 2, oz = centre.getZ() - ShoreMap.SIZE / 2;   // the shore map the water shader reads
        for (Foam fo : fm) { int cx = fo.x() - ox, cz = fo.z() - oz; if (cx >= 0 && cz >= 0 && cx < ShoreMap.SIZE && cz < ShoreMap.SIZE) cells[cx + cz * ShoreMap.SIZE] = (byte) fo.bits(); }
        ShoreMap.set(cells, ox, oz);
        floor.clear(); floor.addAll(f); shore.clear(); shore.addAll(sh); lava.clear(); lava.addAll(lv);
    }

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static boolean complained; private static long seaBuiltAt = -100;

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) { if (IlluminateConfig.get(IlluminateConfig.WATER_LOOK)) { SceneCopy.pending = true; SceneCopy.remember(e.getModelViewMatrix(), e.getProjectionMatrix(), e.getCamera().getPosition()); } return; }   // everything but the water (it is drawn next), for the water shader to read
        boolean before = e.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;   // caustics and lava: the water is drawn after them, over them
        if (!before && e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance(); var level = mc.level; var player = mc.player;
        if (level == null || player == null) return;
        try { render(e, mc, level, player, before); }
        catch (Throwable t) { if (!complained) { complained = true; LOG.warn("Alterra Weather: water effects failed", t); } }
    }

    private static long surgeAt = -1;

    private static void render(RenderLevelStageEvent e, Minecraft mc, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer player, boolean before) {
        long now = level.getGameTime();
        if (now - builtAt >= 10 || now < builtAt) {
            builtAt = now; rebuild(level, player.blockPosition());
            if ((now - seaBuiltAt >= 40 || now < seaBuiltAt) && Foliage.shaderWater()) { seaBuiltAt = now; SeaState.update(level, player.blockPosition()); }   // how open the sea is around here
            if (now % 200 < 10) LOG.info("Alterra Weather: water effects see {} spots of bottom, {} of shore, {} of lava", floor.size(), shore.size(), lava.size());
        }
        if (now != surgeAt && Foliage.shaderWater()) { surgeAt = now; SeaState.refreshSurge(); }   // the storm surge changes from tick to tick
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        var cam = e.getCamera().getPosition();
        if (before && Foliage.shaderWater() && IlluminateConfig.get(IlluminateConfig.SMOOTH_WATER)) FlowMap.upload(cam.x, cam.z);   // water easing between levels
        float t = (now + pt) / 20f, rain = level.getRainLevel(pt);
        float day = Math.max(0.12f, Math.max(0f, Mth.cos(level.getTimeOfDay(pt) * Mth.TWO_PI)) * (1f - 0.75f * rain));   // sun high: 1; at night a faint moonlight
        float strength = IlluminateConfig.get(IlluminateConfig.CAUSTIC_STRENGTH).floatValue();
        var wind = PrismApi.wind().at(level, cam.x, cam.z);
        // the vertices are given relative to the camera, and the view (its turning) is put on the model-view matrix here, whatever was on it before
        var mv = RenderSystem.getModelViewStack();
        mv.pushMatrix(); mv.identity(); mv.mul(e.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.enableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enablePolygonOffset(); RenderSystem.polygonOffset(-1f, -2f);
            if (before) {
                RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);   // adds light
                // (the old pale light on the bottom of shallow water is gone: the water shader now colours the water by its depth)
                // (the old flat caustics texture is gone: caustics come only from the water shader, the same soft light above and below the water)
                if (!lava.isEmpty()) halos(lava, cam, t, strength);
            } else {
                RenderSystem.defaultBlendFunc();
            }
        } finally {
            RenderSystem.disablePolygonOffset(); RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc();
            mv.popMatrix(); RenderSystem.applyModelViewMatrix();
        }
    }

    /** The shallows are lighter: a pale blue light on the bottom, strongest where the water is thinnest and the sky reaches it, fading smoothly into the deep. */
    private static void shallows(List<Spot> spots) {
        if (cornersFor != builtAt) { buildCorners(spots); cornersFor = builtAt; }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (Spot s : spots) {
            float k = 0.34f;
            float a00 = cornerStrength(spots, s.x, s.z) * k, a10 = cornerStrength(spots, s.x + 1, s.z) * k, a11 = cornerStrength(spots, s.x + 1, s.z + 1) * k, a01 = cornerStrength(spots, s.x, s.z + 1) * k;
            float x0 = (float) (s.x - cam.x), x1 = x0 + 1f, z0 = (float) (s.z - cam.z), z1 = z0 + 1f, y = (float) (s.y - cam.y);
            buf.addVertex(x0, y, z1).setColor(0.30f, 0.62f, 0.85f, a01);
            buf.addVertex(x1, y, z1).setColor(0.30f, 0.62f, 0.85f, a11);
            buf.addVertex(x1, y, z0).setColor(0.30f, 0.62f, 0.85f, a10);
            buf.addVertex(x0, y, z0).setColor(0.30f, 0.62f, 0.85f, a00);
        }
        var mesh = buf.build(); if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static final java.util.Map<Long, Float> CORNER = new java.util.HashMap<>();
    private static float cornerStrength(List<Spot> spots, int cx, int cz) {   // average of the four spots around a corner (missing ones count as 0), so the light fades smoothly and does not step block by block
        return CORNER.getOrDefault(((long) cx << 32) ^ (cz & 0xffffffffL), 0f);
    }
    private static long cornersFor = -1;
    private static void buildCorners(List<Spot> spots) {
        CORNER.clear();
        java.util.Map<Long, Float> at = new java.util.HashMap<>();
        for (Spot s : spots) at.put(((long) s.x << 32) ^ (s.z & 0xffffffffL), s.strength);
        for (Spot s : spots) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
            long key = ((long) (s.x + dx) << 32) ^ ((s.z + dz) & 0xffffffffL);
            if (CORNER.containsKey(key)) continue;
            float sum = 0;
            for (int ox = -1; ox <= 0; ox++) for (int oz = -1; oz <= 0; oz++) sum += at.getOrDefault(((long) (s.x + dx + ox) << 32) ^ ((s.z + dz + oz) & 0xffffffffL), 0f);
            CORNER.put(key, sum / 4f);
        }
    }

    private static void layers(ResourceLocation tex, List<Spot> spots, net.minecraft.world.phys.Vec3 cam, float t, float base, net.minecraft.world.phys.Vec3 wind) {
        RenderSystem.setShaderTexture(0, tex);
        var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (spots == floor && cornersFor != builtAt) { buildCorners(spots); cornersFor = builtAt; }
        for (int layer = 0; layer < 2; layer++) {
            float scale = layer == 0 ? 0.095f : 0.14f, speed = layer == 0 ? 0.03f : -0.022f;
            float ox = (float) (t * speed + wind.x * t * 0.02), oz = (float) (t * speed * 0.8 + wind.z * t * 0.02);
            for (Spot s : spots) {
                float fl = 0.85f + 0.15f * Mth.sin(t * 2.1f + s.x * 0.7f + s.z * 0.5f);
                float a00 = Mth.clamp(base * cornerStrength(spots, s.x, s.z) * fl, 0f, 1f), a10 = Mth.clamp(base * cornerStrength(spots, s.x + 1, s.z) * fl, 0f, 1f),
                      a11 = Mth.clamp(base * cornerStrength(spots, s.x + 1, s.z + 1) * fl, 0f, 1f), a01 = Mth.clamp(base * cornerStrength(spots, s.x, s.z + 1) * fl, 0f, 1f);
                if (a00 + a10 + a11 + a01 < 0.01f) continue;
                float x0 = (float) (s.x - cam.x), x1 = x0 + 1f, z0 = (float) (s.z - cam.z), z1 = z0 + 1f, y = (float) (s.y - cam.y);
                float u0 = s.x * scale + ox, u1 = (s.x + 1) * scale + ox, v0 = s.z * scale + oz, v1 = (s.z + 1) * scale + oz;
                buf.addVertex(x0, y, z1).setUv(u0, v1).setColor(1f, 1f, 1f, a01);
                buf.addVertex(x1, y, z1).setUv(u1, v1).setColor(1f, 1f, 1f, a11);
                buf.addVertex(x1, y, z0).setUv(u1, v0).setColor(1f, 1f, 1f, a10);
                buf.addVertex(x0, y, z0).setUv(u0, v0).setColor(1f, 1f, 1f, a00);
            }
        }
        var mesh = buf.build(); if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /** Lava glows: its own surface brightens and dims slowly, block by block, with no shapes around it. */
    private static void halos(List<Spot> spots, net.minecraft.world.phys.Vec3 cam, float t, float strength) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Spot s : spots) {
            float flicker = 0.5f + 0.5f * Mth.sin(t * 1.4f + s.x * 0.9f + s.z * 1.3f) * Mth.sin(t * 0.8f - s.x * 0.6f + s.z * 0.7f);
            float a = Mth.clamp((0.05f + 0.13f * flicker) * strength, 0f, 1f);
            float x0 = (float) (s.x - cam.x), x1 = x0 + 1f, z0 = (float) (s.z - cam.z), z1 = z0 + 1f, y = (float) (s.y - cam.y);
            buf.addVertex(x0, y, z1).setColor(1f, 0.62f, 0.2f, a);
            buf.addVertex(x1, y, z1).setColor(1f, 0.62f, 0.2f, a);
            buf.addVertex(x1, y, z0).setColor(1f, 0.62f, 0.2f, a);
            buf.addVertex(x0, y, z0).setColor(1f, 0.62f, 0.2f, a);
        }
        var mesh = buf.build(); if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}
