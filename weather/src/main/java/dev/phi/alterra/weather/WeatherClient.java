package dev.phi.alterra.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client-only: the weather particles. */
final class WeatherClient {
    private WeatherClient() {}

    static void init(IEventBus bus) {
        bus.addListener((RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(AlterraWeather.DUST.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Dust(l, x, y, z, dx, dy, dz, s, o));
            e.registerSpriteSet(AlterraWeather.PUFF.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Puff(l, x, y, z, dx, dy, dz, s, o));
            e.registerSpriteSet(AlterraWeather.MOTE.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Mote(l, x, y, z, dx, dy, dz, s, o));
        });
    }

    private abstract static class Base extends TextureSheetParticle {
        Base(ClientLevel l, double x, double y, double z) { super(l, x, y, z); hasPhysics = false; friction = 1f; gravity = 0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        void tint(ColorParticleOption c, float spread) { float k = 1f - spread + random.nextFloat() * spread * 2f; setColor(Math.min(1f, c.getRed() * k), Math.min(1f, c.getGreen() * k), Math.min(1f, c.getBlue() * k)); }
        float fade(float in, float out) { float t = (float) age / lifetime; return Math.min(1f, Math.min(t / in, (1f - t) / out)); }
    }

    /** A speck blown straight along with the wind (grit, driven snow): fast, short-lived, fading in and out. */
    static final class Dust extends Base {
        Dust(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, ColorParticleOption c) {
            super(l, x, y, z);
            pickSprite(s); tint(c, 0.12f);
            lifetime = 28 + random.nextInt(24); quadSize = 0.045f + random.nextFloat() * 0.07f;
            xd = dx; yd = dy + (random.nextDouble() - 0.5) * 0.03; zd = dz; alpha = 0f;
        }
        @Override public void tick() { super.tick(); alpha = fade(0.15f, 0.25f) * 0.85f; }
    }

    /** A big soft cloud of dust, snow or mist drifting past: the body of a storm. */
    static final class Puff extends Base {
        private final float size, maxAlpha;
        Puff(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, ColorParticleOption c) {
            super(l, x, y, z);
            pickSprite(s); tint(c, 0.06f);
            lifetime = 70 + random.nextInt(50); size = 2.2f + random.nextFloat() * 3.4f; quadSize = size;
            maxAlpha = 0.10f + random.nextFloat() * 0.10f; roll = oRoll = random.nextFloat() * 0.3f - 0.15f;
            xd = dx; yd = dy; zd = dz; alpha = 0f;
        }
        @Override public void tick() { super.tick(); alpha = fade(0.3f, 0.35f) * maxAlpha; quadSize = size * (0.9f + 0.2f * (float) age / lifetime); }
    }

    /** A slow, swaying mote: pollen floating up, ash falling. */
    static final class Mote extends Base {
        private final float phase, sway, maxAlpha;
        Mote(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, ColorParticleOption c) {
            super(l, x, y, z);
            pickSprite(s); tint(c, 0.15f);
            lifetime = 90 + random.nextInt(80); quadSize = 0.05f + random.nextFloat() * 0.06f; maxAlpha = 0.65f + random.nextFloat() * 0.3f;
            phase = random.nextFloat() * Mth.TWO_PI; sway = 0.004f + random.nextFloat() * 0.006f;
            xd = dx; yd = dy; zd = dz; alpha = 0f;
        }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) { remove(); return; }
            float t = age * 0.12f + phase;
            x += xd + Mth.sin(t) * sway; z += zd + Mth.cos(t * 0.8f) * sway; y += yd + Mth.sin(t * 1.3f) * sway * 0.5f;
            alpha = fade(0.2f, 0.3f) * maxAlpha;
        }
    }
}
