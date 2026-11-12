package dev.phi.alterra.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Trellis. Use grapes on one standing on dirt or farmland to plant a vine; it grows leaves and grapes (AGE 0-3) and spreads up into trellises above it, 3 high at most. Right-click ripe grapes to pick them. */
public class TrellisBlock extends Block implements net.minecraft.world.level.block.BonemealableBlock {
    public static final MapCodec<TrellisBlock> CODEC = simpleCodec(TrellisBlock::new);
    public enum Vine implements net.minecraft.util.StringRepresentable {
        NONE, PURPLE, WHITE;
        @Override public String getSerializedName() { return name().toLowerCase(); }
    }
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<Vine> VINE = EnumProperty.create("vine", Vine.class);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape X = Block.box(0, 0, 6.5, 16, 16, 9.5), Z = Block.box(6.5, 0, 0, 9.5, 16, 16);
    private static final int MAX_HEIGHT = 3;

    public TrellisBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(VINE, Vine.NONE).setValue(AGE, 0)); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AXIS, VINE, AGE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(AXIS, c.getHorizontalDirection().getClockWise().getAxis()); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return s.getValue(AXIS) == Direction.Axis.X ? X : Z; }

    private static boolean ground(BlockState s) { return s.is(BlockTags.DIRT) || s.is(Blocks.FARMLAND); }
    private boolean vineCanGrow(LevelReader l, BlockPos p) {
        BlockState below = l.getBlockState(p.below());
        return ground(below) || (below.is(this) && below.getValue(VINE) != Vine.NONE);
    }
    private static Vine vineFor(ItemStack st) {
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        return id.equals("alterra:grapes") ? Vine.PURPLE : id.equals("alterra:white_grapes") ? Vine.WHITE : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Vine v = vineFor(held);
        if (v == null || state.getValue(VINE) != Vine.NONE || !vineCanGrow(level, pos)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(VINE, v).setValue(AGE, 0), 3);
            if (!player.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(VINE) == Vine.NONE || state.getValue(AGE) < 3) return InteractionResult.PASS;
        if (!level.isClientSide) {
            String fruit = state.getValue(VINE) == Vine.WHITE ? "white_grapes" : "grapes";
            popResource(level, pos, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("alterra", fruit)), 2 + level.random.nextInt(2)));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1f, 1f);
            level.setBlock(pos, state.setValue(AGE, 2), 2);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected boolean isRandomlyTicking(BlockState s) { return s.getValue(VINE) != Vine.NONE; }
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        if (r.nextInt(5) != 0) return;
        int age = state.getValue(AGE);
        if (age < 3) { level.setBlock(pos, state.setValue(AGE, age + 1), 2); return; }
        climb(state, level, pos);
    }
    /** A leafy vine spreads into the empty trellis above it, up to three high. */
    private void climb(BlockState state, ServerLevel level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        if (!above.is(this) || above.getValue(VINE) != Vine.NONE) return;
        int h = 1; while (level.getBlockState(pos.below(h)).is(this) && level.getBlockState(pos.below(h)).getValue(VINE) != Vine.NONE) h++;
        if (h < MAX_HEIGHT) level.setBlock(pos.above(), above.setValue(VINE, state.getValue(VINE)).setValue(AGE, 0), 3);
    }

    /** A vine whose support is gone dies back to a bare trellis. */
    @Override
    protected BlockState updateShape(BlockState s, Direction d, BlockState o, net.minecraft.world.level.LevelAccessor l, BlockPos p, BlockPos op) {
        if (d == Direction.DOWN && s.getValue(VINE) != Vine.NONE && !vineCanGrow(l, p)) return s.setValue(VINE, Vine.NONE).setValue(AGE, 0);
        return super.updateShape(s, d, o, l, p, op);
    }

    @Override public boolean isValidBonemealTarget(LevelReader l, BlockPos p, BlockState s) { return s.getValue(VINE) != Vine.NONE && s.getValue(AGE) < 3; }
    @Override public boolean isBonemealSuccess(Level l, RandomSource r, BlockPos p, BlockState s) { return true; }
    @Override public void performBonemeal(ServerLevel l, RandomSource r, BlockPos p, BlockState s) { l.setBlock(p, s.setValue(AGE, Math.min(3, s.getValue(AGE) + 1)), 2); }
}
