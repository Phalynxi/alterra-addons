package dev.phi.alterra.kitchen;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The ice cream machine for Create: the shaft goes into the back, the front faces the way it was placed. */
public class PoweredIceMachineBlock extends HorizontalKineticBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public PoweredIceMachineBlock(Properties props) { super(props); registerDefaultState(defaultBlockState().setValue(LIT, false)); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(LIT); }
    @Override public MutableComponent getName() { return Component.literal("Powered Ice Cream Machine"); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(HORIZONTAL_FACING).getAxis(); }
    @Override public boolean hasShaftTowards(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state, Direction face) { return face == state.getValue(HORIZONTAL_FACING).getOpposite(); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PoweredIceMachineEntity(CreateCompat.BE.get(), pos, state); }
    @SuppressWarnings("unchecked")
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CreateCompat.BE.get() ? (l, p, s, be) -> ((PoweredIceMachineEntity) be).tick() : null;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MenuProvider be) player.openMenu(be);
        return InteractionResult.CONSUME;
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof PoweredIceMachineEntity be) Containers.dropContents(level, pos, be.contents());
        super.onRemove(state, level, pos, next, moved);
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextDouble() > 0.3) return;
        Direction f = state.getValue(HORIZONTAL_FACING);
        level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5 + f.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.3 + random.nextDouble() * 0.4, pos.getZ() + 0.5 + f.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
    }
}
