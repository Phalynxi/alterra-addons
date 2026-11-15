package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.Hulls;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No underwater fog inside a ship's hull below the waterline. */
@Mixin(Camera.class)
public abstract class CameraHullMixin {
    @Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true)
    private void alterra$dryHull(CallbackInfoReturnable<FogType> cir) {
        if (cir.getReturnValue() != FogType.WATER) return;
        Camera self = (Camera) (Object) this;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var p = self.getPosition();
        if (level != null && Hulls.dry(level, p.x, p.y, p.z)) cir.setReturnValue(FogType.NONE);
    }
}
