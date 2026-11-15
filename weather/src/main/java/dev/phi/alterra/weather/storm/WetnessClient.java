package dev.phi.alterra.weather.storm;

import dev.phi.aethryon.api.Look;
import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tells the server whether this game is drawing wet surfaces (the water shader is on), once when that changes: with it off nothing is asked for, worked out or sent. */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class WetnessClient {
    private WetnessClient() {}
    private static boolean told;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null || mc.player == null || mc.player.tickCount % 20 != 0) return;
        boolean want = Look.shaderWater.getAsBoolean();
        if (want == told) return;
        try {
            if (!mc.getConnection().hasChannel(WetSubscribe.TYPE)) return;   // the server does not have this mod: this game works the wetness out itself
            PacketDistributor.sendToServer(new WetSubscribe(want)); told = want;
            if (!want) Wetness.serverAt = 0;
        } catch (Throwable t) { /* nothing to tell */ }
    }

    @SubscribeEvent
    public static void left(ClientPlayerNetworkEvent.LoggingOut e) { told = false; Wetness.serverAt = 0; }
}
