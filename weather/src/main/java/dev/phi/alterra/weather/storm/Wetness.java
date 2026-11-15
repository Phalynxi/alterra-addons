package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.Drying;
import dev.phi.alterra.weather.net.WetSync;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How wet the world is around a player, worked out on the server: it rises while it rains where the player is (not in snow or the desert) and dries slowly afterwards, and is kept with the player, so it carries on after a relog and is the same for everyone
 * who looks at the same weather. It is only worked out and sent for players whose game has asked for it (they are drawing wet surfaces); everyone else costs nothing.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class Wetness {
    private Wetness() {}
    private static final Set<UUID> SUBSCRIBED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Float> LAST_SENT = new ConcurrentHashMap<>();
    private static final String KEY = "alterra_weather_wet";
    /** On the client: the last value the server sent, and when. */
    public static volatile float serverWet; public static volatile long serverAt;

    public static void subscribe(ServerPlayer p, boolean on) {
        if (on) { SUBSCRIBED.add(p.getUUID()); LAST_SENT.remove(p.getUUID()); } else { SUBSCRIBED.remove(p.getUUID()); LAST_SENT.remove(p.getUUID()); }
    }
    public static boolean subscribed(UUID id) { return SUBSCRIBED.contains(id); }
    public static void received(float wet) { serverWet = wet; serverAt = System.nanoTime(); }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0 || !SUBSCRIBED.contains(p.getUUID())) return;
        var level = p.serverLevel();
        float rain = HeavyStorm.rainAt(level, p.getX(), p.getZ());   // vanilla rain, or the downpour under a storm cell
        if (!level.dimensionType().hasSkyLight() || level.getBiome(p.blockPosition()).value().getPrecipitationAt(p.blockPosition()) != Biome.Precipitation.RAIN) rain = 0f;
        float wet = p.getPersistentData().getFloat(KEY);
        wet = Math.max(0f, Math.min(1f, wet + (rain > 0.05f ? rain * 0.5f / 25f : -0.5f / 120f * Drying.factor(level, p.blockPosition()))));   // half a second per step: wet in about 25 seconds of rain, dry in about two minutes (faster in warm places, slower in cold ones)
        p.getPersistentData().putFloat(KEY, wet);
        Float last = LAST_SENT.get(p.getUUID());
        if ((last == null || Math.abs(last - wet) > 0.01f || p.tickCount % 100 == 0) && p.connection.hasChannel(WetSync.TYPE)) {
            PacketDistributor.sendToPlayer(p, new WetSync(wet)); LAST_SENT.put(p.getUUID(), wet);
        }
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent e) { SUBSCRIBED.remove(e.getEntity().getUUID()); LAST_SENT.remove(e.getEntity().getUUID()); }
}
