package dev.phi.alterra.illuminate.mixin;

import dev.phi.alterra.illuminate.look.Foliage;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium reads its shader text through this method; the block vertex shader comes back with the sway code in it. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void alterra$patch(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        String patched = Foliage.patchedSodium(name);
        if (patched != null) cir.setReturnValue(patched);
    }
}
