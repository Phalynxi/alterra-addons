package dev.phi.alterra.weather.mixin;

import dev.phi.aethryon.api.AethryonApi;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create: Deep Seas (High Seas) sails on our wind: its regional wind is replaced by Alterra Weather's (which already follows rain, thunder and the heavy storms), in its units (its sails are tuned to about 9 at full strength).
 * Its own height, biome and shelter factors still apply on top. Only applied when Deep Seas is installed.
 */
@Pseudo
@Mixin(targets = "com.maxenonyme.highseas.wind.WindManager", remap = false)
public abstract class HighSeasWindMixin {
    @Inject(method = "regionalWind", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void alterra$ourWind(Level level, double x, double z, CallbackInfoReturnable<Vec3> cir) {
        Vec3 w = AethryonApi.wind().at(level, x, z);
        cir.setReturnValue(new Vec3(w.x * 6.0, 0.0, w.z * 6.0));
    }

    @Inject(method = "weatherMult", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void alterra$weatherInWind(Level level, CallbackInfoReturnable<Double> cir) { cir.setReturnValue(1.0); }   // the weather is already in our wind
}
