package dev.phi.alterra.kitchen.mixin;

import dev.phi.alterra.kitchen.DrinkMixerBlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** A mechanical mixer mixes any drinks with no written recipe into a mixed drink (DrinkMixerBlockEntity): three or four in the Drink Mixer, two in any basin. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity", remap = false)
public abstract class MixerDrinksMixin {
    @Inject(method = "getMatchingRecipes", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void alterra$drinks(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null || !(self.getLevel().getBlockEntity(self.getBlockPos().below(2)) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin)) return;
        boolean mixer = basin instanceof DrinkMixerBlockEntity;
        // three or more drinks in the Drink Mixer always make a mixed drink; two drinks in any basin do too, when no written recipe takes them
        if ((mixer && DrinkMixerBlockEntity.canMix(basin, 3)) || (cir.getReturnValue().isEmpty() && DrinkMixerBlockEntity.canMix(basin, 2))) {
            List<Recipe<?>> l = new ArrayList<>(); l.add(DrinkMixerBlockEntity.MARKER); cir.setReturnValue(l);
        }
    }
}
