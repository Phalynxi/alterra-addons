package dev.phi.alterra.illuminate.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.phi.alterra.illuminate.look.SeaMotion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Boats (and chest boats and rafts) are drawn with a little bob and lean from the waves under them. */
@Mixin(value = BoatRenderer.class, remap = false)
public abstract class BoatRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), remap = false, require = 0)
    private void alterra$waveStart(Boat boat, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ps.pushPose(); SeaMotion.lift(boat, ps);
    }
    @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"), remap = false, require = 0)
    private void alterra$waveEnd(Boat boat, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ps.popPose();
    }
}
