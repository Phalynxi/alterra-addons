package dev.phi.alterra.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What is on a prep table, kitchen counter or ice cream stand: it stays there when the screen is closed (and drops when the block is broken). */
public class AssemblyBlockEntity extends BlockEntity {
    private final SimpleContainer items;
    public AssemblyBlockEntity(BlockPos pos, BlockState state) {
        super(AlterraKitchen.ASSEMBLY_BE.get(), pos, state);
        items = new SimpleContainer(((KitchenCounterBlock) state.getBlock()).layout().roles().size());
        items.addListener(c -> setChanged());
    }
    public SimpleContainer items() { return items; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); tag.put("Items", items.createTag(registries)); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) items.fromTag(tag.getList("Items", 10), registries);
    }
}
