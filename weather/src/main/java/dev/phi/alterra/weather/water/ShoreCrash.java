package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;
import dev.phi.alterra.weather.Wind;
import dev.phi.alterra.weather.config.WeatherConfig;
import dev.phi.alterra.weather.storm.HeavyStorm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Waves breaking on the shore: where water meets low land, at the crest of a wave, a burst of spray is thrown up and inland, and the place it lands is wet (the scene look
 * keeps that water as puddle and wet sand, which then drains and dries). A calm lap is a few drops, a storm sea or a gale throws sheets of it.
 */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class ShoreCrash {
    private ShoreCrash() {}
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static boolean broken;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (broken || !WeatherConfig.get(WeatherConfig.SHORE_SPLASH)) return;
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !level.dimensionType().hasSkyLight()) return;
        try { crash(mc, level); }
        catch (Throwable t) { broken = true; org.slf4j.LoggerFactory.getLogger("Alterra Weather").error("Shore splashes failed; they are switched off until the game restarts", t); }
    }

    private static void crash(Minecraft mc, ClientLevel level) {
        var p = mc.player; var r = level.random;
        float rain = level.getRainLevel(1f), thunder = level.getThunderLevel(1f), heavy = HeavyStorm.level(level);
        var w = Wind.at(level.getGameTime(), p.getX(), p.getZ(), rain, thunder, heavy);
        float storm = Math.max(Math.max(thunder, heavy), Math.max(rain * 0.5f, Math.min(1f, (float) w.length() * 0.4f)));
        float q = (mc.options.particles().get() == net.minecraft.client.ParticleStatus.ALL ? 1f : mc.options.particles().get() == net.minecraft.client.ParticleStatus.DECREASED ? 0.5f : 0.2f) * WeatherConfig.get(WeatherConfig.INTENSITY).floatValue();
        if (WeatherConfig.get(WeatherConfig.ADAPT)) q *= Math.max(0.25f, Math.min(1f, mc.getFps() / 50f));
        long gt = level.getGameTime();
        int tries = Math.round((3 + 10 * storm) * q + 0.5f);
        for (int i = 0; i < tries; i++) {
            int x = (int) Math.floor(p.getX()) + r.nextInt(49) - 24, z = (int) Math.floor(p.getZ()) + r.nextInt(49) - 24;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            double crest = Math.sin(gt * 0.045 + x * 0.8 + z * 0.6);   // the same wave rhythm the shore wash-up uses: it breaks at the crest
            if (crest < 0.88 || r.nextFloat() > 0.12 + 0.5 * storm) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (!level.getFluidState(new BlockPos(x, h - 1, z)).is(FluidTags.WATER)) continue;
            for (Direction d : SIDES) {
                int nx = x + d.getStepX(), nz = z + d.getStepZ(), h2 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz);
                if (level.getFluidState(new BlockPos(nx, h2 - 1, nz)).is(FluidTags.WATER) || h2 - h > 1 || h2 - h < -1) continue;   // land at about the level of the water
                int n = Math.max(1, Math.round((3 + storm * 10) * q));
                double sx = x + 0.5 + d.getStepX() * 0.45, sz = z + 0.5 + d.getStepZ() * 0.45;
                for (int k = 0; k < n; k++) {
                    double v = (0.10 + r.nextDouble() * 0.16) * (0.65 + 0.7 * storm), up = (0.15 + r.nextDouble() * 0.2) * (0.65 + 0.8 * storm), side = (r.nextDouble() - 0.5) * 0.12;
                    level.addParticle(AlterraWeather.SPRAY.get(), sx + (r.nextDouble() - 0.5) * 0.6 * Math.abs(d.getStepZ()), h + 0.05, sz + (r.nextDouble() - 0.5) * 0.6 * Math.abs(d.getStepX()),
                            d.getStepX() * v + side * Math.abs(d.getStepZ()), up, d.getStepZ() * v + side * Math.abs(d.getStepX()));
                }
                WeatherHooks.splashOnLand.accept(new double[] {x + 0.5 + d.getStepX() * (1.6 + 1.4 * storm), z + 0.5 + d.getStepZ() * (1.6 + 1.4 * storm), 1.3, 0.012 + 0.03 * storm});
                break;
            }
        }
    }
}
