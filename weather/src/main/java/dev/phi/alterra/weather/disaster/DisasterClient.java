package dev.phi.alterra.weather.disaster;

import dev.phi.prismlib.api.Funnel;
import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;
import dev.phi.alterra.weather.config.WeatherConfig;
import dev.phi.alterra.weather.net.DisasterSync;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The disasters as the client sees them: the server's positions (DisasterSync) kept for a few ticks, handed to Alterra Illuminate to draw the funnels
 * ({@link WeatherHooks#funnels}), with the dust and debris around them as particles.
 */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class DisasterClient {
    private DisasterClient() {}
    private static final class F { int id, type; double x, y, z, tx, ty, tz; float radius, top, strength; long seen; boolean fresh = true; }   // x, y, z: what is drawn (eased towards the server's tx, ty, tz)
    private static final Map<Integer, F> MAP = new HashMap<>();

    /** How wide the funnel is at the top: a tornado flares out a lot, a dust devil a little, a whirlpool is a short, wide mist. */
    public static float topRadius(int type, float radius) { return type == Disasters.TORNADO ? radius * 3.2f + 5f : type == Disasters.DUST_DEVIL ? radius * 2.4f : radius * 1.25f; }
    public static float radiusAt(int type, float radius, float top, float h) { return radius + (topRadius(type, radius) - radius) * (float) Math.pow(Math.max(0f, Math.min(1f, h / top)), 1.4); }

    public static void received(DisasterSync p) {
        var mc = Minecraft.getInstance(); if (mc.level == null) return;
        long now = mc.level.getGameTime(); var v = p.v();
        for (int i = 0; i + 7 < v.size(); i += 8) {
            F f = MAP.computeIfAbsent(Math.round(v.get(i)), k -> { org.slf4j.LoggerFactory.getLogger("Alterra Weather").info("[disaster] client sees {} (type {})", k, Math.round(v.get(0 + 1))); return new F(); });
            f.id = Math.round(v.get(i)); f.type = Math.round(v.get(i + 1)); f.tx = v.get(i + 2); f.ty = v.get(i + 3); f.tz = v.get(i + 4); if (f.fresh) { f.x = f.tx; f.y = f.ty; f.z = f.tz; f.fresh = false; } f.radius = v.get(i + 5); f.top = v.get(i + 6); f.strength = v.get(i + 7); f.seen = now;
        }
    }

    /** The nearest funnels (up to four), as the drawing mod reads them through Prism Lib. */
    private static volatile java.util.List<Funnel> funnels = java.util.List.of();
    public static java.util.List<Funnel> funnels() { return funnels; }

    /** The grid water of every whirlpool this client sees: it runs its own copy of the server's simulation and keeps drawing it until the water has settled after the whirlpool is gone. */
    private static final class Sim { WhirlpoolSim sim; boolean seen; float spin; int gone; }
    private static final Map<Integer, Sim> SIMS = new HashMap<>();
    private static volatile java.util.List<dev.phi.prismlib.api.WaterSurface> surfaces = java.util.List.of();
    public static java.util.List<dev.phi.prismlib.api.WaterSurface> surfaces() { return surfaces; }

    private static void tickSims(net.minecraft.client.multiplayer.ClientLevel level) {
        for (Sim s : SIMS.values()) s.seen = false;
        for (F f : MAP.values()) {
            if (f.type != Disasters.VORTEX) continue;
            Sim s = SIMS.computeIfAbsent(f.id, k -> new Sim());
            if (s.sim == null) { s.sim = new WhirlpoolSim(level, f.tx, f.ty, f.tz, f.radius, f.strength, f.top < 0 ? -1f : 1f); org.slf4j.LoggerFactory.getLogger("Alterra Weather").info("[disaster] this client simulates whirlpool {} (radius {}, strength {})", f.id, f.radius, f.strength); }
            s.seen = true; s.gone = 0; s.spin = f.top < 0 ? -1f : 1f; s.sim.grid.drag = 0.12f;
            if (!Minecraft.getInstance().isPaused()) s.sim.tick(f.strength, s.spin);
        }
        var out = new java.util.ArrayList<dev.phi.prismlib.api.WaterSurface>();
        for (var it = SIMS.values().iterator(); it.hasNext(); ) {
            Sim s = it.next();
            if (!s.seen && !Minecraft.getInstance().isPaused()) { s.gone++; s.sim.grid.drag = 2.5f; s.sim.tick(0f, s.spin); }   // the whirlpool is gone: the water settles, quickly
            if (!s.seen && (s.sim.quiet() || s.gone > 140)) { org.slf4j.LoggerFactory.getLogger("Alterra Weather").info("[disaster] this client stops simulating a whirlpool (gone {} ticks)", s.gone); it.remove(); continue; }   // never longer than seven seconds
            out.add(new dev.phi.prismlib.api.WaterSurface(s.sim.grid, s.sim.y - 0.11, s.sim.radius * 1.35f));
        }
        surfaces = out;
    }

    private static FunnelSound roar;
    /** One continuous roar for the nearest tornado or dust devil: louder the nearer and stronger it is, panned towards it, faded in and out. */
    private static void tickRoar(Minecraft mc, net.minecraft.world.phys.Vec3 cam, F[] near) {
        F best = null; double bd = 1e18;
        for (F f : near) {
            if (f == null || (f.type != Disasters.TORNADO && f.type != Disasters.DUST_DEVIL)) continue;
            double d = Math.hypot(f.x - cam.x, f.z - cam.z);
            if (d < bd) { bd = d; best = f; }
        }
        float reach = best == null ? 0f : best.type == Disasters.TORNADO ? 260f : 110f;
        float vol = best == null ? 0f : (float) Math.pow(net.minecraft.util.Mth.clamp(1.0 - (bd - best.radius) / reach, 0.0, 1.0), 1.6) * net.minecraft.util.Mth.clamp(best.strength, 0f, 1f) * (best.type == Disasters.TORNADO ? 1.5f : 0.6f);
        if (roar != null && roar.isStopped()) roar = null;
        if (vol > 0.02f && best != null) {
            if (roar == null) { roar = new FunnelSound(); mc.getSoundManager().play(roar); org.slf4j.LoggerFactory.getLogger("Alterra Weather").info("[sound] funnel roar starts at {} blocks, volume {}", (int) bd, vol); }
            // the camera's heading turns the world's direction into left and right
            double dx = best.x - cam.x, dz = best.z - cam.z, len = Math.max(1e-3, Math.hypot(dx, dz)); dx /= len; dz /= len;
            float yaw = mc.gameRenderer.getMainCamera().getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            double rx = dx * Math.cos(yaw) + dz * Math.sin(yaw), rz = -dx * Math.sin(yaw) + dz * Math.cos(yaw);   // relative to where you look
            roar.target(Math.min(1.4f, vol), best.type == Disasters.TORNADO ? 0.92f + 0.12f * best.strength : 1.15f, rx, rz);
        } else if (roar != null) roar.end();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || mc.player == null) { MAP.clear(); SIMS.clear(); funnels = java.util.List.of(); surfaces = java.util.List.of(); return; }
        long now = level.getGameTime();
        MAP.values().removeIf(f -> now - f.seen > 12);
        for (F f : MAP.values()) { f.x += (f.tx - f.x) * 0.4; f.y += (f.ty - f.y) * 0.25; f.z += (f.tz - f.z) * 0.4; }   // smooth between the server's updates
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        // the four nearest, for the scene pass
        F[] near = new F[4];
        for (F f : MAP.values()) {
            double d = (f.x - cam.x) * (f.x - cam.x) + (f.z - cam.z) * (f.z - cam.z);
            for (int i = 0; i < 4; i++) {
                if (near[i] == null) { near[i] = f; break; }
                double di = (near[i].x - cam.x) * (near[i].x - cam.x) + (near[i].z - cam.z) * (near[i].z - cam.z);
                if (d < di) { F t = near[i]; near[i] = f; f = t; d = di; }
            }
        }
        var list = new java.util.ArrayList<Funnel>(4);
        for (F f : near) if (f != null) {
            var type = f.type == Disasters.TORNADO ? Funnel.Type.TORNADO : f.type == Disasters.DUST_DEVIL ? Funnel.Type.DUST_DEVIL : Funnel.Type.WHIRLPOOL;
            list.add(new Funnel(type, f.x, f.y, f.z, f.radius, topRadius(f.type, f.radius), f.top, f.strength, Math.signum(f.top)));
        }
        funnels = list;
        tickRoar(mc, cam, near);
        tickSims(level);
        if (mc.player.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat && !mc.isPaused()) {   // the boat you drive is yours to steer: the water turns it, here in your game
            for (F f : near) {
                if (f == null || f.type != Disasters.VORTEX || Math.abs(boat.getY() - f.y) > 4) continue;
                double dx = boat.getX() - f.x, dz = boat.getZ() - f.z, d = Math.sqrt(dx * dx + dz * dz);
                if (d < f.radius * 1.7) boat.setYRot(boat.getYRot() + (float) (Math.signum(f.top) * Vortex.speed(d, f.radius, f.strength) * 70.0));
            }
        }
        if (mc.isPaused()) return;
        float q = mc.options.particles().get() == net.minecraft.client.ParticleStatus.ALL ? 1f : mc.options.particles().get() == net.minecraft.client.ParticleStatus.DECREASED ? 0.5f : 0.2f;
        q *= WeatherConfig.get(WeatherConfig.INTENSITY).floatValue();
        var r = level.random;
        for (F f : near) {
            if (f == null || (f.x - cam.x) * (f.x - cam.x) + (f.z - cam.z) * (f.z - cam.z) > 150 * 150) continue;
            float base = (f.type == Disasters.TORNADO ? 7f : f.type == Disasters.DUST_DEVIL ? 3f : 5f) * f.strength * q;
            int cnt = (int) base + (r.nextFloat() < base % 1f ? 1 : 0);
            for (int i = 0; i < cnt; i++) {
                double ang = r.nextDouble() * Math.PI * 2, h = Math.pow(r.nextDouble(), 2.2) * (f.type == Disasters.TORNADO ? 30 : f.top * 0.8);
                double rad = radiusAt(f.type, f.radius, f.top, (float) h) * (0.6 + r.nextDouble() * 0.6);
                double spin = f.type == Disasters.TORNADO ? 0.14 : 0.07, vx = -Math.sin(ang) * spin, vz = Math.cos(ang) * spin;
                double px = f.x + Math.cos(ang) * rad, pz = f.z + Math.sin(ang) * rad, py = f.y + h + 0.3;
                int rgb = f.type == Disasters.TORNADO ? (r.nextInt(3) == 0 ? 0x2e2a26 : 0x6e655a) : f.type == Disasters.DUST_DEVIL ? (r.nextBoolean() ? 0xE4CB92 : 0xC9A56A) : (r.nextBoolean() ? 0xE6F0F6 : 0xB9CBD6);
                dev.phi.alterra.weather.WeatherParticles.add(level, ColorParticleOption.create(AlterraWeather.DUST.get(), 0xFF000000 | rgb), px, py, pz, vx, f.type == Disasters.VORTEX ? 0.03 : 0.12, vz);
            }
            if (f.type != Disasters.VORTEX && r.nextFloat() < 0.5f * f.strength * q) {   // a low skirt of dust at the foot
                double ang = r.nextDouble() * Math.PI * 2, rad = f.radius * (1.2 + r.nextDouble() * 1.4);
                dev.phi.alterra.weather.WeatherParticles.add(level, ColorParticleOption.create(AlterraWeather.PUFF.get(), 0xFF000000 | (f.type == Disasters.TORNADO ? 0x7a7165 : 0xD2B27A)), f.x + Math.cos(ang) * rad, f.y + 0.6, f.z + Math.sin(ang) * rad, -Math.sin(ang) * 0.12, 0.05, Math.cos(ang) * 0.12);
            }
            if (f.type == Disasters.VORTEX && r.nextFloat() < 0.8f * q) {   // spray thrown off the whirlpool
                double ang = r.nextDouble() * Math.PI * 2, rad = f.radius * (0.5 + r.nextDouble() * 0.6);
                dev.phi.alterra.weather.WeatherParticles.add(level, AlterraWeather.SPRAY.get(), f.x + Math.cos(ang) * rad, f.y + 0.1, f.z + Math.sin(ang) * rad, -Math.sin(ang) * 0.2, 0.15, Math.cos(ang) * 0.2);
            }
        }
    }
}
