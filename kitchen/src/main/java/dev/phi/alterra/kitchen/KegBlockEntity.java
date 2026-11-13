package dev.phi.alterra.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What is fermenting in a keg: the bottled drink that went in (item id), how many, what comes out and when it is ready (game time). */
public class KegBlockEntity extends BlockEntity {
    public String input = "", output = "";
    public int count;
    public long readyAt;
    public KegBlockEntity(BlockPos pos, BlockState state) { super(AlterraKitchen.KEG_BE.get(), pos, state); }

    boolean ready(Level level) { return count > 0 && level.getGameTime() >= readyAt; }

    void changed(Level level, BlockPos pos) {
        setChanged();
        int fill = count == 0 ? 0 : ready(level) ? 2 : 1;
        BlockState s = getBlockState();
        if (s.getValue(KegBlock.FILL) != fill) level.setBlock(pos, s.setValue(KegBlock.FILL, fill), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putString("input", input); tag.putString("output", output); tag.putInt("count", count); tag.putLong("readyAt", readyAt);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        input = tag.getString("input"); output = tag.getString("output"); count = tag.getInt("count"); readyAt = tag.getLong("readyAt");
    }
}
