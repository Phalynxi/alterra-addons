package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;
import dev.phi.alterra.weather.net.BlastSync;
import dev.phi.alterra.weather.water.Ripples;
import dev.phi.alterra.weather.water.Splashes;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Explosions on or in water: the server tells the players whose game draws the water effects (the same ones who asked for the wetness) where one went off, and their game throws up a column of spray, foam and rings that spread out in waves.
 * Nothing about the explosion itself changes (the game's own rules for blocks and water stay as they are).
 */
public final class Blast {
    private Blast() {}

    @EventBusSubscriber(modid = AlterraWeather.ID)
    public static final class Server {
        @SubscribeEvent
        public static void boom(ExplosionEvent.Detonate e) {
            if (e.getLevel().isClientSide()) return;
            var c = e.getExplosion().center(); float r = e.getExplosion().radius();
            for (var o : ((net.minecraft.server.level.ServerLevel) e.getLevel()).players()) {
                if (o.distanceToSqr(c) > 160.0 * 160.0 || !Wetness.subscribed(o.getUUID()) || !o.connection.hasChannel(BlastSync.TYPE)) continue;
                PacketDistributor.sendToPlayer((ServerPlayer) o, new BlastSync(c.x, c.y, c.z, r));
            }
        }
    }

    /** Called on the client with the explosion's position and radius. */
    public static void received(BlastSync b) {
        var level = Minecraft.getInstance().level;
        if (level == null || !WeatherHooks.shaderWater.getAsBoolean()) return;
        // the water's top at this spot, looked for from above the blast down to below it
        var p = new BlockPos.MutableBlockPos((int) Math.floor(b.x()), (int) Math.floor(b.y() + b.radius() + 3), (int) Math.floor(b.z()));
        double top = Double.NaN;
        for (int i = 0; i < (int) (b.radius() * 2 + 8); i++) {
            var fs = level.getFluidState(p);
            if (fs.is(FluidTags.WATER) && !level.getFluidState(p.above()).is(FluidTags.WATER)) { top = p.getY() + fs.getHeight(level, p); break; }
            p.move(0, -1, 0);
        }
        if (Double.isNaN(top)) return;
        double r = Math.max(1.0, b.radius()), h = b.y() - top;   // h above the water (negative: below it)
        if (h > r * 1.5 || h < -r * 3.0) return;   // too high above it or too deep under it to reach the surface
        double power = Math.min(1.0, (h >= 0 ? 1.0 - h / (r * 1.5) : 1.0 - -h / (r * 3.0)) * 0.7 + 0.3);
        double sc = Math.min(4.5, r * 0.9 * (0.4 + 0.6 * power));
        Splashes.add(b.x(), top, b.z(), sc);
        var rnd = level.random; int ring = 3 + (int) (r * 1.5);
        for (int i = 0; i < ring; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = r * (0.3 + rnd.nextDouble() * 0.8);
            Splashes.add(b.x() + Math.cos(a) * d, top, b.z() + Math.sin(a) * d, sc * 0.4);
        }
        Ripples.add(b.x(), b.z(), (float) Math.max(1.0, Math.min(3.0, r * 0.6)));   // one ring, as big as the blast
    }
}
