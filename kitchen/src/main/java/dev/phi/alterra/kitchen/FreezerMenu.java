package dev.phi.alterra.kitchen;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;

/** The ice cream machine's screen is the smoker's: its fuel slot takes snow and ice, and it works the freezing recipes. */
public final class FreezerMenu extends AbstractFurnaceMenu {
    public FreezerMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(MenuType.SMOKER, AlterraKitchen.FREEZING.get(), RecipeBookType.SMOKER, id, inv, container, data);
    }
    @Override protected boolean isFuel(ItemStack st) { return Stations.cold(st) > 0; }
}
