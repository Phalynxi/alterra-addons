package dev.phi.alterra.weather.disaster;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.Wind;
import dev.phi.alterra.weather.config.DisasterConfig;
import dev.phi.alterra.weather.storm.HeavyStorm;
import dev.phi.alterra.weather.storm.Storms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hail, in some heavy thunderstorms (or when told to): stones fall around each player. Each stone is sent to the clients as a falling particle, and when it lands
 * (a second later) it hurts what stands there and now and then breaks glass that is open to the sky. Stones that land on a roof or under leaves do nothing.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class Hail {
    private Hail() {}
    /** Game time hail is forced until (the /alterradisaster hail command). */
    public static volatile long forcedUntil;
    private record Stone(long due, double x, double y, double z) {}
    private static final List<Stone> FALLING = new ArrayList<>();
    private static final double FALL_FROM = 34, SPEED = 1.5;   // blocks above the ground the particle appears, blocks a tick

    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { FALLING.clear(); forcedUntil = 0; }

    /** Is it hailing here: forced, or a heavy storm that was rolled to bring it (about four in ten). */
    public static boolean on(ServerLevel l, double x, double z) {
        if (l.getGameTime() < forcedUntil) return true;
        return DisasterConfig.get(DisasterConfig.HAIL) && HeavyStorm.at(l, x, z) > 0.4f && Storms.rollSlot(l.getGameTime() / 1200L + 7919L) < 0.4;
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post ev) {
        if (!(ev.getLevel() instanceof ServerLevel l) || !l.dimensionType().hasSkyLight() || !Disasters.available()) return;
        long now = l.getGameTime();
        if (!FALLING.isEmpty()) land(l, now);
        if (l.players().isEmpty()) return;
        float rate = Math.max(0.1f, DisasterConfig.get(DisasterConfig.RATE).floatValue());
        for (ServerPlayer pl : l.players()) {
            if (!on(l, pl.getX(), pl.getZ())) continue;
            float strength = now < forcedUntil ? 1f : HeavyStorm.at(l, pl.getX(), pl.getZ());
            int n = Math.round(strength * 4f * Math.min(rate, 1.5f) + (l.random.nextFloat() < 0.5f ? 1 : 0));
            for (int i = 0; i < n; i++) {
                double x = pl.getX() + (l.random.nextDouble() - 0.5) * 44, z = pl.getZ() + (l.random.nextDouble() - 0.5) * 44;
                if (!l.hasChunkAt(BlockPos.containing(x, 64, z))) continue;
                int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
                Vec3 w = Wind.at(now, x, z, l.getRainLevel(1f), l.getThunderLevel(1f), HeavyStorm.at(l, x, z));
                double vx = w.x * 0.45, vz = w.z * 0.45, drift = (FALL_FROM / SPEED);   // the wind leans the fall
                double sx = x - vx * drift, sz = z - vz * drift;   // start upwind, land at (x, z)
                l.sendParticles(AlterraWeather.HAIL.get(), sx, top + FALL_FROM, sz, 0, vx, -SPEED, vz, 1.0);
                if (l.canSeeSky(new BlockPos((int) x, top, (int) z)) || l.getBlockState(new BlockPos((int) x, top - 1, (int) z)).is(BlockTags.LEAVES)) FALLING.add(new Stone(now + Math.round(drift), x, top, z));
            }
        }
    }

    /** The stones that arrive now. */
    private static void land(ServerLevel l, long now) {
        Iterator<Stone> it = FALLING.iterator(); int heard = 0;
        while (it.hasNext()) {
            Stone s = it.next();
            if (s.due > now) continue;
            it.remove();
            if (!l.hasChunkAt(BlockPos.containing(s.x, 64, s.z))) continue;
            for (LivingEntity le : l.getEntitiesOfClass(LivingEntity.class, new AABB(s.x - 0.4, s.y - 0.2, s.z - 0.4, s.x + 0.4, s.y + 2.0, s.z + 0.4), x -> !x.isSpectator() && !x.isInvulnerable()))
                le.hurt(l.damageSources().generic(), 1f + l.random.nextFloat() * 2f);
            BlockPos under = new BlockPos((int) Math.floor(s.x), (int) s.y - 1, (int) Math.floor(s.z));
            var st = l.getBlockState(under);
            if (DisasterConfig.get(DisasterConfig.HAIL_GLASS) && (st.is(BlockTags.IMPERMEABLE) || st.is(net.minecraft.world.level.block.Blocks.GLASS_PANE)) && l.random.nextFloat() < 0.05f) {
                l.destroyBlock(under, false); l.playSound(null, under, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 1f);
            } else if (heard++ < 3) l.playSound(null, BlockPos.containing(s.x, s.y, s.z), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.WEATHER, 0.35f, 1.7f + l.random.nextFloat() * 0.5f);   // a few tinks, not one for every stone
        }
        if (FALLING.size() > 4000) FALLING.clear();
    }
}
