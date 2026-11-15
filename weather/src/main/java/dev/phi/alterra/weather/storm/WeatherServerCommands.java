package dev.phi.alterra.weather.storm;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.Wind;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.phi.alterra.weather.storm.Storms.Kind;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** Adds Alterra's storms to the vanilla /weather command: /weather sandstorm [seconds], /weather calm, /weather wind. */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class WeatherServerCommands {
    private WeatherServerCommands() {}
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var weather = e.getDispatcher().getRoot().getChild("weather");
        if (weather == null) { LOG.warn("Alterra Weather: /weather was not found, its storm commands are not added"); return; }
        LOG.info("Alterra Weather: adding storm commands to /weather");
        for (Kind k : Kind.values()) {
            String name = k.name().toLowerCase(Locale.ROOT);
            var run = (com.mojang.brigadier.Command<net.minecraft.commands.CommandSourceStack>) c -> start(c.getSource(), k, 300);
            weather.addChild(Commands.literal(name).requires(s -> s.hasPermission(2)).executes(run)
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(c -> start(c.getSource(), k, IntegerArgumentType.getInteger(c, "seconds")))).build());
        }
        weather.addChild(Commands.literal("thunderstorm").requires(s -> s.hasPermission(2)).executes(c -> heavy(c.getSource(), 300))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(c -> heavy(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))).build());
        weather.addChild(Commands.literal("calm").requires(s -> s.hasPermission(2)).executes(c -> {
            if (HeavyStorm.forcedUntil != 0) { HeavyStorm.forcedUntil = 0; PacketDistributor.sendToAllPlayers(new ForcedStorm(ForcedStorm.HEAVY, 0)); }
            for (Kind k : Kind.values()) if (Storms.FORCED_UNTIL.remove(k) != null) PacketDistributor.sendToAllPlayers(new ForcedStorm(k.ordinal(), 0));
            c.getSource().sendSuccess(() -> Component.literal("The storms are over"), true);
            return 1;
        }).build());
        weather.addChild(Commands.literal("wind").executes(c -> {
            var src = c.getSource(); var level = src.getLevel(); var pos = src.getPosition();
            var w = Wind.at(level.getGameTime(), pos.x, pos.z, level.getRainLevel(1f), level.getThunderLevel(1f));
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Wind: %.2f, blowing towards the %s", w.length(), Wind.compass(w))), false);
            return 1;
        }).build());
    }

    /** /weather thunderstorm: real vanilla thunder and rain, and a heavy one. */
    private static int heavy(net.minecraft.commands.CommandSourceStack src, int seconds) {
        var level = src.getLevel(); long until = level.getGameTime() + seconds * 20L;
        level.setWeatherParameters(0, seconds * 20, true, true);
        HeavyStorm.forcedUntil = until;
        PacketDistributor.sendToAllPlayers(new ForcedStorm(ForcedStorm.HEAVY, until));
        src.sendSuccess(() -> Component.literal("A heavy thunderstorm for " + seconds + " seconds"), true);
        return 1;
    }

    private static int start(net.minecraft.commands.CommandSourceStack src, Kind k, int seconds) {
        long until = src.getLevel().getGameTime() + seconds * 20L;
        Storms.FORCED_UNTIL.put(k, until);
        PacketDistributor.sendToAllPlayers(new ForcedStorm(k.ordinal(), until));
        src.sendSuccess(() -> Component.literal(k.name().toLowerCase(Locale.ROOT) + " for " + seconds + " seconds"), true);
        return 1;
    }

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) { Storms.FORCED_UNTIL.clear(); }   // storms do not carry over to the next world

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {   // a player who joins in the middle of one still sees it
        if (!(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return;
        Storms.FORCED_UNTIL.forEach((k, until) -> PacketDistributor.sendToPlayer(p, new ForcedStorm(k.ordinal(), until)));
        if (HeavyStorm.forcedUntil != 0) PacketDistributor.sendToPlayer(p, new ForcedStorm(ForcedStorm.HEAVY, HeavyStorm.forcedUntil));
    }
}
