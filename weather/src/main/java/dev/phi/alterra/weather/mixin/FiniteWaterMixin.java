package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.FiniteWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water spreads by FiniteWater instead of vanilla's rules (on the server, when finiteWater is on). */
@Mixin(FlowingFluid.class)
public abstract class FiniteWaterMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void alterra$finite(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (!((Object) this instanceof WaterFluid) || level.isClientSide || !FiniteWater.on()) return;
        dev.phi.alterra.weather.water.WaterSim.wake(level, pos);   // the continuous simulation moves it
        ci.cancel();
    }
}
