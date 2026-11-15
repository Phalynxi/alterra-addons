package dev.phi.alterra.weather.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.phi.alterra.weather.disaster.Disasters;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks a tornado has torn loose (marked by their start position) tumble as they fly, each about its own axis and at its own speed. */
@Mixin(FallingBlockRenderer.class)
public abstract class DebrisSpinMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void alterra$spinBegin(FallingBlockEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci) {
        ps.pushPose();
        if (!e.getStartPos().equals(Disasters.SPIN_MARK)) return;
        int id = e.getId(); float t = e.tickCount + pt;
        ps.translate(0.0, 0.5, 0.0);   // about the middle of the block
        ps.mulPose(new Quaternionf().rotationAxis(t * (0.18f + (id % 7) * 0.035f), new Vector3f(((id * 37) % 11 - 5) * 0.1f + 0.3f, 1f, ((id * 53) % 13 - 6) * 0.1f).normalize()));
        ps.translate(0.0, -0.5, 0.0);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void alterra$spinEnd(FallingBlockEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci) { ps.popPose(); }
}
