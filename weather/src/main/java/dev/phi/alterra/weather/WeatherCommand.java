package dev.phi.alterra.weather;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.phi.alterra.weather.Storms.Kind;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Arrays;
import java.util.Locale;

/** /alterraweather <storm> or off: shows a storm wherever you are. Client only. */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class WeatherCommand {
    private WeatherCommand() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent e) {
        SuggestionProvider<net.minecraft.commands.CommandSourceStack> names = (ctx, b) -> {
            Arrays.stream(Kind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)).forEach(b::suggest); b.suggest("off");
            return b.buildFuture();
        };
        e.getDispatcher().register(Commands.literal("alterraweather").then(Commands.argument("storm", StringArgumentType.word()).suggests(names).executes(c -> {
            String arg = StringArgumentType.getString(c, "storm").toLowerCase(Locale.ROOT);
            if (arg.equals("off")) { WeatherEffects.FORCED.clear(); c.getSource().sendSuccess(() -> Component.literal("Weather: back to normal"), false); return 1; }
            for (Kind k : Kind.values()) if (k.name().toLowerCase(Locale.ROOT).equals(arg)) {
                if (WeatherEffects.FORCED.remove(k) == null) WeatherEffects.FORCED.put(k, true);
                c.getSource().sendSuccess(() -> Component.literal("Weather: " + arg + (WeatherEffects.FORCED.containsKey(k) ? " on" : " off")), false);
                return 1;
            }
            c.getSource().sendFailure(Component.literal("Unknown storm. Try: " + String.join(", ", Arrays.stream(Kind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)).toList()) + ", off"));
            return 0;
        })));
    }
}
