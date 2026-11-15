package dev.phi.alterra.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A wild plant (a fern, nightshade, hemlock, a deathcap, oleander): a small crossed-planes plant that grows on dirt and drops its leaves, berries or cap when broken. */
public class WildPlantBlock extends BushBlock {
    public static final MapCodec<WildPlantBlock> CODEC = simpleCodec(p -> new WildPlantBlock(p, ""));
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(2, 0, 2, 14, 13, 14);
    private final String title;
    public WildPlantBlock(Properties props, String title) { super(props); this.title = title; }
    @Override protected MapCodec<? extends BushBlock> codec() { return CODEC; }
    @Override public net.minecraft.network.chat.MutableComponent getName() { return Component.literal(title); }
    @Override protected VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }
}
