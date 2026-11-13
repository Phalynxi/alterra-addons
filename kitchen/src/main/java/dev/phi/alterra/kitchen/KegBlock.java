package dev.phi.alterra.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

/** Keg: put in bottled juice, wait, and take out wine (or cider, mead...) with a glass bottle. What turns into what is in alterra_kitchen_ferment.json. */
public class KegBlock extends BaseEntityBlock {
    public static final MapCodec<KegBlock> CODEC = simpleCodec(KegBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 2);   // 0 empty, 1 fermenting, 2 ready
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);
    public static final int CAPACITY = 8;
    private static Map<String, Map<String, Object>> table;

    public KegBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FILL, 0)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FILL); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new KegBlockEntity(pos, state); }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> table() {
        if (table == null) {
            try (var in = KegBlock.class.getResourceAsStream("/alterra_kitchen_ferment.json")) {
                table = new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in), Map.class);
            } catch (Exception e) { table = Map.of(); }
        }
        return table;
    }
    private static String id(ItemStack st) { return BuiltInRegistries.ITEM.getKey(st.getItem()).toString(); }
    private static ItemStack stack(String id, int n) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), n); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof KegBlockEntity keg)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var entry = table().get(id(held));
        if (entry != null && (keg.count == 0 || keg.input.equals(id(held))) && keg.count < CAPACITY && !keg.ready(level)) {   // put a bottle in
            if (!level.isClientSide) {
                int n = Math.min(player.isShiftKeyDown() ? held.getCount() : 1, CAPACITY - keg.count);
                if (keg.count == 0) { keg.input = id(held); keg.output = (String) entry.get("out"); keg.readyAt = level.getGameTime() + ((Number) entry.get("ticks")).longValue(); }
                keg.count += n;
                if (!player.getAbilities().instabuild) held.shrink(n);
                keg.changed(level, pos);
                level.scheduleTick(pos, this, (int) Math.max(1, keg.readyAt - level.getGameTime()));
                level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.8f, 1.1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(Items.GLASS_BOTTLE) && keg.count > 0) {   // take a bottle out
            if (!keg.ready(level)) {
                if (!level.isClientSide) status(keg, level, player);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                ItemStack out = stack(keg.output, 1);
                keg.count--; if (keg.count == 0) { keg.input = ""; keg.output = ""; }
                keg.changed(level, pos);
                if (!player.getAbilities().instabuild) held.shrink(1);
                if (!player.getInventory().add(out)) player.drop(out, false);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 0.9f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof KegBlockEntity keg) {
            if (!level.isClientSide) status(keg, level, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static void status(KegBlockEntity keg, Level level, Player player) {
        String msg;
        if (keg.count == 0) msg = "Empty. Put in bottled juice.";
        else if (keg.ready(level)) msg = keg.count + (keg.count == 1 ? " bottle" : " bottles") + " ready. Use a glass bottle.";
        else {
            long left = Math.max(1, (keg.readyAt - level.getGameTime()) / 1200);
            msg = "Still fermenting, about " + left + (left == 1 ? " minute" : " minutes") + " left.";
        }
        player.displayClientMessage(Component.literal(msg), true);
    }

    /** Switches the look to "ready" when the time comes. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof KegBlockEntity keg) {
            keg.changed(level, pos);
            if (keg.count > 0 && !keg.ready(level)) level.scheduleTick(pos, this, (int) Math.max(1, keg.readyAt - level.getGameTime()));
        }
    }

    /** Breaking it gives the bottles back. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KegBlockEntity keg && keg.count > 0) {
            Block.popResource(level, pos, stack(keg.ready(level) ? keg.output : keg.input, keg.count));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
