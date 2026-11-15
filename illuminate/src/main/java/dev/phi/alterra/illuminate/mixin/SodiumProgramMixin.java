package dev.phi.alterra.illuminate.mixin;

import dev.phi.alterra.illuminate.look.Foliage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium draws the world with its own shader programs. Whenever one is bound, hand it the sway parameters (only the patched block shader has the uniforms). */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.GlProgram", remap = false)
public abstract class SodiumProgramMixin {
    @Inject(method = "bind", at = @At("TAIL"), remap = false, require = 0)
    private void alterra$sway(CallbackInfo ci) { Foliage.upload(Foliage.handleOf(this)); }
}
