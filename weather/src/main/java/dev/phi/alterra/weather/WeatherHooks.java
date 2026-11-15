package dev.phi.alterra.weather;

import dev.phi.alterra.weather.net.DisasterSync;
import dev.phi.alterra.weather.net.WaterSync;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Where Alterra Illuminate (the look: water shader, clouds, scene light) plugs into the weather. Without it these stay at their defaults and the weather works on its own
 * with vanilla-looking water.
 */
public final class WeatherHooks {
    private WeatherHooks() {}
    /** A block changed on the client (for easing the water surface between levels). */
    public static volatile java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState[]> blockChanged = (p, s) -> {};

    /** The one clock every animation runs on (waves, sway, boats riding the swell, the aurora): the wall clock, smooth every frame, the same speed at any tick rate and on every computer. */
    /** How far a storm's fog (sandstorm, blizzard, fog, ash) closes in the view: 0 clear, near 1 in the thick of it. Set by the weather each frame; the visuals fade their clouds by it. */
    public static volatile float stormFog = 0f;
    /** The first air above the ground at a column, leaves not counted. Works on the client too: the client keeps only the MOTION_BLOCKING and WORLD_SURFACE
     *  height maps (MOTION_BLOCKING_NO_LEAVES there gives the bottom of the world, which put every storm particle 24 blocks underground). */
    public static int ground(net.minecraft.world.level.Level l, int x, int z) { return dev.phi.aethryon.api.AethryonApi.ground(l, x, z); }
    /** Set on the client: takes what the server says about the disasters. */
    public static volatile Consumer<DisasterSync> disasters = d -> {};
    /** Seconds since the game started: smooth, never wraps (a wrap made every wave, caustic and light pattern jump at once). */
    public static float clock() { return dev.phi.aethryon.api.AethryonApi.clock().seconds(); }
}
