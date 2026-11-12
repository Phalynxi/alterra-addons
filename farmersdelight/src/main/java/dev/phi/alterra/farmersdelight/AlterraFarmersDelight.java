package dev.phi.alterra.farmersdelight;

import dev.phi.alterra.api.AlterraApi;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Alterra for Farmer's Delight: three knives, plus cooking pot and cutting board recipes. */
@Mod(AlterraFarmersDelight.ID)
public final class AlterraFarmersDelight {
    public static final String ID = "alterra_farmersdelight";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);

    public AlterraFarmersDelight(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, FdConfig.SPEC);
        KnifeItems.register(ITEMS);
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) ITEMS.getEntries().forEach(h -> e.accept(h.get()));
        });
        AlterraApi.addTabItems(() -> ITEMS.getEntries().stream().map(h -> new ItemStack(h.get())).toList());
        AlterraApi.addSettingsCategory("Farmer's Delight", "Alterra's knives and recipes.", () -> new ItemStack(Items.IRON_SWORD), List.of(
                AlterraApi.Option.bool("Ember Knife cooks", "The Ember Knife cooks what it cuts or kills.", FdConfig.EMBER_KNIFE_COOKS)));
    }
}
