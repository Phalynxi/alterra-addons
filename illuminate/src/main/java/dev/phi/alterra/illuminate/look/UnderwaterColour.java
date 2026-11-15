package dev.phi.alterra.illuminate.look;

import dev.phi.alterra.illuminate.Illuminate;
import dev.phi.alterra.illuminate.IlluminateConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The colour of the water around you when you dive: the biome's water colour turned clear and turquoise, darker at night,
 * instead of vanilla's dark navy fog. Vanilla's fog and the scene pass (PostFx) both use it, so the distance and the near water match.
 */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
final class UnderwaterColour {
    private UnderwaterColour() {}
    static final float[] rgb = {0.08f, 0.32f, 0.40f};

    /** Work out this frame's colour (render thread). */
    static float[] update(float pt) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return rgb;
        int wc = mc.level.getBiome(BlockPos.containing(mc.gameRenderer.getMainCamera().getPosition())).value().getWaterColor();
        float day = Math.max(0.12f, 1f - mc.level.getSkyDarken(pt) / 11f * 1.0f);   // skyDarken: 0 at noon, up to 11 at night
        day = Math.min(1f, day);
        float r = ((wc >> 16) & 255) / 255f, g = ((wc >> 8) & 255) / 255f, b = (wc & 255) / 255f;
        float k = 0.25f + 0.45f * day;   // night water stays a deep blue, not black
        rgb[0] = (r * 0.35f + 0.10f * 0.65f) * k;
        rgb[1] = (g * 0.35f + 0.42f * 0.65f) * k;
        rgb[2] = (b * 0.35f + 0.50f * 0.65f) * k;
        return rgb;
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor e) {
        if (e.getCamera().getFluidInCamera() != FogType.WATER || !IlluminateConfig.get(IlluminateConfig.UNDERWATER_FOG)) return;
        float[] c = update((float) e.getPartialTick());
        e.setRed(c[0]); e.setGreen(c[1]); e.setBlue(c[2]);
    }
}
