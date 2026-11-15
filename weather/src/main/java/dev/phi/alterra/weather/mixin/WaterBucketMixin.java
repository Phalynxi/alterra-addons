package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.FiniteWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With finite water a bucket scoops up a full bucket from the water around, even where no block is full. */
@Mixin(LiquidBlock.class)
public abstract class WaterBucketMixin {
    @Inject(method = "pickupBlock", at = @At("HEAD"), cancellable = true)
    private void alterra$scoop(Player player, LevelAccessor level, BlockPos pos, BlockState state, CallbackInfoReturnable<ItemStack> cir) {
        if (!state.is(Blocks.WATER) || !FiniteWater.on()) return;
        cir.setReturnValue(FiniteWater.takeBucket(level, pos) ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY);
    }
}
