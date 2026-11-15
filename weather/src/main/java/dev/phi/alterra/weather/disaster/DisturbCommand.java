package dev.phi.alterra.weather.disaster;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.phi.aethryon.api.AethryonApi;
import dev.phi.aethryon.api.Disturbance;
import dev.phi.alterra.weather.AlterraWeather;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/** /alterradisturb <step|wheel|wash|hull|blast|splash> <radius> <energy> [dx dz]: send a disturbance at your feet (for testing what answers them). */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class DisturbCommand {
    private DisturbCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("alterradisturb").requires(s -> s.hasPermission(2))
                .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> { for (var k : Disturbance.Kind.values()) b.suggest(k.name().toLowerCase(Locale.ROOT)); return b.buildFuture(); })
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.1f, 40f)).then(Commands.argument("energy", FloatArgumentType.floatArg(0f, 4f))
                                .executes(c -> send(c.getSource(), StringArgumentType.getString(c, "kind"), FloatArgumentType.getFloat(c, "radius"), FloatArgumentType.getFloat(c, "energy"), 0, 0))
                                .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-1, 1)).then(Commands.argument("dz", DoubleArgumentType.doubleArg(-1, 1))
                                        .executes(c -> send(c.getSource(), StringArgumentType.getString(c, "kind"), FloatArgumentType.getFloat(c, "radius"), FloatArgumentType.getFloat(c, "energy"), DoubleArgumentType.getDouble(c, "dx"), DoubleArgumentType.getDouble(c, "dz")))))))));
    }

    private static int send(net.minecraft.commands.CommandSourceStack src, String kind, float radius, float energy, double dx, double dz) {
        Disturbance.Kind k;
        try { k = Disturbance.Kind.valueOf(kind.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { src.sendFailure(Component.literal("Kinds: step, wheel, wash, hull, blast, splash")); return 0; }
        var p = src.getPosition(); AethryonApi.disturb(src.getLevel(), new Disturbance(k, p.x, p.y, p.z, radius, energy, dx, 0, dz));
        return 1;
    }
}
