package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.Surge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create: Deep Seas keeps its own buoyancy for ships; it only learns where the storm surge has put the top of the sea, so ships ride up with it. */
@Pseudo
@Mixin(targets = "com.maxenonyme.highseas.BoatBuoyancySystem", remap = false)
public abstract class HighSeasFloatMixin {
    @Inject(method = "surfaceNear", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void alterra$surge(ServerLevel level, double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        double top = Surge.surface(level, x, y, z);
        if (!Double.isNaN(top) && top > cir.getReturnValue()) cir.setReturnValue(top);
    }
}
