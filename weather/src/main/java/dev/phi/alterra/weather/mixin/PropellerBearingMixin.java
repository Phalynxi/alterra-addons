package dev.phi.alterra.weather.mixin;

import dev.phi.aethryon.api.AethryonApi;
import dev.phi.aethryon.api.Disturbance;
import dev.phi.alterra.weather.compat.SableLink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A running propeller of Create Aeronautics blows: the air goes the way opposite to the thrust. Where that stream meets the ground (within about fifteen blocks) it is a WASH disturbance
 * there, strongest close to the ground: snow, sand and dust are lifted and carried outwards, water is pushed, things nearby are blown away. Every second tick, only while it turns.
 */
@Pseudo
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity", remap = false)
public abstract class PropellerBearingMixin {
    private int alterra$n; private boolean alterra$told;

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void alterra$blow(CallbackInfo ci) {
        if ((++alterra$n & 1) != 0) return;
        BlockEntity be = (BlockEntity) (Object) this; var level = be.getLevel(); if (level == null) return;
        if (!Boolean.TRUE.equals(SableLink.call(this, "isActive"))) return;
        Object thrust = SableLink.field(this, "thrustDirection"), flow = SableLink.call(this, "getAirflow");
        if (!(thrust instanceof Vector3d t) || !(flow instanceof Number af)) return;
        double len = t.length(); if (len < 1e-3) return;
        double ax = -t.x / len, ay = -t.y / len, az = -t.z / len;   // the air goes against the thrust
        float energy = (float) Math.min(1.5, Math.abs(af.doubleValue()) * 0.05);
        if (!alterra$told) { alterra$told = true; com.mojang.logging.LogUtils.getLogger().info("Alterra Weather: a propeller blows (airflow {}, thrust direction {} {} {}): ground wash {}", af, t.x, t.y, t.z, energy); }   // the numbers for tuning
        if (energy < 0.05f) return;
        Vec3 from = SableLink.toWorld(level, Vec3.atCenterOf(be.getBlockPos()));
        for (int s = 1; s <= 15; s++) {
            Vec3 p = from.add(ax * s, ay * s, az * s); BlockPos bp = BlockPos.containing(p);
            if (!level.isLoaded(bp) || level.getBlockState(bp).isAir()) continue;
            float near = 1f - s / 17f;   // the closer to the ground, the harder
            AethryonApi.disturb(level, new Disturbance(Disturbance.Kind.WASH, p.x, bp.getY() + 1, p.z, 2.5f + energy * 3f * near, energy * near, 0, 0, 0));
            return;
        }
    }
}
