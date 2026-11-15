package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.storm.CloudMap;
import dev.phi.alterra.weather.config.HeavyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** It only rains where a thick cloud is overhead (CloudMap): the game's own check, so crops, fire, mobs, filling cauldrons and the rest follow the clouds too. */
@Mixin(Level.class)
public abstract class CloudRainMixin {
    @Inject(method = "isRainingAt", at = @At("RETURN"), cancellable = true)
    private void alterra$underClouds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && HeavyConfig.get(HeavyConfig.CLOUD_RAIN) && !CloudMap.rainsHere((Level) (Object) this, pos.getX() + 0.5, pos.getZ() + 0.5)) cir.setReturnValue(false);
    }
}
