package dev.phi.alterra.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A hand-cranked fruit press: put fruit in (one kind at a time, up to 8; sneak to put in all you hold), then use a glass bottle on it to
 * press a juice once there is enough fruit (2, a coconut 1). FILL shows how full it is. Which fruit gives which juice:
 * alterra_press.json (generated from the juice recipes).
 */
public class FruitPressBlock extends BaseEntityBlock {
    public static final MapCodec<FruitPressBlock> CODEC = simpleCodec(FruitPressBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 2);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);
    private static Map<String, java.util.List<Object>> table;   // fruit item id -> [juice item id, fruit per bottle]
    public static final int CAPACITY = 8;

    public FruitPressBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FILL, 0)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FILL); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FruitPressBlockEntity(pos, state); }

    @SuppressWarnings("unchecked")
    static Map<String, java.util.List<Object>> table() {
        if (table == null) {
            try (var in = FruitPressBlock.class.getResourceAsStream("/alterra_kitchen_press.json")) {
                table = new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in), Map.class);
            } catch (Exception e) { table = Map.of(); }
        }
        return table;
    }
    private static String id(ItemStack st) { return BuiltInRegistries.ITEM.getKey(st.getItem()).toString(); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FruitPressBlockEntity press)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var entry = table().get(id(held));
        if (entry != null && (press.fruit.isEmpty() || press.fruit.equals(id(held))) && press.count < CAPACITY) {   // load fruit
            if (!level.isClientSide) {
                int n = Math.min(player.isShiftKeyDown() ? held.getCount() : 1, CAPACITY - press.count);
                press.fruit = id(held); press.count += n;
                if (!player.getAbilities().instabuild) held.shrink(n);
                press.changed(level, pos);
                level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8f, 1.1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(Items.GLASS_BOTTLE) && !press.fruit.isEmpty()) {   // press a bottle of juice
            var e = table().get(press.fruit);
            int need = e == null ? 2 : ((Number) e.get(1)).intValue();
            if (e == null || press.count < need) return ItemInteractionResult.FAIL;
            if (!level.isClientSide) {
                ItemStack juice = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) e.get(0))));
                press.count -= need; if (press.count == 0) press.fruit = "";
                press.changed(level, pos);
                if (!player.getAbilities().instabuild) held.shrink(1);
                if (!player.getInventory().add(juice)) player.drop(juice, false);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.7f, 0.9f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Breaking it gives the fruit back. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FruitPressBlockEntity press && !press.fruit.isEmpty() && press.count > 0)
            Block.popResource(level, pos, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(press.fruit)), press.count));
        super.onRemove(state, level, pos, newState, moved);
    }
}
