package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.Surge;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Boats float on the storm surge: the raised sea is where their water line is, on the server and for the one steering alike. */
@Mixin(Boat.class)
public abstract class BoatSurgeMixin {
    @Shadow private double waterLevel;

    @Inject(method = "checkInWater", at = @At("RETURN"), cancellable = true)
    private void alterra$surgeIn(CallbackInfoReturnable<Boolean> cir) {
        Boat self = (Boat) (Object) this;
        double minY = self.getBoundingBox().minY, top = Surge.surface(self.level(), self.getX(), minY, self.getZ());
        if (Double.isNaN(top) || top <= minY) return;
        waterLevel = Math.max(waterLevel, top); cir.setReturnValue(true);
    }

    @Inject(method = "getWaterLevelAbove", at = @At("RETURN"), cancellable = true)
    private void alterra$surgeAbove(CallbackInfoReturnable<Float> cir) {
        Boat self = (Boat) (Object) this;
        double top = Surge.surface(self.level(), self.getX(), self.getBoundingBox().minY, self.getZ());
        if (!Double.isNaN(top) && top > cir.getReturnValue()) cir.setReturnValue((float) top);
    }
}
