package dev.phi.alterra.weather;

import dev.phi.alterra.weather.disaster.DisasterClient;
import dev.phi.alterra.weather.storm.StormRain;
import dev.phi.alterra.weather.water.Foam;
import dev.phi.alterra.weather.water.Ripples;
import dev.phi.alterra.weather.water.Splashes;
import dev.phi.alterra.weather.water.SubLevels;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client-only: the weather particles. */
public final class WeatherClient {
    private WeatherClient() {}

    public static void init(IEventBus bus) {
        WeatherHooks.disasters = DisasterClient::received;
        bus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent e) -> e.register(net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"), new StormRain()));   // wind-blown rain
        bus.addListener((RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(AlterraWeather.RIPPLE.get(), sp -> (t, l, x, y, z, dx, dy, dz) -> new Ripple(l, x, y, z, sp));
            e.registerSpriteSet(AlterraWeather.CLUMP.get(), sp -> (t, l, x, y, z, dx, dy, dz) -> new Clump(l, x, y, z, sp, (float) dx));
            e.registerSpriteSet(AlterraWeather.SPRAY.get(), sp -> { spraySprites = sp; return (t, l, x, y, z, dx, dy, dz) -> new Spray(l, x, y, z, dx, dy, dz, sp); });
            e.registerSpecial(net.minecraft.core.particles.ParticleTypes.RAIN, (t, l, x, y, z, dx, dy, dz) -> spraySprites == null ? null : new RainHit(l, x, y, z, spraySprites));   // raindrops hitting the ground: our small water drops instead of vanilla's
            e.registerSpriteSet(AlterraWeather.FOAM.get(), sp -> (t, l, x, y, z, dx, dy, dz) -> new Foam(l, x, y, z, sp, dx, dz));
            e.registerSpriteSet(AlterraWeather.DUST.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Dust(l, x, y, z, dx, dy, dz, s, o));
            e.registerSpriteSet(AlterraWeather.PUFF.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Puff(l, x, y, z, dx, dy, dz, s, o));
            e.registerSpriteSet(AlterraWeather.DRIFT.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Drift(l, x, y, z, dx, dy, dz, s, o));
            e.registerSpriteSet(AlterraWeather.PRINT.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Print(l, x, y, z, s, o, (int) dx, (float) dy, (int) dz));   // dx: which foot, dy: facing (radians), dz: how long it lasts (ticks)
            e.registerSpriteSet(AlterraWeather.DROP.get(), sp -> (t, l, x, y, z, dx, dy, dz) -> new Droplet(l, x, y, z, dx, dy, dz, sp));
            e.registerSpriteSet(AlterraWeather.HAIL.get(), sp -> (t, l, x, y, z, dx, dy, dz) -> new HailStone(l, x, y, z, dx, dy, dz, sp));
            e.registerSpriteSet(AlterraWeather.MOTE.get(), s -> (o, l, x, y, z, dx, dy, dz) -> new Mote(l, x, y, z, dx, dy, dz, s, o));
        });
    }

    public static SpriteSet spraySprites;

    /** A raindrop landing: a pixel of water, the sea's own blue and see-through, that bounces up a little and falls back, fading. */
    private static final class RainHit extends TextureSheetParticle {
        RainHit(ClientLevel l, double x, double y, double z, SpriteSet s) {
            super(l, x, y + 0.02, z);
            pickSprite(s); hasPhysics = true; gravity = 0.9f; friction = 0.95f;
            xd = (random.nextDouble() - 0.5) * 0.06; zd = (random.nextDouble() - 0.5) * 0.06; yd = 0.06 + random.nextDouble() * 0.07;
            lifetime = 6 + random.nextInt(5); quadSize = 0.025f + random.nextFloat() * 0.02f;
            rCol = 0.62f; gCol = 0.80f; bCol = 0.95f; alpha = 0.7f;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public void tick() { super.tick(); alpha = 0.7f * Math.min(1f, (lifetime - age) / 4f); }
    }

    /** A drop of water thrown up and out. It is a small block of water: tinted like the sea, shaded by face, stretched along its fall, with real physics (gravity, drag that slows small drops more, ships and blocks are solid), and where it falls back it leaves a ripple. */
    private static final class Spray extends TextureSheetParticle {
        private final float size; private final float tint;
        Spray(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z);
            xd = dx; yd = dy; zd = dz; gravity = 0f; friction = 1f; hasPhysics = false;
            lifetime = 50; size = 0.05f + random.nextFloat() * random.nextFloat() * 0.1f; quadSize = size; tint = random.nextFloat(); pickSprite(s);
            rCol = 0.36f + 0.1f * tint; gCol = 0.68f + 0.08f * tint; bCol = 0.92f; alpha = 0.8f;   // the sea's own blue, not white
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) { remove(); return; }
            quadSize = size * Math.min(1f, 0.4f + age / 3f) * Math.min(1f, (lifetime - age) / 6f);
            double sp = Math.sqrt(xd * xd + yd * yd + zd * zd), k = 1.0 - Math.min(0.25, sp * 0.0045 / size);   // drag grows with speed and bites small drops harder
            xd *= k; yd = yd * k - 0.062; zd *= k;
            double[] hit = SubLevels.collide(x, y, z, xd, yd, zd);   // a ship is solid: the drop bounces off its hull or stops on its deck
            if (hit != null) { if (hit[6] > 0) { remove(); return; } setPos(hit[0], hit[1], hit[2]); xd = hit[3]; yd = hit[4]; zd = hit[5]; }
            double nx = x + xd, ny = y + yd, nz = z + zd;
            var bp = net.minecraft.core.BlockPos.containing(nx, ny, nz);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) { remove(); return; }   // it hits the ground or a wall and is gone into it
            setPos(nx, ny, nz);
            if (yd < 0 && level.getFluidState(net.minecraft.core.BlockPos.containing(x, y - size * 0.5, z)).is(net.minecraft.tags.FluidTags.WATER)) {
                if (SubLevels.inside(x, z)) { remove(); return; }   // into a hull: the water there is hidden
                if (random.nextInt(3) == 0) Ripples.add(x, z, 0.1f + size * 1.5f);
                if (size > 0.1f && yd < -0.25 && random.nextBoolean()) level.addParticle(AlterraWeather.SPRAY.get(), x, Splashes.surface(level, x, y, z) + 0.05, z, (random.nextDouble() - 0.5) * 0.06, -yd * 0.3, (random.nextDouble() - 0.5) * 0.06);   // a big one bounces a smaller one back up
                remove();
            }
        }
    }

    /** A clump of white foam lying on the water: it swells a little as it settles, drifts, and thins out. dx is its size. */
    public static final class Clump extends Flat {
        private final float size;
        Clump(ClientLevel l, double x, double y, double z, SpriteSet s, float sizeIn) {
            super(l, x, y, z); pickSprite(s); lifetime = 24 + random.nextInt(18); size = sizeIn; quadSize = size * 0.6f; alpha = 0f;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI; xd = (random.nextFloat() - 0.5f) * 0.01f; zd = (random.nextFloat() - 0.5f) * 0.01f;
        }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime || SubLevels.inside(x, z)) { remove(); return; }
            x += xd; z += zd;
            float t = (float) age / lifetime;
            quadSize = size * (0.6f + 0.55f * Mth.sqrt(t));
            alpha = fade(0.12f, 0.6f) * 0.95f;
        }
    }

    /** A particle that lies flat on the water: drawn as a horizontal square, turned by its roll. */
    private abstract static class Flat extends TextureSheetParticle {
        Flat(ClientLevel l, double x, double y, double z) { super(l, x, y, z); hasPhysics = false; friction = 1f; gravity = 0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override
        public void render(com.mojang.blaze3d.vertex.VertexConsumer vc, net.minecraft.client.Camera cam, float pt) {
            var c = cam.getPosition();
            float px = (float) (Mth.lerp(pt, xo, x) - c.x), py = (float) (Mth.lerp(pt, yo, y) - c.y), pz = (float) (Mth.lerp(pt, zo, z) - c.z);
            float s = getQuadSize(pt), cs = Mth.cos(roll) * s, sn = Mth.sin(roll) * s;
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1(); int light = getLightColor(pt);
            // counter-clockwise seen from above, so the face looks up (the other way round it would be culled)
            vc.addVertex(px - cs - sn, py, pz - sn + cs).setUv(u1, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
            vc.addVertex(px + cs - sn, py, pz + sn + cs).setUv(u0, v1).setColor(rCol, gCol, bCol, alpha).setLight(light);
            vc.addVertex(px + cs + sn, py, pz + sn - cs).setUv(u0, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
            vc.addVertex(px - cs + sn, py, pz - sn - cs).setUv(u1, v0).setColor(rCol, gCol, bCol, alpha).setLight(light);
        }
        float fade(float in, float out) { float t = (float) age / lifetime; return Math.min(1f, Math.min(t / in, (1f - t) / out)); }
    }

    /** A ring spreading out where a raindrop hit the water. */
    public static final class Ripple extends Flat {
        private final float size;
        Ripple(ClientLevel l, double x, double y, double z, SpriteSet s) {
            super(l, x, y, z); pickSprite(s); lifetime = 12 + random.nextInt(8); size = 0.12f + random.nextFloat() * 0.12f; quadSize = size; alpha = 0f;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        }
        @Override public void tick() { super.tick(); float t = (float) age / lifetime; quadSize = size * (0.35f + 2.6f * t); alpha = fade(0.12f, 1f) * (1f - t) * 0.9f; }
    }

    /** A drop of rain thrown up where a raindrop hit something: small, light, arcs up and falls back, gone in a moment. */
    public static final class Droplet extends TextureSheetParticle {
        private final float size;
        Droplet(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z); pickSprite(s); xd = dx; yd = dy; zd = dz; gravity = 0f; hasPhysics = false; friction = 1f;
            lifetime = 7 + random.nextInt(6); size = 0.04f + random.nextFloat() * 0.035f; quadSize = size;
            rCol = 0.86f; gCol = 0.93f; bCol = 1.0f; alpha = 0.7f;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) { remove(); return; }
            x += xd; y += yd; z += zd; yd -= 0.022; xd *= 0.92; zd *= 0.92;
            alpha = 0.7f * Math.min(1f, (lifetime - age) / 3f); quadSize = size * (1f - 0.35f * age / lifetime);
        }
    }

    /** A hailstone: falls straight and fast, bounces twice on whatever it lands on (a little less each time), then is gone. */
    public static final class HailStone extends TextureSheetParticle {
        private int bounces;
        HailStone(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s) {
            super(l, x, y, z); pickSprite(s); xd = dx; yd = dy; zd = dz; gravity = 0f; hasPhysics = false; friction = 1f; lifetime = 80; quadSize = 0.07f + random.nextFloat() * 0.04f;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) { remove(); return; }
            for (int k = 0; k < 2; k++) {   // two half steps: a fast stone must not skip over a thin floor
                double nx = x + xd * 0.5, ny = y + yd * 0.5, nz = z + zd * 0.5;
                var bp = net.minecraft.core.BlockPos.containing(nx, ny, nz); var st = level.getBlockState(bp);
                if (!st.getFluidState().isEmpty()) { remove(); return; }
                var shape = st.getCollisionShape(level, bp);
                if (yd < 0 && !shape.isEmpty() && ny < bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y)) {
                    if (++bounces > 2) { remove(); return; }
                    y = bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.02; yd = 0.17 + random.nextDouble() * 0.12 - bounces * 0.04; xd *= 0.45; zd *= 0.45; continue;
                }
                x = nx; y = ny; z = nz;
            }
            if (bounces > 0) yd -= 0.05;
        }
    }

    /** A boot print lying flat on snow, sand or mud: dark and soft, there for a while, then it fills in and fades. */
    public static final class Print extends Flat {
        Print(ClientLevel l, double x, double y, double z, SpriteSet s, ColorParticleOption c, int foot, float facing, int life) {
            super(l, x, y, z); setSprite(s.get(1 - foot, 1));   // (the quad is seen mirrored from above, so the left foot takes the mirrored picture)
            setColor(c.getRed(), c.getGreen(), c.getBlue());
            lifetime = life; quadSize = 0.19f; alpha = 0f; roll = oRoll = facing + Mth.PI;
        }
        @Override public void tick() {
            super.tick(); alpha = fade(0.02f, 0.45f) * 0.6f;
            if ((age & 3) == 0) {   // the print lies on a block: if that goes (it is broken or dug away) or something now stands in its place, the print goes with it
                var under = level.getBlockState(net.minecraft.core.BlockPos.containing(x, y - 0.03, z));
                var on = level.getBlockState(net.minecraft.core.BlockPos.containing(x, y + 0.03, z));
                if (under.isAir() || under.canBeReplaced() && !under.is(net.minecraft.world.level.block.Blocks.SNOW) || !on.isAir() && on.isSolidRender(level, net.minecraft.core.BlockPos.containing(x, y + 0.03, z))) remove();
            }
        }
    }

    /** A patch of foam at the shore: it runs up towards the land and slips back, and thins out. */
    public static final class Foam extends Flat {
        private final double dx, dz; private final float size, phase;
        Foam(ClientLevel l, double x, double y, double z, SpriteSet s, double dx, double dz) {
            super(l, x, y, z); pickSprite(s); this.dx = dx; this.dz = dz; lifetime = 50 + random.nextInt(40); size = 0.32f + random.nextFloat() * 0.3f; quadSize = size;
            phase = random.nextFloat() * Mth.TWO_PI; roll = oRoll = random.nextFloat() * Mth.TWO_PI; alpha = 0f;
        }
        @Override public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) { remove(); return; }
            float t = (float) age / lifetime, wave = Mth.sin(t * Mth.TWO_PI * 1.3f + phase);   // up the beach and back
            x += dx * 0.012 * wave; z += dz * 0.012 * wave;
            quadSize = size * (0.8f + 0.25f * wave);
            alpha = fade(0.25f, 0.45f) * 0.8f;
        }
    }

    private abstract static class Base extends TextureSheetParticle {
        Base(ClientLevel l, double x, double y, double z) { super(l, x, y, z); hasPhysics = false; friction = 1f; gravity = 0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        void tint(ColorParticleOption c, float spread) { float k = 1f - spread + random.nextFloat() * spread * 2f; setColor(Math.min(1f, c.getRed() * k), Math.min(1f, c.getGreen() * k), Math.min(1f, c.getBlue() * k)); }
        float fade(float in, float out) { float t = (float) age / lifetime; return Math.min(1f, Math.min(t / in, (1f - t) / out)); }
    }

    /** A speck blown straight along with the wind (grit, driven snow): fast, short-lived, fading in and out. */
    public static final class Dust extends Base {
        Dust(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, ColorParticleOption c) {
            super(l, x, y, z);
            pickSprite(s); tint(c, 0.12f);
            lifetime = 28 + random.nextInt(24); quadSize = 0.045f + random.nextFloat() * 0.07f;
            xd = dx; yd = dy + (random.nextDouble() - 0.5) * 0.03; zd = dz; alpha = 0f;
        }
        @Override public void tick() { super.tick(); alpha = fade(0.15f, 0.25f) * 0.85f; }
    }

    /** A big soft cloud of dust, snow or mist drifting past: the body of a storm. */
    public static final class Puff extends Base {
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

    /** A low veil of sand or snow skimming the ground with the wind: small, soft and see-through, there and gone. */
    public static final class Drift extends Base {
        private final float size, maxAlpha;
        Drift(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, ColorParticleOption c) {
            super(l, x, y, z);
            pickSprite(s); tint(c, 0.05f);
            lifetime = 24 + random.nextInt(22); size = 0.45f + random.nextFloat() * 0.7f; quadSize = size;
            maxAlpha = 0.22f + random.nextFloat() * 0.18f; roll = oRoll = random.nextFloat() * 0.4f - 0.2f;
            xd = dx; yd = dy; zd = dz; alpha = 0f;
        }
        @Override public void tick() { super.tick(); alpha = fade(0.25f, 0.4f) * maxAlpha; quadSize = size * (0.85f + 0.4f * (float) age / lifetime); }
    }

    /** A slow, swaying mote: pollen floating up, ash falling. */
    public static final class Mote extends Base {
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
