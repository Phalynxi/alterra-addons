package dev.phi.alterra.weather.storm;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.phi.alterra.weather.AlterraWeather;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;

/** /alterracells [range]: the storm cells around you (default 3000 blocks): how far, which way, how strong, and where they are heading. */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class CellsCommand {
    private CellsCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("alterracells").requires(s -> s.hasPermission(2))
                .executes(c -> list(c.getSource(), 3000))
                .then(Commands.argument("range", DoubleArgumentType.doubleArg(100, 20000)).executes(c -> list(c.getSource(), DoubleArgumentType.getDouble(c, "range")))));
    }

    private static int list(net.minecraft.commands.CommandSourceStack src, double range) {
        var level = src.getLevel(); var p = src.getPosition(); var cells = new ArrayList<StormCells.Cell>();
        StormCells.near(level.getGameTime(), p.x, p.z, range, cells);
        if (cells.isEmpty()) { src.sendSuccess(() -> Component.literal("No storm cells within " + (int) range + " blocks."), false); return 0; }
        cells.sort(java.util.Comparator.comparingDouble(c -> Math.hypot(c.x() - p.x, c.z() - p.z)));
        for (var c : cells) {
            double dx = c.x() - p.x, dz = c.z() - p.z, d = Math.hypot(dx, dz);
            String compass = new String[] {"E", "SE", "S", "SW", "W", "NW", "N", "NE"}[(int) Math.floorMod(Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8L)];
            double closing = d < 1 ? 0 : -(dx * c.vx() + dz * c.vz()) / d * 20.0;   // blocks a second towards you (negative: moving away)
            String line = String.format("Cell %s of you, %d blocks away, %d across, strength %.2f, %s %.1f blocks/s%s", compass, Math.round(d), Math.round(c.radius() * 2), c.level(),
                    closing >= 0 ? "closing at" : "moving away at", Math.abs(closing), d < c.radius() ? " (you are in it)" : "");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return cells.size();
    }
}
