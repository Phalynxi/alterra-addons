package dev.phi.alterra.example;

import dev.phi.alterra.api.AlterraApi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** An example Alterra addon: one setting on Alterra's settings screen. Replace it with your own. */
@Mod(AlterraExample.ID)
public final class AlterraExample {
    public static final String ID = "alterra_example";
    private static final ModConfigSpec.BooleanValue GREETING;
    private static final ModConfigSpec SPEC;
    static {
        var b = new ModConfigSpec.Builder();
        GREETING = b.comment("Say hello when the game starts.").define("greeting", true);
        SPEC = b.build();
    }

    public AlterraExample(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
        AlterraApi.addSettingsCategory("Example", "What the example addon does.", () -> new ItemStack(Items.APPLE),
                List.of(AlterraApi.Option.bool("Greeting", "Say hello when the game starts.", GREETING)));
    }
}
