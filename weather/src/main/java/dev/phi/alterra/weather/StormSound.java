package dev.phi.alterra.weather;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A wind loop that follows the player and fades smoothly towards the volume the storm asks for. */
final class StormSound extends AbstractTickableSoundInstance {
    private float goal;
    private boolean ending;

    StormSound(SoundEvent event) {
        super(event, SoundSource.WEATHER, RandomSource.create());
        looping = true; delay = 0; volume = 0.001f; relative = true; attenuation = SoundInstance.Attenuation.NONE;
    }
    void target(float v) { goal = v; }
    boolean faded() { return volume <= 0.01f; }
    void end() { ending = true; goal = 0f; }

    @Override
    public void tick() {
        volume += Mth.clamp(goal - volume, -0.012f, 0.012f);
        if (ending && volume <= 0.004f) stop();
    }
}
