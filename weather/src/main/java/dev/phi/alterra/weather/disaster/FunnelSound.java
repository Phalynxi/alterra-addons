package dev.phi.alterra.weather.disaster;

import dev.phi.alterra.weather.AlterraWeather;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The roar of a tornado or dust devil as one continuous sound that follows it: its loudness comes from how near and strong it is, its direction pans it between the ears, and it
 * fades in and out instead of starting and stopping. (It used to be a dust-wind one-shot started every second by the server, copies piling up on each other.)
 */
public final class FunnelSound extends AbstractTickableSoundInstance {
    private float goal, goalPitch = 1f; private double dx, dz;
    private boolean ending;

    FunnelSound() {
        super(AlterraWeather.TORNADO_ROAR.get(), SoundSource.WEATHER, RandomSource.create());
        this.looping = true; this.delay = 0; this.volume = 0.001f; this.relative = true; this.attenuation = SoundInstance.Attenuation.NONE;
    }

    /** Called every tick by DisasterClient: the wanted loudness and pitch, and the way to the funnel (a unit vector, for panning). */
    void target(float volume, float pitch, double dirX, double dirZ) { goal = volume; goalPitch = pitch; dx = dirX; dz = dirZ; ending = false; }
    void end() { goal = 0f; ending = true; }
    boolean faded() { return ending && volume < 0.01f; }

    @Override
    public void tick() {
        volume += (goal - volume) * 0.06f;
        pitch += (goalPitch - pitch) * 0.04f;
        x = dx * 3.0; y = 0.0; z = dz * 3.0;   // relative to the listener: only the direction matters (no distance falloff), for panning
        if (faded()) stop();
    }
}
