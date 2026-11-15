package dev.phi.alterra.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The prep table, the kitchen counter and the ice cream stand: stations where food is assembled on a plate (see AssemblyMenu); what is on them stays in the block. */
public class KitchenCounterBlock extends CraftingTableBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final MapCodec<CraftingTableBlock> CODEC = simpleCodec(p -> new KitchenCounterBlock(p, AssemblyMenu.Layout.COUNTER));
    private final AssemblyMenu.Layout layout;
    public KitchenCounterBlock(Properties props, AssemblyMenu.Layout layout) { super(props); this.layout = layout; }
    public AssemblyMenu.Layout layout() { return layout; }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AssemblyBlockEntity(pos, state); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof AssemblyBlockEntity be) net.minecraft.world.Containers.dropContents(level, pos, be.items());
        super.onRemove(state, level, pos, next, moved);
    }
    @Override public MapCodec<CraftingTableBlock> codec() { return CODEC; }
    @Override public net.minecraft.network.chat.MutableComponent getName() { return Component.literal(layout.title); }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof AssemblyBlockEntity be)
            player.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AssemblyMenu(id, inv, layout, ContainerLevelAccess.create(level, pos), this, be.items()), Component.literal(layout.title)), buf -> buf.writeInt(layout.ordinal()));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

}
