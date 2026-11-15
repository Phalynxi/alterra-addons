package dev.phi.alterra.illuminate.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells FlowMap when water rises or falls somewhere, so the surface eases to its new level. */
@Mixin(LevelRenderer.class)
public abstract class WaterLevelChangeMixin {
    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void alterra$waterLevel(BlockGetter level, BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        dev.phi.alterra.illuminate.maps.FlowMap.changed(pos, before, after);
    }
}
