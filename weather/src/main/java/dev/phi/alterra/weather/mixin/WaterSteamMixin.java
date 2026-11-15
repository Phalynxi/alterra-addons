package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.FiniteWater;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Heated water steams and bubbles (client). */
@Mixin(WaterFluid.class)
public abstract class WaterSteamMixin {
    @Inject(method = "animateTick", at = @At("TAIL"))
    private void alterra$steam(Level level, BlockPos pos, FluidState state, RandomSource r, CallbackInfo ci) { FiniteWater.animate(level, pos, state, r); }
}
