package dev.phi.alterra.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

/** A stove or an oven: a furnace block with its own look, name and recipe type. */
public class KitchenFurnaceBlock extends AbstractFurnaceBlock {
    public static final MapCodec<KitchenFurnaceBlock> CODEC = simpleCodec(p -> new KitchenFurnaceBlock(p, null, null, "", false));
    private final Supplier<BlockEntityType<KitchenFurnaceEntity>> type;
    private final Supplier<? extends RecipeType<? extends AbstractCookingRecipe>> recipes;
    private final String title; private final boolean stove;

    public KitchenFurnaceBlock(Properties props, Supplier<BlockEntityType<KitchenFurnaceEntity>> type, Supplier<? extends RecipeType<? extends AbstractCookingRecipe>> recipes, String title, boolean stove) {
        super(props); this.type = type; this.recipes = recipes; this.title = title; this.stove = stove;
    }

    @Override protected MapCodec<? extends AbstractFurnaceBlock> codec() { return CODEC; }
    @Override public net.minecraft.network.chat.MutableComponent getName() { return net.minecraft.network.chat.Component.literal(title); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new KitchenFurnaceEntity(type.get(), pos, state, recipes.get(), title, stove); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) { return createFurnaceTicker(level, t, type.get()); }

    @Override protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof KitchenFurnaceEntity be) player.openMenu((MenuProvider) be);
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (recipes.get() == AlterraKitchen.FREEZING.get()) {   // a cold breath from the front instead of fire
            Direction f = state.getValue(FACING);
            if (random.nextDouble() < 0.3) level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5 + f.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.3 + random.nextDouble() * 0.4, pos.getZ() + 0.5 + f.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
            return;
        }
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.FURNACE_FIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f, false);
        Direction d = state.getValue(FACING); Direction.Axis axis = d.getAxis();
        double off = random.nextDouble() * 0.6 - 0.3, dx = axis == Direction.Axis.X ? d.getStepX() * 0.52 : off, dz = axis == Direction.Axis.Z ? d.getStepZ() * 0.52 : off;
        level.addParticle(ParticleTypes.SMOKE, x + dx, y + random.nextDouble() * 6.0 / 16.0, z + dz, 0, 0, 0);
        level.addParticle(ParticleTypes.FLAME, x + dx, y + random.nextDouble() * 6.0 / 16.0, z + dz, 0, 0, 0);
        if (stove) level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.6, y + 1.02, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);   // steam from the cooktop
    }
}
