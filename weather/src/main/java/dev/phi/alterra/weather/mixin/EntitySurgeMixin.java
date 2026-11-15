package dev.phi.alterra.weather.mixin;

import dev.phi.alterra.weather.water.Hulls;
import dev.phi.alterra.weather.water.Surge;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inside a ship's hull is dry (Hulls). The storm surge is real water: an entity in the raised part of the sea is in water (swims, floats, is slowed and breaks its fall) and its eyes can be under it, exactly as in water blocks. */
@Mixin(Entity.class)
public abstract class EntitySurgeMixin {
    @Shadow protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow protected boolean wasEyeInWater;
    @Shadow private FluidType forgeFluidTypeOnEyes;

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("TAIL"), remap = false)
    private void alterra$surge(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        double minY = self.getBoundingBox().minY, top = Surge.surface(self.level(), self.getX(), minY, self.getZ());
        FluidType water = NeoForgeMod.WATER_TYPE.value();
        if (!Double.isNaN(top) && top > minY && top - minY > forgeFluidTypeHeight.getDouble(water)) forgeFluidTypeHeight.put(water, top - minY);
        if (forgeFluidTypeHeight.containsKey(water) && Hulls.dry(self.level(), self.getX(), minY + 0.05, self.getZ())) forgeFluidTypeHeight.removeDouble(water);   // inside a ship's hull the sea does not reach
    }

    @Inject(method = "updateFluidOnEyes", at = @At("TAIL"))
    private void alterra$surgeEyes(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        double eye = self.getEyeY(), top = Surge.surface(self.level(), self.getX(), eye, self.getZ());
        if (!Double.isNaN(top) && top > eye) { wasEyeInWater = true; forgeFluidTypeOnEyes = NeoForgeMod.WATER_TYPE.value(); }
        if (wasEyeInWater && Hulls.dry(self.level(), self.getX(), eye, self.getZ())) { wasEyeInWater = false; forgeFluidTypeOnEyes = NeoForgeMod.EMPTY_TYPE.value(); }
    }
}
