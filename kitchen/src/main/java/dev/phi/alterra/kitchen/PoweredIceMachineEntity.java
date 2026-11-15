package dev.phi.alterra.kitchen;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The ice cream machine on Create's rotation: it freezes while it turns (faster the faster it turns) and still takes snow and ice in its fuel slot. Only exists when Create is installed. */
public class PoweredIceMachineEntity extends KineticBlockEntity implements MenuProvider, WorldlyContainer {
    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);   // input, cold, output
    private int lit, litTotal, progress, total = 100;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) { return switch (i) { case 0 -> lit; case 1 -> litTotal; case 2 -> progress; default -> total; }; }
        @Override public void set(int i, int v) { switch (i) { case 0 -> lit = v; case 1 -> litTotal = v; case 2 -> progress = v; default -> total = v; } }
        @Override public int getCount() { return 4; }
    };

    public PoweredIceMachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        float rpm = Math.abs(getSpeed());
        int factor = rpm < 8 ? 0 : Math.max(1, Math.round(rpm / 16));   // 16 rpm is the pace of the plain machine
        ItemStack in = items.get(0);
        var recipe = in.isEmpty() ? java.util.Optional.<net.minecraft.world.item.crafting.RecipeHolder<FreezingRecipe>>empty() : level.getRecipeManager().getRecipeFor(AlterraKitchen.FREEZING.get(), new SingleRecipeInput(in), level);
        ItemStack result = recipe.map(h -> h.value().assemble(new SingleRecipeInput(in), level.registryAccess())).orElse(ItemStack.EMPTY);
        ItemStack out = items.get(2);
        boolean fits = !result.isEmpty() && (out.isEmpty() || (ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize()));
        boolean working = false;
        if (factor > 0 && fits) {
            if (lit <= 0) { int burn = Stations.cold(items.get(1)); if (burn > 0) { lit = litTotal = burn; items.get(1).shrink(1); } }
            if (lit > 0) {
                working = true; lit = Math.max(0, lit - factor); total = recipe.get().value().getCookingTime(); progress += factor;
                if (progress >= total) {
                    progress = 0;
                    if (out.isEmpty()) items.set(2, result.copy()); else out.grow(result.getCount());
                    in.shrink(1);
                }
            }
        } else if (progress > 0) progress = Math.max(0, progress - 2);
        BlockState st = getBlockState();
        if (st.getValue(PoweredIceMachineBlock.LIT) != working) level.setBlock(worldPosition, st.setValue(PoweredIceMachineBlock.LIT, working), 3);
        setChanged();
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Lit", lit); tag.putInt("LitTotal", litTotal); tag.putInt("Progress", progress);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        items.clear(); ContainerHelper.loadAllItems(tag, items, registries);
        lit = tag.getInt("Lit"); litTotal = tag.getInt("LitTotal"); progress = tag.getInt("Progress");
    }

    @Override public Component getDisplayName() { return Component.literal("Powered Ice Cream Machine"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new FreezerMenu(id, inv, this, data); }

    // ---- the container: a hopper or funnel puts things in from the top (input) or sides (cold) and takes the ice cream from below
    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int n) { ItemStack r = ContainerHelper.removeItem(items, i, n); if (!r.isEmpty()) setChanged(); return r; }
    @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }
    @Override public void setItem(int i, ItemStack st) { items.set(i, st); if (st.getCount() > getMaxStackSize()) st.setCount(getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player p) { return Container.stillValidBlockEntity(this, p); }
    @Override public void clearContent() { items.clear(); }
    @Override public boolean canPlaceItem(int i, ItemStack st) { return i == 0 || (i == 1 && Stations.cold(st) > 0); }
    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.DOWN ? new int[] {2} : side == Direction.UP ? new int[] {0} : new int[] {1}; }
    @Override public boolean canPlaceItemThroughFace(int i, ItemStack st, Direction side) { return side != Direction.DOWN && canPlaceItem(i, st); }
    @Override public boolean canTakeItemThroughFace(int i, ItemStack st, Direction side) { return i == 2; }
    public NonNullList<ItemStack> contents() { return items; }
}
