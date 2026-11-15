package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.config.HeavyConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Server side of the heavy thunderstorm: keeps its state and strikes lightning around the players while it lasts. */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class HeavyStormServer {
    private HeavyStormServer() {}

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !level.dimensionType().hasSkyLight()) return;
        HeavyStorm.update(level);
        float heavy = HeavyStorm.level(level);
        if (heavy < 0.35f || !level.isThundering() || !HeavyConfig.get(HeavyConfig.LIGHTNING)) return;
        double rate = heavy * 0.016 * HeavyConfig.get(HeavyConfig.LIGHTNING_RATE);   // per player per tick
        for (ServerPlayer p : level.players()) {
            if (level.random.nextDouble() >= rate) continue;
            double a = level.random.nextDouble() * Math.PI * 2, dist = 28 + level.random.nextDouble() * 56;
            int x = (int) (p.getX() + Math.cos(a) * dist), z = (int) (p.getZ() + Math.sin(a) * dist);
            if (!level.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            bolt.moveTo(Vec3.atBottomCenterOf(new BlockPos(x, y, z)));
            bolt.setVisualOnly(level.random.nextDouble() >= HeavyConfig.get(HeavyConfig.REAL_BOLTS));   // most are for show
            level.addFreshEntity(bolt);
        }
    }

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) { HeavyStorm.reset(); }
}
