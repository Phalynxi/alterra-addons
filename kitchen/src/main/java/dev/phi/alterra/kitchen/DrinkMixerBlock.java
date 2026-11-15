package dev.phi.alterra.kitchen;

import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** A basin that holds four drinks (see DrinkMixerBlockEntity). */
public class DrinkMixerBlock extends BasinBlock {
    public DrinkMixerBlock(Properties p) { super(p); }
    @Override public BlockEntityType<? extends BasinBlockEntity> getBlockEntityType() { return CreateCompat.MIXER_BE.get(); }
}
