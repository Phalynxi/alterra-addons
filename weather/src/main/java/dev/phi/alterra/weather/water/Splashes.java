package dev.phi.alterra.weather.water;

import dev.phi.prismlib.api.Look;
import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/**
 * A splash has three stages, like Particular's: first a ring of foam clumps appears on the surface where it hit and big white drops are thrown up (a column in the middle, more flung out around it); then the drops fly and fall (WeatherClient.Spray);
 * and where each lands in the water it leaves a ripple and a small clump of foam that settles and thins out (the third stage). It scales with the impact: a swimmer's plop, a boat landing, a tree falling in.
 */
public final class Splashes {
    private Splashes() {}

    /** A splash at a place on the water surface: scale 0.15 (a plop) to 4.5 (a tree falling in). */
    public static void add(double x, double y, double z, double scale) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || SubLevels.inside(x, z)) return;
        double sc = Math.max(0.15, Math.min(4.5, scale)); var r = level.random;
        double s2 = Math.min(sc, 3.0); var sp = AlterraWeather.SPRAY.get();
        int crown = 4 + (int) (sc * 2.5);   // the crown: a ring of drops thrown out and up from the rim of the hole
        for (int i = 0; i < crown; i++) {
            double a = (i + r.nextDouble() * 0.8) / crown * Math.PI * 2, r0 = 0.1 + 0.08 * sc, out = (0.05 + 0.04 * s2) * (0.8 + 0.4 * r.nextDouble()), up = (0.12 + 0.05 * s2) * (0.8 + 0.4 * r.nextDouble());
            dev.phi.alterra.weather.WeatherParticles.add(level, sp, x + Math.cos(a) * r0, y + 0.05, z + Math.sin(a) * r0, Math.cos(a) * out, up, Math.sin(a) * out);
        }
        int jet = 2 + (int) (sc * 1.2);   // the jet that rises in the middle as the hole closes: fastest at the top, so the column stretches and breaks into drops
        for (int i = 0; i < jet; i++) {
            double f = (i + 0.5) / jet, up = Math.min(0.38, (0.10 + 0.05 * s2) * (0.55 + 0.9 * f) * (0.9 + 0.2 * r.nextDouble()));
            dev.phi.alterra.weather.WeatherParticles.add(level, sp, x + (r.nextDouble() - 0.5) * 0.04, y + 0.05 + i * 0.05, z + (r.nextDouble() - 0.5) * 0.04, (r.nextDouble() - 0.5) * 0.02, up, (r.nextDouble() - 0.5) * 0.02);
        }
        Look.splashOnLand.accept(new double[] {x, z, 1.2 + sc * 1.1, 0.04 * sc});   // and the water that is thrown out lands on the shore
        int loose = (int) Math.min(6, 1 + sc * 1.5);   // a few flung wide
        for (int i = 0; i < loose; i++) {
            double a = r.nextDouble() * Math.PI * 2, out = (0.03 + r.nextDouble() * 0.07) * (0.6 + 0.4 * s2), up = 0.10 + r.nextDouble() * 0.15;
            dev.phi.alterra.weather.WeatherParticles.add(level, sp, x, y + 0.1, z, Math.cos(a) * out, up, Math.sin(a) * out);
        }
    }

    /** A bow splash: a sheet of water thrown forward and out to the sides, low, with foam on the surface ahead of the bow. dirX, dirZ is the way it is going (unit). */
    public static void bow(double x, double y, double z, double scale, double dirX, double dirZ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || SubLevels.inside(x, z)) return;
        double sc = Math.max(0.15, Math.min(3.0, scale)); var r = level.random;
        Look.splashOnLand.accept(new double[] {x, z, 1.5 + sc, 0.025 * sc});
        int drops = (int) Math.min(26, 4 + sc * 7);
        for (int i = 0; i < drops; i++) {
            double side = (r.nextBoolean() ? 1 : -1) * (0.05 + r.nextDouble() * 0.18) * (0.6 + 0.4 * sc), fwd = (0.06 + r.nextDouble() * 0.16) * (0.6 + 0.4 * sc);
            double vx = dirX * fwd - dirZ * side, vz = dirZ * fwd + dirX * side, up = 0.10 + r.nextDouble() * 0.16 * (0.6 + 0.4 * sc);
            dev.phi.alterra.weather.WeatherParticles.add(level, AlterraWeather.SPRAY.get(), x - dirZ * side * 2, y + 0.1, z + dirX * side * 2, vx, up, vz);
        }
    }

    /** The y of the water's top above a point, looking up to three blocks above it. */
    public static double surface(ClientLevel level, double x, double y, double z) {
        var p = new BlockPos.MutableBlockPos((int) Math.floor(x), (int) Math.floor(y) + 2, (int) Math.floor(z));
        for (int i = 0; i < 6; i++) {
            var fs = level.getFluidState(p);
            if (fs.is(FluidTags.WATER)) return p.getY() + fs.getHeight(level, p);
            p.move(0, -1, 0);
        }
        return y;
    }
}
