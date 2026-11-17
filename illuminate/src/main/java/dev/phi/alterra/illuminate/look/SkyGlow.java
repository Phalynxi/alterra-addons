package dev.phi.alterra.illuminate.look;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The colour of the sky at the horizon, with the real sunset and sunrise glow in it (the game's own: orange, then red, at the right moments), for everything that mirrors or hazes into the sky. */
public final class SkyGlow {
    private SkyGlow() {}

    /** The horizon colour without the sunset glow (the glow is directional: see {@link #glow}). */
    public static float[] horizon(ClientLevel level, Vec3 skyColour, float day, float partial) {
        return new float[] {(float) Math.min(1, skyColour.x * 0.8 + 0.16 * day), (float) Math.min(1, skyColour.y * 0.8 + 0.18 * day), (float) Math.min(1, skyColour.z * 0.8 + 0.20 * day)};
    }

    /** The game's own sunrise and sunset glow: colour and strength (zero when there is none right now). It belongs to the side of the sky where the sun is. */
    public static float[] glow(ClientLevel level, float partial) {
        float[] g = level.effects().getSunriseColor(level.getTimeOfDay(partial), partial);
        return g == null ? new float[] {0f, 0f, 0f, 0f} : new float[] {g[0], g[1], g[2], Mth.clamp(g[3], 0f, 1f)};
    }

    /** Where the sun really is (it stays right after the sun has gone down; the shaders' own sun direction turns into the moon's). */
    public static float[] sunTrue(ClientLevel level, float partial) {
        double a = level.getTimeOfDay(partial) * Math.PI * 2;
        return new float[] {(float) -Math.sin(a), (float) Math.cos(a), 0f};
    }
}
