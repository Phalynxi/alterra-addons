package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.FiniteWater;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** An empty bucket can be aimed at any water, not only full blocks (finite water is rarely full). */
@Mixin(BucketItem.class)
public abstract class BucketAimMixin {
    @Shadow @Final public Fluid content;

    @ModifyArg(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BucketItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"), index = 2)
    private ClipContext.Fluid alterra$anyWater(ClipContext.Fluid mode) { return content == Fluids.EMPTY && FiniteWater.on() ? ClipContext.Fluid.ANY : mode; }
}
