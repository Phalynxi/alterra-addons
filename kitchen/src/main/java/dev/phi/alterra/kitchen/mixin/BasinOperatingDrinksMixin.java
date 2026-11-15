package dev.phi.alterra.kitchen.mixin;

import dev.phi.alterra.kitchen.DrinkMixerBlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When the mixer finishes a round over a Drink Mixer, the drinks are mixed into a bottle, and it goes on while there is more to mix. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity", remap = false)
public abstract class BasinOperatingDrinksMixin {
    @Shadow(remap = false) protected Recipe<?> currentRecipe;
    @Shadow(remap = false) public abstract boolean continueWithPreviousRecipe();

    @Inject(method = "applyBasinRecipe", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void alterra$mixDrinks(CallbackInfo ci) {
        if (currentRecipe != DrinkMixerBlockEntity.MARKER) return;
        ci.cancel();
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null || !(self.getLevel().getBlockEntity(self.getBlockPos().below(2)) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity dm)) return;
        if (DrinkMixerBlockEntity.mixOnce(dm) && DrinkMixerBlockEntity.canMix(dm, 2)) continueWithPreviousRecipe();
        dm.notifyChangeOfContents();
        if (self instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity sbe) sbe.sendData();
    }
}
