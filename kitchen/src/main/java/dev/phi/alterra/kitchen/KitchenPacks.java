package dev.phi.alterra.kitchen;

import dev.phi.alterra.api.AlterraApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.Optional;

/** The drinks' thirst and temperature values for Legendary Survival Overhaul and Tough As Nails, as built-in data packs that follow Alterra's switches. */
@EventBusSubscriber(modid = AlterraKitchen.ID, bus = EventBusSubscriber.Bus.MOD)
public final class KitchenPacks {
    private KitchenPacks() {}

    private static final String[][] PACKS = {{"lso_drinks", "lsoDrinks", "Drink thirst"}, {"lso_temp", "lsoTemperature", "Drink temperature"},
            {"tan_drinks", "tanDrinks", "Tough As Nails drink thirst"}, {"tan_temp", "tanTemperature", "Tough As Nails drink temperature"}};

    @SubscribeEvent
    public static void add(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.SERVER_DATA) return;
        var file = ModList.get().getModFileById(AlterraKitchen.ID);
        if (file == null) return;
        for (String[] pk : PACKS) {
            if (!AlterraApi.compat(pk[1]) || (pk[0].startsWith("tan_") && !ModList.get().isLoaded("toughasnails"))) continue;   // the Tough As Nails packs only when it is installed
            var path = file.getFile().findResource("resourcepacks/" + pk[0]);
            e.addRepositorySource(consumer -> {
                var info = new PackLocationInfo("alterra_kitchen_" + pk[0], Component.literal("Alterra Kitchen: " + pk[2]), PackSource.BUILT_IN, Optional.empty());
                Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(path), PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) consumer.accept(pack);
            });
        }
    }
}
