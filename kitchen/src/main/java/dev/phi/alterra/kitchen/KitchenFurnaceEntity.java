package dev.phi.alterra.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The stove and the oven: a fuel slot, an input and an output like a furnace. The stove takes the smoking recipes (pasta, noodles, buns, fast), the oven the smelting recipes (baked goods and dried fruit). */
public class KitchenFurnaceEntity extends AbstractFurnaceBlockEntity {
    private final String title; private final boolean stove, freezer;

    public KitchenFurnaceEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, RecipeType<? extends AbstractCookingRecipe> recipes, String title, boolean stove) {
        super(type, pos, state, recipes);
        this.title = title; this.stove = stove; this.freezer = recipes == AlterraKitchen.FREEZING.get();
    }

    /** The ice cream machine runs on snow and ice. */
    @Override protected int getBurnDuration(net.minecraft.world.item.ItemStack fuel) { return freezer ? Stations.cold(fuel) : super.getBurnDuration(fuel); }
    @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack st) { return freezer && slot == 1 ? Stations.cold(st) > 0 : super.canPlaceItem(slot, st); }

    @Override protected Component getDefaultName() { return Component.literal(title); }

    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        if (freezer) return new FreezerMenu(id, inventory, this, this.dataAccess);
        return stove ? new SmokerMenu(id, inventory, this, this.dataAccess) : new FurnaceMenu(id, inventory, this, this.dataAccess);
    }
}
