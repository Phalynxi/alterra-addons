package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.FiniteWater;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Finite water gets random ticks: shallow water dries up, heated water boils away. */
@Mixin(Fluid.class)
public abstract class WaterTicksMixin {
    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
    private void alterra$ticks(CallbackInfoReturnable<Boolean> cir) { if ((Object) this instanceof WaterFluid && FiniteWater.on()) cir.setReturnValue(true); }

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void alterra$dry(Level level, BlockPos pos, FluidState state, RandomSource r, CallbackInfo ci) { if ((Object) this instanceof WaterFluid && FiniteWater.on()) FiniteWater.randomTick(level, pos, state, r); }
}
