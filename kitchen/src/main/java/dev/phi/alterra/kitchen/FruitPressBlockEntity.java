package dev.phi.alterra.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The fruit waiting in a fruit press: which fruit (item id) and how many. */
public class FruitPressBlockEntity extends BlockEntity {
    public String fruit = "";
    public int count;
    public FruitPressBlockEntity(BlockPos pos, BlockState state) { super(AlterraKitchen.FRUIT_PRESS_BE.get(), pos, state); }

    void changed(Level level, BlockPos pos) {
        setChanged();
        int fill = count == 0 ? 0 : count < 4 ? 1 : 2;
        BlockState s = getBlockState();
        if (s.getValue(FruitPressBlock.FILL) != fill) level.setBlock(pos, s.setValue(FruitPressBlock.FILL, fill), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.saveAdditional(tag, reg); tag.putString("fruit", fruit); tag.putInt("count", count); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.loadAdditional(tag, reg); fruit = tag.getString("fruit"); count = tag.getInt("count"); }
}
