package dev.phi.alterra.weather;

import dev.phi.prismlib.particle.Particle;
import dev.phi.prismlib.particle.ParticleKind;
import dev.phi.prismlib.particle.ParticleKind.Shape;
import dev.phi.prismlib.particle.Particles;
import dev.phi.alterra.weather.water.Ripples;
import dev.phi.alterra.weather.water.Splashes;
import dev.phi.alterra.weather.water.SubLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The weather's particles on Prism Lib's own engine: the kinds (how each looks and behaves) and {@link #add}, which takes what the old {@code level.addParticle} calls passed
 * (our particle types, a few vanilla ones that we draw ourselves) and makes the particle. Anything it does not know goes to the game's own engine (other mods' types).
 */
public final class WeatherParticles {
    private WeatherParticles() {}

    // ---- the kinds
    public static final ParticleKind
            RAIN_HIT = new ParticleKind(Shape.BILLBOARD, WeatherParticles::rainHit),
            SPRAY = new ParticleKind(Shape.BILLBOARD, WeatherParticles::spray),
            CLUMP = new ParticleKind(Shape.FLAT, WeatherParticles::clump),
            RIPPLE = new ParticleKind(Shape.FLAT, WeatherParticles::ripple),
            DROPLET = new ParticleKind(Shape.BILLBOARD, WeatherParticles::droplet),
            HAIL = new ParticleKind(Shape.BILLBOARD, WeatherParticles::hail),
            PRINT = new ParticleKind(Shape.FLAT, WeatherParticles::print),
            FOAM = new ParticleKind(Shape.FLAT, WeatherParticles::foam),
            DUST = new ParticleKind(Shape.BILLBOARD, WeatherParticles::dust),
            PUFF = new ParticleKind(Shape.BILLBOARD, WeatherParticles::puff),
            DRIFT = new ParticleKind(Shape.BILLBOARD, WeatherParticles::drift),
            MOTE = new ParticleKind(Shape.BILLBOARD, WeatherParticles::mote);

    /** Called from the particle provider registration: the sprite sets the game stitched for our types. */
    public static void spritesOf(net.minecraft.core.particles.ParticleType<?> type, net.minecraft.client.particle.SpriteSet set) {
        if (type == AlterraWeather.SPRAY.get()) { SPRAY.sprites(set); RAIN_HIT.sprites(set); }
        else if (type == AlterraWeather.RIPPLE.get()) RIPPLE.sprites(set);
        else if (type == AlterraWeather.CLUMP.get()) CLUMP.sprites(set);
        else if (type == AlterraWeather.DROP.get()) DROPLET.sprites(set);
        else if (type == AlterraWeather.HAIL.get()) HAIL.sprites(set);
        else if (type == AlterraWeather.PRINT.get()) PRINT.sprites(set);
        else if (type == AlterraWeather.FOAM.get()) FOAM.sprites(set);
        else if (type == AlterraWeather.DUST.get()) DUST.sprites(set);
        else if (type == AlterraWeather.PUFF.get()) PUFF.sprites(set);
        else if (type == AlterraWeather.DRIFT.get()) DRIFT.sprites(set);
        else if (type == AlterraWeather.MOTE.get()) MOTE.sprites(set);
    }

    // ---- making them
    /** The old entry point: one call for every kind of particle the weather makes. */
    public static void add(Level level, ParticleOptions o, double x, double y, double z, double dx, double dy, double dz) {
        var type = o.getType();
        if (type == AlterraWeather.SPRAY.get()) spawnSpray(level, x, y, z, dx, dy, dz);
        else if (type == AlterraWeather.RIPPLE.get()) Particles.spawn(RIPPLE, level, x, y, z, p -> { p.life = 12 + p.random.nextInt(8); p.data[0] = 0.12f + p.random.nextFloat() * 0.12f; p.size = p.data[0]; p.a = 0f; p.roll = p.random.nextFloat() * Mth.TWO_PI; });
        else if (type == AlterraWeather.CLUMP.get()) Particles.spawn(CLUMP, level, x, y, z, p -> { p.life = 24 + p.random.nextInt(18); p.data[0] = (float) dx; p.size = p.data[0] * 0.6f; p.a = 0f; p.roll = p.random.nextFloat() * Mth.TWO_PI; p.vx = (p.random.nextFloat() - 0.5f) * 0.01f; p.vz = (p.random.nextFloat() - 0.5f) * 0.01f; });
        else if (type == AlterraWeather.DROP.get()) Particles.spawn(DROPLET, level, x, y, z, p -> { p.vx = dx; p.vy = dy; p.vz = dz; p.life = 7 + p.random.nextInt(6); p.data[0] = 0.04f + p.random.nextFloat() * 0.035f; p.size = p.data[0]; p.r = 0.86f; p.g = 0.93f; p.b = 1f; p.a = 0.7f; });
        else if (type == AlterraWeather.HAIL.get()) Particles.spawn(HAIL, level, x, y, z, p -> { p.vx = dx; p.vy = dy; p.vz = dz; p.life = 80; p.size = 0.07f + p.random.nextFloat() * 0.04f; });
        else if (type == AlterraWeather.FOAM.get()) Particles.spawn(FOAM, level, x, y, z, p -> { p.vx = dx; p.vz = dz; p.life = 50 + p.random.nextInt(40); p.data[0] = 0.32f + p.random.nextFloat() * 0.3f; p.size = p.data[0]; p.data[1] = p.random.nextFloat() * Mth.TWO_PI; p.roll = p.random.nextFloat() * Mth.TWO_PI; p.a = 0f; });
        else if (type == AlterraWeather.PRINT.get() && o instanceof ColorParticleOption c) Particles.spawn(PRINT, level, x, y, z, p -> {   // dx: which foot, dy: facing (radians), dz: how long it lasts (ticks)
            p.sprite = PRINT.sprite(1 - (int) dx, 1);   // (the quad is seen mirrored from above, so the left foot takes the mirrored picture)
            p.r = c.getRed(); p.g = c.getGreen(); p.b = c.getBlue(); p.life = (int) dz; p.size = 0.19f; p.a = 0f; p.roll = (float) dy + Mth.PI; });
        else if (type == AlterraWeather.DUST.get() && o instanceof ColorParticleOption c) Particles.spawn(DUST, level, x, y, z, p -> { tint(p, c, 0.12f); p.life = 28 + p.random.nextInt(24); p.size = 0.045f + p.random.nextFloat() * 0.07f; p.vx = dx; p.vy = dy + (p.random.nextDouble() - 0.5) * 0.03; p.vz = dz; p.a = 0f; });
        else if (type == AlterraWeather.PUFF.get() && o instanceof ColorParticleOption c) puff(level, x, y, z, dx, dy, dz, c);
        else if (type == AlterraWeather.DRIFT.get() && o instanceof ColorParticleOption c) Particles.spawn(DRIFT, level, x, y, z, p -> { tint(p, c, 0.05f); p.life = 24 + p.random.nextInt(22); p.data[0] = 0.45f + p.random.nextFloat() * 0.7f; p.size = p.data[0]; p.data[1] = 0.22f + p.random.nextFloat() * 0.18f; p.roll = p.random.nextFloat() * 0.4f - 0.2f; p.vx = dx; p.vy = dy; p.vz = dz; p.a = 0f; });
        else if (type == AlterraWeather.MOTE.get() && o instanceof ColorParticleOption c) Particles.spawn(MOTE, level, x, y, z, p -> { tint(p, c, 0.15f); p.life = 90 + p.random.nextInt(80); p.size = 0.05f + p.random.nextFloat() * 0.06f; p.data[0] = 0.65f + p.random.nextFloat() * 0.3f; p.data[1] = p.random.nextFloat() * Mth.TWO_PI; p.data[2] = 0.004f + p.random.nextFloat() * 0.006f; p.vx = dx; p.vy = dy; p.vz = dz; p.a = 0f; });
        else if (type == ParticleTypes.SPLASH) add(level, droplet(), x, y, z, (Math.random() - 0.5) * 0.04, dy + 0.03, (Math.random() - 0.5) * 0.04);   // vanilla's splash drops: ours
        else if (type == ParticleTypes.BUBBLE_POP) Particles.spawn(DROPLET, level, x, y, z, p -> { p.vx = (p.random.nextDouble() - 0.5) * 0.01; p.vy = 0.012; p.vz = (p.random.nextDouble() - 0.5) * 0.01; p.life = 10 + p.random.nextInt(6); p.data[0] = 0.05f; p.size = 0.05f; p.r = 0.8f; p.g = 0.92f; p.b = 1f; p.a = 0.6f; });
        else if (type == ParticleTypes.CLOUD) Particles.spawn(PUFF, level, x, y, z, p -> { p.life = 20 + p.random.nextInt(10); p.data[0] = 0.5f; p.size = 0.5f; p.data[1] = 0.25f; p.vy = dy; p.vx = dx; p.vz = dz; p.r = p.g = p.b = 0.95f; p.a = 0f; });
        else level.addParticle(o, x, y, z, dx, dy, dz);   // another mod's type: its own engine
    }

    private static ParticleOptions droplet() { return AlterraWeather.DROP.get(); }
    private static void tint(Particle p, ColorParticleOption c, float spread) {
        float k = 1f - spread + p.random.nextFloat() * spread * 2f;
        p.r = Math.min(1f, c.getRed() * k); p.g = Math.min(1f, c.getGreen() * k); p.b = Math.min(1f, c.getBlue() * k);
    }
    private static void puff(Level level, double x, double y, double z, double dx, double dy, double dz, ColorParticleOption c) {
        Particles.spawn(PUFF, level, x, y, z, p -> { tint(p, c, 0.06f); p.life = 70 + p.random.nextInt(50); p.data[0] = 2.2f + p.random.nextFloat() * 3.4f; p.size = p.data[0]; p.data[1] = 0.10f + p.random.nextFloat() * 0.10f; p.roll = p.random.nextFloat() * 0.3f - 0.15f; p.vx = dx; p.vy = dy; p.vz = dz; p.a = 0f; });
    }
    public static void spawnSpray(Level level, double x, double y, double z, double dx, double dy, double dz) {
        Particles.spawn(SPRAY, level, x, y, z, p -> {
            p.vx = dx; p.vy = dy; p.vz = dz; p.life = 50; p.data[0] = 0.05f + p.random.nextFloat() * p.random.nextFloat() * 0.1f; p.size = p.data[0];
            float tint = p.random.nextFloat(); p.r = 0.36f + 0.1f * tint; p.g = 0.68f + 0.08f * tint; p.b = 0.92f; p.a = 0.8f;   // the sea's own blue, not white
        });
    }
    /** A raindrop hitting the ground (what vanilla's RAIN particle is for): a pixel of water that bounces up a little and falls back. */
    public static void spawnRainHit(Level level, double x, double y, double z) {
        Particles.spawn(RAIN_HIT, level, x, y + 0.02, z, p -> {
            p.vx = (p.random.nextDouble() - 0.5) * 0.06; p.vz = (p.random.nextDouble() - 0.5) * 0.06; p.vy = 0.06 + p.random.nextDouble() * 0.07;
            p.life = 6 + p.random.nextInt(5); p.size = 0.025f + p.random.nextFloat() * 0.02f; p.r = 0.62f; p.g = 0.80f; p.b = 0.95f; p.a = 0.7f;
        });
    }

    // ---- behaviours
    private static void rainHit(Particle p) {
        p.vy -= 0.036; p.vx *= 0.95; p.vy *= 0.95; p.vz *= 0.95;
        double nx = p.x + p.vx, ny = p.y + p.vy, nz = p.z + p.vz;
        if (p.solidAt(nx, ny, nz)) { p.vx = p.vy = p.vz = 0; } else { p.x = nx; p.y = ny; p.z = nz; }
        p.a = 0.7f * Math.min(1f, (p.life - p.age) / 4f);
    }

    private static void spray(Particle p) {
        double size = p.data[0];
        p.size = (float) (size * Math.min(1f, 0.4f + p.age / 3f) * Math.min(1f, (p.life - p.age) / 6f));
        double sp = Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz), k = 1.0 - Math.min(0.25, sp * 0.0045 / size);   // drag grows with speed and bites small drops harder
        p.vx *= k; p.vy = p.vy * k - 0.062; p.vz *= k;
        double[] hit = SubLevels.collide(p.x, p.y, p.z, p.vx, p.vy, p.vz);   // a ship is solid: the drop bounces off its hull or stops on its deck
        if (hit != null) { if (hit[6] > 0) { p.kill(); return; } p.x = hit[0]; p.y = hit[1]; p.z = hit[2]; p.vx = hit[3]; p.vy = hit[4]; p.vz = hit[5]; }
        double nx = p.x + p.vx, ny = p.y + p.vy, nz = p.z + p.vz;
        if (p.solidAt(nx, ny, nz)) { p.kill(); return; }   // it hits the ground or a wall and is gone into it
        p.x = nx; p.y = ny; p.z = nz;
        if (p.vy < 0 && p.level.getFluidState(BlockPos.containing(p.x, p.y - size * 0.5, p.z)).is(FluidTags.WATER)) {
            if (SubLevels.inside(p.x, p.z)) { p.kill(); return; }   // into a hull: the water there is hidden
            if (p.random.nextInt(3) == 0) Ripples.add(p.x, p.z, 0.1f + (float) size * 1.5f);
            if (size > 0.1f && p.vy < -0.25 && p.random.nextBoolean()) spawnSpray(p.level, p.x, Splashes.surface(p.level, p.x, p.y, p.z) + 0.05, p.z, (p.random.nextDouble() - 0.5) * 0.06, -p.vy * 0.3, (p.random.nextDouble() - 0.5) * 0.06);   // a big one bounces a smaller one back up
            p.kill();
        }
    }

    private static void clump(Particle p) {
        if (SubLevels.inside(p.x, p.z)) { p.kill(); return; }
        p.x += p.vx; p.z += p.vz;
        p.size = p.data[0] * (0.6f + 0.55f * Mth.sqrt(p.t()));
        p.a = p.fade(0.12f, 0.6f) * 0.95f;
    }

    private static void ripple(Particle p) { float t = p.t(); p.size = p.data[0] * (0.35f + 2.6f * t); p.a = p.fade(0.12f, 1f) * (1f - t) * 0.9f; }

    private static void droplet(Particle p) {
        p.x += p.vx; p.y += p.vy; p.z += p.vz; p.vy -= 0.022; p.vx *= 0.92; p.vz *= 0.92;
        p.a = 0.7f * Math.min(1f, (p.life - p.age) / 3f); p.size = p.data[0] * (1f - 0.35f * p.age / p.life);
    }

    private static void hail(Particle p) {   // falls straight and fast, bounces twice on whatever it lands on (a little less each time), then is gone
        for (int k = 0; k < 2; k++) {   // two half steps: a fast stone must not skip over a thin floor
            double nx = p.x + p.vx * 0.5, ny = p.y + p.vy * 0.5, nz = p.z + p.vz * 0.5;
            var bp = BlockPos.containing(nx, ny, nz); var st = p.level.getBlockState(bp);
            if (!st.getFluidState().isEmpty()) { p.kill(); return; }
            var shape = st.getCollisionShape(p.level, bp);
            if (p.vy < 0 && !shape.isEmpty() && ny < bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y)) {
                int bounces = (int) ++p.data[0];
                if (bounces > 2) { p.kill(); return; }
                p.y = bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.02; p.vy = 0.17 + p.random.nextDouble() * 0.12 - bounces * 0.04; p.vx *= 0.45; p.vz *= 0.45; continue;
            }
            p.x = nx; p.y = ny; p.z = nz;
        }
        if (p.data[0] > 0) p.vy -= 0.05;
    }

    private static void print(Particle p) {
        p.a = p.fade(0.02f, 0.45f) * 0.6f;
        if ((p.age & 3) == 0) {   // the print lies on a block: if that goes (it is broken or dug away) or something now stands in its place, the print goes with it
            var under = p.stateAt(p.x, p.y - 0.03, p.z); var on = p.stateAt(p.x, p.y + 0.03, p.z);
            if (under.isAir() || under.canBeReplaced() && !under.is(net.minecraft.world.level.block.Blocks.SNOW) || !on.isAir() && on.isSolidRender(p.level, BlockPos.containing(p.x, p.y + 0.03, p.z))) p.kill();
        }
    }

    private static void foam(Particle p) {   // runs up towards the land and slips back, and thins out
        float t = p.t(), wave = Mth.sin(t * Mth.TWO_PI * 1.3f + p.data[1]);
        p.x += p.vx * 0.012 * wave; p.z += p.vz * 0.012 * wave;
        p.size = p.data[0] * (0.8f + 0.25f * wave);
        p.a = p.fade(0.25f, 0.45f) * 0.8f;
    }

    private static void dust(Particle p) { p.move(); p.a = p.fade(0.15f, 0.25f) * 0.85f; }
    private static void puff(Particle p) { p.move(); p.a = p.fade(0.3f, 0.35f) * p.data[1]; p.size = p.data[0] * (0.9f + 0.2f * p.t()); }
    private static void drift(Particle p) { p.move(); p.a = p.fade(0.25f, 0.4f) * p.data[1]; p.size = p.data[0] * (0.85f + 0.4f * p.t()); }
    private static void mote(Particle p) {
        float t = p.age * 0.12f + p.data[1], sway = p.data[2];
        p.x += p.vx + Mth.sin(t) * sway; p.z += p.vz + Mth.cos(t * 0.8f) * sway; p.y += p.vy + Mth.sin(t * 1.3f) * sway * 0.5f;
        p.a = p.fade(0.2f, 0.3f) * p.data[0];
    }
}
