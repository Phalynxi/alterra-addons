package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.AlterraWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;

/**
 * Lightning in storm cells you are not under: the thunder reaching you later (no flash: it lit the whole sky and the water white) (sound travels about 17 blocks a tick), deeper and quieter the further away.
 * Only for cells whose heart is more than half a radius away (under one, the real lightning of {@link HeavyStormServer} does it). It is the same cells the server uses, so a flash
 * comes from where the dark wall of cloud really is, and you can count the seconds to know how far.
 */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class DistantStorm {
    private DistantStorm() {}
    private record Rumble(long at, double dx, double dz, float volume, float pitch) {}
    private static final ArrayList<Rumble> PENDING = new ArrayList<>();
    private static final ArrayList<StormCells.Cell> SCRATCH = new ArrayList<>();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance(); ClientLevel level = mc.level; var p = mc.player;
        if (level == null || p == null || mc.isPaused()) { PENDING.clear(); return; }
        if (!level.dimensionType().hasSkyLight()) return;
        long now = level.getGameTime();
        for (int i = PENDING.size() - 1; i >= 0; i--) {   // thunder that has arrived
            Rumble r = PENDING.get(i); if (now < r.at()) continue;
            PENDING.remove(i);
            double len = Math.max(1, Math.hypot(r.dx(), r.dz())), s = 30.0 / len;   // played from the direction of the flash, close enough to be heard properly
            level.playLocalSound(p.getX() + r.dx() * s, p.getY() + 40, p.getZ() + r.dz() * s, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, r.volume(), r.pitch(), false);
        }
        if (now % 10 != 0) return;
        StormCells.near(now, p.getX(), p.getZ(), 4500, SCRATCH);
        for (var c : SCRATCH) {
            double dx = c.x() - p.getX(), dz = c.z() - p.getZ(), d = Math.hypot(dx, dz);
            if (d < c.radius() * 0.5 || c.level() < 0.35f || d > 4500) continue;
            if (level.random.nextFloat() > 0.14f * c.level()) continue;   // about one flash in seven seconds per strong cell
            double fx = dx + (level.random.nextDouble() - 0.5) * c.radius(), fz = dz + (level.random.nextDouble() - 0.5) * c.radius(), fd = Math.max(200.0, Math.hypot(fx, fz));
            long delay = (long) (fd / 17.0);
            float far = (float) Math.min(1.0, fd / 4500.0);
            PENDING.add(new Rumble(now + delay, fx, fz, 3.5f * (1f - 0.8f * far), 0.55f + 0.25f * (1f - far) + level.random.nextFloat() * 0.1f));
            if (PENDING.size() > 40) PENDING.remove(0);
        }
    }
}
