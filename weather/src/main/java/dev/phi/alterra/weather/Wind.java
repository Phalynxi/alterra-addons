package dev.phi.alterra.weather;

import dev.phi.alterra.weather.storm.Storms;

import dev.phi.alterra.weather.storm.Storms.Kind;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The wind: a smooth field over the world that depends only on the game time and the place, so the server and every client work out the same
 * wind without sending anything. Direction and speed drift slowly from place to place and over the day. Inside a storm the wind blows the
 * way that storm blows and gets stronger. Speed is 0..1.5 (1 is a hard storm wind).
 */
public final class Wind {
    private Wind() {}

    /** Wind speed 0..1.5 and the direction (radians, the way it blows: 0 is east, a quarter turn is south) as a vector of that length. */
    public static Vec3 at(long time, double x, double z) { return at(time, x, z, 0f, 0f); }

    /** The same, with vanilla rain and thunder adding to it (a thunderstorm is windy). */
    public static Vec3 at(long time, double x, double z, float rain, float thunder) { return at(time, x, z, rain, thunder, 0f); }

    /** With a heavy thunderstorm on top: a gale. */
    public static Vec3 at(long time, double x, double z, float rain, float thunder, float heavy) {
        double t = time / 20.0;   // seconds
        // two slow waves for the direction, two for the speed, all different in size and rhythm so no pattern shows
        double angle = Math.sin(x / 1400 + t / 900) * 1.7 + Math.sin(z / 1100 - t / 1300 + 1.3) * 1.3 + Math.sin((x + z) / 2600 + t / 2100) * 0.9 + 0.6;
        double calm = 0.5 + 0.5 * Math.sin(x / 900 + t / 420 + 0.4) * Math.sin(z / 1200 - t / 610 + 2.1);
        double speed = 0.12 + 0.38 * calm * calm + 0.05 * Math.sin(t / 7 + x / 90) ;   // gusts on top
        int rx = Storms.region(x), rz = Storms.region(z);
        double strongest = 0, stormAngle = angle;
        for (Kind k : Kind.values()) {
            if (k == Kind.AURORA || k == Kind.POLLEN) continue;
            double lv = Storms.level(k, time, rx, rz);
            if (lv > strongest) { strongest = lv; stormAngle = Storms.wind(k, time, rx, rz); }
        }
        if (strongest > 0.01) {   // in a storm: turn to its direction and blow harder
            double d = Mth.wrapDegrees((float) Math.toDegrees(stormAngle - angle)) * Math.PI / 180.0;
            angle += d * strongest;
            speed += strongest * 0.75;
        }
        speed = Mth.clamp(speed + rain * 0.2 + thunder * 0.4 + heavy * 0.5, 0.0, 1.5);
        return new Vec3(Math.cos(angle) * speed, 0, Math.sin(angle) * speed);
    }

    /** "north east", "south" ... the way the wind blows towards. */
    public static String compass(Vec3 wind) {
        double deg = (Math.toDegrees(Math.atan2(wind.z, wind.x)) + 360 + 90) % 360;   // 0 = north (negative z), 90 = east
        String[] names = {"south", "south west", "west", "north west", "north", "north east", "east", "south east"};
        return names[(int) Math.floor(((deg + 180) % 360 + 22.5) / 45) % 8];
    }
}
