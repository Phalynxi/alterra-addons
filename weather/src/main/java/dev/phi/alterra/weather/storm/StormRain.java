package dev.phi.alterra.weather.storm;

import dev.phi.aethryon.api.Look;
import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;
import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.alterra.weather.config.WeatherConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.phi.aethryon.api.AethryonApi;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Rain and snow drawn by Alterra: the same columns around the camera as vanilla's, but leaning with the wind (further down, further downwind),
 * faster and thicker in a heavy storm, with splashes on the ground. Replaces the overworld's weather rendering; with the setting off, vanilla's is used.
 */
public final class StormRain extends DimensionSpecialEffects.OverworldEffects {
    // rain: our own texture (thin pale streaks, mostly see-through, tools/weather_art.py); snow stays vanilla
    private static final int CLOUD_BASE = 250;   // where the clouds begin (the same height PostFx draws them at)
    private static final ResourceLocation RAIN = ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "textures/environment/rain.png"), SNOW = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float pt, com.mojang.blaze3d.vertex.PoseStack ps, double camX, double camY, double camZ, org.joml.Matrix4f modelView, org.joml.Matrix4f proj) {
        return Look.ownClouds.getAsBoolean();   // ours are drawn instead, as a volume (PostFx)
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float pt, LightTexture lightTexture, double camX, double camY, double camZ) {
        if (!WeatherConfig.get(WeatherConfig.REPLACE_RAIN)) return false;
        float rain = HeavyStorm.rainAt(level, camX, camZ);   // vanilla rain or a storm cell overhead
        if (rain <= 0f) return true;
        float heavy = HeavyStorm.at(level, camX, camZ);
        lightTexture.turnOnLightLayer();
        Vec3 wind = AethryonApi.wind().at(level, camX, camZ);
        float slant = (float) ((0.10 + 0.50 * wind.length()) * WeatherConfig.get(WeatherConfig.RAIN_SLANT));
        double sx = wind.length() > 1e-3 ? wind.x / wind.length() * slant : 0, sz = wind.length() > 1e-3 ? wind.z / wind.length() * slant : 0;   // sideways per block fallen
        int radius = (Minecraft.useFancyGraphics() ? 10 : 5) + (heavy > 0.4f ? 3 : 0);
        int cx = Mth.floor(camX), cy = Mth.floor(camY), cz = Mth.floor(camZ);
        RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency()); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        var pos = new BlockPos.MutableBlockPos();
        BufferBuilder bb = null; ResourceLocation current = null;
        float time = ticks + pt;
        for (int z = cz - radius; z <= cz + radius; z++) for (int x = cx - radius; x <= cx + radius; x++) {
            Biome.Precipitation pr = level.getBiome(pos.set(x, cy, z)).value().getPrecipitationAt(pos);
            if (pr == Biome.Precipitation.NONE) continue;
            if (HeavyConfig.get(HeavyConfig.CLOUD_RAIN) && !CloudMap.rainsHere(level, x + 0.5, z + 0.5)) continue;   // it only rains under a thick cloud
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int y0 = Math.max(cy - radius, ground), y1 = Math.min(Math.max(cy + radius, ground), CLOUD_BASE);   // rain only falls below the clouds: above them there is none
            if (y0 >= y1) continue;
            if (y0 == y1) continue;
            ResourceLocation tex = pr == Biome.Precipitation.RAIN ? RAIN : SNOW;
            if (tex != current) {
                flush(bb);
                RenderSystem.setShaderTexture(0, tex);
                bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                current = tex;
            }
            RandomSource r = RandomSource.create((long) x * x * 3121 + x * 45238971L ^ (long) z * z * 418711 + z * 13761L);
            double dx = x + 0.5 - camX, dz = z + 0.5 - camZ, dist = Math.sqrt(dx * dx + dz * dz);
            float fade = ((1f - (float) (dist * dist) / (radius * radius)) * 0.6f + 0.4f) * rain;
            if (fade <= 0.02f) continue;
            int light = LevelRenderer.getLightColor(level, pos.set(x, (y0 + y1) / 2, z));
            float speed = pr == Biome.Precipitation.RAIN ? 0.07f * (1f + 0.8f * heavy) : 0.012f;
            float scroll = (time * speed * (1f + r.nextFloat() * 0.4f)) + r.nextFloat();   // the picture runs down the column
            float u0 = r.nextFloat() * 0.5f, alpha = Math.min(1f, fade * (pr == Biome.Precipitation.RAIN ? 0.85f + 0.25f * heavy : 0.7f));
            double px = dist > 1e-4 ? dz / dist * 0.5 : 0.5, pz = dist > 1e-4 ? -dx / dist * 0.5 : 0;   // across the view, so a column always faces you
            int passes = heavy > 0.5f && pr == Biome.Precipitation.RAIN && (x + z) % 2 == 0 ? 2 : 1;   // the second layer only on every other column
            for (int pass = 0; pass < passes; pass++) {
                double ox = pass == 0 ? px : px * 0.6, oz = pass == 0 ? pz : pz * 0.6;
                double fx = x + 0.5 - camX + (pass == 1 ? 0.35 : 0), fz = z + 0.5 - camZ + (pass == 1 ? -0.35 : 0);
                double topY = y1 - camY, botY = y0 - camY;
                float v0 = y0 * 0.25f + scroll, v1 = y1 * 0.25f + scroll;
                float cl = Math.min(1f, 0.85f + 0.15f * (1f - heavy));   // a heavy storm is a little darker
                boolean wet = pr == Biome.Precipitation.RAIN;
                float cr = wet ? cl * 0.80f : cl, cg = wet ? cl * 0.92f : cl, cb = cl;   // rain is water: a faint sea blue, and see-through
                float a = wet ? alpha * (0.42f + 0.12f * r.nextFloat()) * (1f - 0.35f * heavy) : alpha;   // rain stays see-through, and thinner in a thunderstorm (it was a wall of lines)
                float aTop = wet ? a * 0.12f : a;   // each column fades out towards its top, so the sky is not filled with streaks
                // the foot is further downwind than the top: lean by how far the column falls
                double lean = y1 - y0;   // the top is upwind of the foot by this much times the lean per block
                bb.addVertex((float) (fx - ox), (float) botY, (float) (fz - oz)).setUv(u0, v0).setColor(cr, cg, cb, a).setLight(light);
                bb.addVertex((float) (fx + ox), (float) botY, (float) (fz + oz)).setUv(u0 + 0.5f, v0).setColor(cr, cg, cb, a).setLight(light);
                bb.addVertex((float) (fx + ox - sx * lean), (float) topY, (float) (fz + oz - sz * lean)).setUv(u0 + 0.5f, v1).setColor(cr, cg, cb, aTop).setLight(light);
                bb.addVertex((float) (fx - ox - sx * lean), (float) topY, (float) (fz - oz - sz * lean)).setUv(u0, v1).setColor(cr, cg, cb, aTop).setLight(light);
            }
        }
        flush(bb);
        RenderSystem.enableCull(); RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc();
        lightTexture.turnOffLightLayer();
        return true;
    }

    /** Draws what the builder holds; a builder nothing was added to (every column in range was skipped) has nothing to draw and must not be built. */
    private static void flush(BufferBuilder bb) {
        if (bb == null) return;
        var mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /** Splashes where the rain lands. (Vanilla's version also plays its own rain sounds; Alterra's come from the Weather addon.) */
    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        if (!WeatherConfig.get(WeatherConfig.REPLACE_RAIN)) return false;
        float rain = HeavyStorm.rainAt(level, camera.getPosition().x, camera.getPosition().z);
        if (rain <= 0f) return true;
        float heavy = HeavyStorm.at(level, camera.getPosition().x, camera.getPosition().z);
        var rnd = RandomSource.create((long) ticks * 312987231L);
        BlockPos cam = BlockPos.containing(camera.getPosition());
        var pos = new BlockPos.MutableBlockPos();
        int n = (int) (120 * rain * rain * (1f + 1.5f * heavy)) / (Minecraft.getInstance().options.particles().get() == net.minecraft.client.ParticleStatus.ALL ? 1 : 3);
        for (int i = 0; i < n; i++) {
            int x = cam.getX() + rnd.nextInt(21) - 10, z = cam.getZ() + rnd.nextInt(21) - 10;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            pos.set(x, y - 1, z);
            if (level.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.RAIN || pos.distSqr(cam) > 100) continue;
            if (HeavyConfig.get(HeavyConfig.CLOUD_RAIN) && !CloudMap.rainsHere(level, x + 0.5, z + 0.5)) continue;
            var st = level.getBlockState(pos);
            if (st.isAir()) continue;
            double top = st.getFluidState().isEmpty() ? st.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y) : st.getFluidState().getHeight(level, pos);
            if (st.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && WeatherConfig.get(WeatherConfig.RIPPLES)) {   // a drop on water: a ring, and now and then a small splash
                double sy = pos.getY() + Math.max(0, top) + 0.02;
                if (!Look.shaderWater.getAsBoolean()) dev.phi.alterra.weather.WeatherParticles.add(level, AlterraWeather.RIPPLE.get(), x + rnd.nextDouble(), sy, z + rnd.nextDouble(), 0, 0, 0);   // the rings are drawn by the water shader when it is in use
                if (rnd.nextInt(5) == 0) dev.phi.alterra.weather.WeatherParticles.add(level, ParticleTypes.SPLASH, x + rnd.nextDouble(), sy + 0.05, z + rnd.nextDouble(), 0, 0.02, 0);
            } else {   // a drop on the ground: a small crown of light droplets, and a ring where water already stands (a puddle)
                double gx = x + rnd.nextDouble(), gz = z + rnd.nextDouble(), gy = pos.getY() + Math.max(0, top);
                float pud = Look.puddleAt.apply(x, z);
                if (pud > 0.03f) dev.phi.alterra.weather.WeatherParticles.add(level, AlterraWeather.RIPPLE.get(), gx, gy + 0.03, gz, 0, 0, 0);
                int drops = 2 + (pud > 0.03f ? 1 : 0);
                for (int k = 0; k < drops; k++) {
                    double a = rnd.nextDouble() * Math.PI * 2, out = 0.012 + rnd.nextDouble() * 0.02;
                    dev.phi.alterra.weather.WeatherParticles.add(level, AlterraWeather.DROP.get(), gx, gy + 0.04, gz, Math.cos(a) * out, 0.06 + rnd.nextDouble() * 0.06, Math.sin(a) * out);
                }
            }
        }
        return true;
    }
}
