package dev.phi.alterra.weather.mixin;

import dev.phi.aethryon.api.AethryonApi;
import dev.phi.aethryon.api.Disturbance;
import dev.phi.alterra.weather.compat.SableLink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A wheel of an Offroad vehicle on the ground tells the world about it: a WHEEL disturbance at the contact, the way the vehicle is going and how hard it presses.
 * Snow takes the track, and whatever else listens (sand, dust, mud, water) takes its own. Only while the wheel is down and the vehicle is moving.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity", remap = false)
public abstract class WheelMountMixin {
    private Vec3 alterra$last;

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void alterra$track(CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this; var level = be.getLevel(); if (level == null) return;
        if (Boolean.TRUE.equals(SableLink.field(this, "liftedUp"))) { alterra$last = null; return; }   // the wheel is lifted up: no contact
        Vec3 hub = SableLink.toWorld(level, Vec3.atCenterOf(be.getBlockPos())), last = alterra$last; alterra$last = hub;
        if (last == null) return;
        double vx = hub.x - last.x, vz = hub.z - last.z, speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < 0.012 || speed > 3.0) return;   // standing, or teleported
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(hub.x), (int) Math.floor(hub.z));
        if (hub.y - gy > 2.6 || hub.y < gy - 1.0) return;   // in the air (or under the ground): not touching
        float energy = (float) Math.min(1.0, 0.45 + speed * 1.2);
        AethryonApi.disturb(level, new Disturbance(Disturbance.Kind.WHEEL, hub.x, gy, hub.z, 0.7f, energy, vx / speed, 0, vz / speed));
    }
}
