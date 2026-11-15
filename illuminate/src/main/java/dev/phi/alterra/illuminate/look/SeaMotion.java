package dev.phi.alterra.illuminate.look;

import dev.phi.alterra.illuminate.IlluminateConfig;
import dev.phi.alterra.illuminate.maps.SeaState;
import dev.phi.alterra.weather.WeatherHooks;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.phi.alterra.api.AlterraApi;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.HashMap;
import java.util.Map;

/**
 * Boats ride the swell of the water surface: the same waves, time and wind as the water shader's swell and the same height (the surface really is that much higher or lower there), so a boat stays on the water instead of
 * sinking below it or hovering. It is averaged over the hull, smoothed over time and calmer the faster the boat goes, so it rises and leans gently with the long waves and does not shake with every ripple.
 * Only how the boat is drawn changes; where it is and what it hits does not.
 */
public final class SeaMotion {
    private SeaMotion() {}

    /** The height of the swell at a point, -1 to 1, the way the water shader makes it. */
    static float swell(double px, double pz, float t, double wx, double wz) {
        double k = 0.39269908, sp = Math.hypot(wx, wz), dx = sp > 0.001 ? wx / sp : 1.0, dz = sp > 0.001 ? wz / sp : 0.0;
        double a = Math.sin((px * dx + pz * dz) * k + t * 1.1);
        double b = Math.sin((px * -dz + pz * dx) * 2.0 * k + t * 1.7 + 1.0);
        double c = Math.sin(px * 3.0 * k + pz * 2.0 * k - t * 2.3);
        return (float) (a * 0.5 + b * 0.15 + c * 0.03);   // the long swell in full, the shorter waves much less: the boat climbs the swells and does not shake with every ripple
    }

    private static final Map<Integer, float[]> smooth = new HashMap<>();   // per boat: height, pitch, roll, last time
    private static long cleaned;

    /** The surface at a point, averaged over a hull's width: height in blocks. */
    private static float surface(double x, double z, float t, double wx, double wz, float amp) {
        float s = 0;
        for (int i = 0; i < 5; i++) { double ox = i == 1 ? 0.7 : i == 2 ? -0.7 : 0, oz = i == 3 ? 0.7 : i == 4 ? -0.7 : 0; s += swell(x + ox, z + oz, t, wx, wz); }
        return s / 5f * amp;
    }

    /** Called when a boat is about to be drawn, with the pose already at the boat. */
    public static void lift(Boat boat, PoseStack ps) {
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || !boat.isInWater() || !IlluminateConfig.get(IlluminateConfig.WATER_WAVES) || !IlluminateConfig.get(IlluminateConfig.WATER_LOOK)) return;
        double x = boat.getX(), z = boat.getZ();
        var cp = mc.gameRenderer.getMainCamera().getPosition(); var w = AlterraApi.wind(level, cp.x, cp.z);   // the shader uses the wind at the camera
        float t = WeatherHooks.clock(), amp = Foliage.swellAmp(SeaState.at(x, z));
        float h = surface(x, z, t, w.x, w.z, amp);   // only how it is drawn: its real movement is never touched
        float sx = (surface(x + 0.8, z, t, w.x, w.z, amp) - surface(x - 0.8, z, t, w.x, w.z, amp)) / 1.6f, sz = (surface(x, z + 0.8, t, w.x, w.z, amp) - surface(x, z - 0.8, t, w.x, w.z, amp)) / 1.6f;
        double speed = Math.hypot(boat.getDeltaMovement().x, boat.getDeltaMovement().z);
        float calm = (float) (1.0 / (1.0 + 0.6 * speed));   // a boat under way planes only a little
        float[] st = smooth.computeIfAbsent(boat.getId(), k -> new float[] {h, 0f, 0f, t});
        float dt = Math.max(0f, Math.min(0.2f, t - st[3])); st[3] = t;
        float a = 1f - (float) Math.exp(-dt * 12f);
        // never faster than a real boat rides a swell, however wild the sea is: the rise is limited to 0.45 blocks a second and the tilt to 0.3 radians a second
        float dh = (Math.max(-0.5f, Math.min(0.5f, h * calm)) - st[0]) * a, dp = (Math.max(-0.2f, Math.min(0.2f, sz * calm)) - st[1]) * a, dr = (Math.max(-0.2f, Math.min(0.2f, -sx * calm)) - st[2]) * a;
        float mh = 0.45f * dt, mt = 0.3f * dt;
        st[0] += Math.max(-mh, Math.min(mh, dh)); st[1] += Math.max(-mt, Math.min(mt, dp)); st[2] += Math.max(-mt, Math.min(mt, dr));
        if (System.nanoTime() - cleaned > 20_000_000_000L) { cleaned = System.nanoTime(); smooth.keySet().removeIf(id -> level.getEntity(id) == null); }
        ps.translate(0.0, st[0], 0.0);
        ps.mulPose(Axis.XP.rotation(st[1])); ps.mulPose(Axis.ZP.rotation(st[2]));
    }
}
