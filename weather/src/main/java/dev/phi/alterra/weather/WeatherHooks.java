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
    /** Is the water shader drawing the water (then ripples, rain rings and splashes are drawn by it)? */
    public static volatile BooleanSupplier shaderWater = () -> false;
    /** Does something draw its own clouds (then vanilla's are left out)? */
    public static volatile BooleanSupplier ownClouds = () -> false;
    /** Precise water amounts from the server's water simulation, for a smooth surface. */
    public static volatile Consumer<WaterSync> waterAmounts = w -> {};
    /** A block changed on the client (for easing the water surface between levels). */
    public static volatile java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState[]> blockChanged = (p, s) -> {};

    /** The one clock every animation runs on (waves, sway, boats riding the swell, the aurora): the wall clock, smooth every frame, the same speed at any tick rate and on every computer. */
    /** How far a storm's fog (sandstorm, blizzard, fog, ash) closes in the view: 0 clear, near 1 in the thick of it. Set by the weather each frame; the visuals fade their clouds by it. */
    public static volatile float stormFog = 0f;
    /** The first air above the ground at a column, leaves not counted. Works on the client too: the client keeps only the MOTION_BLOCKING and WORLD_SURFACE
     *  height maps (MOTION_BLOCKING_NO_LEAVES there gives the bottom of the world, which put every storm particle 24 blocks underground). */
    public static int ground(net.minecraft.world.level.Level l, int x, int z) {
        int h = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        var p = new net.minecraft.core.BlockPos.MutableBlockPos(x, h - 1, z);
        for (int i = 0; i < 48 && h > l.getMinBuildHeight() && l.getBlockState(p).is(net.minecraft.tags.BlockTags.LEAVES); i++) { h--; p.move(0, -1, 0); }
        return h;
    }
    /** The funnels (tornadoes, dust devils, whirlpools) near the camera, for the scene pass to draw: up to four, eight numbers each (x, y, z, radius, height, strength, type, top radius). */
    public static volatile float[] funnels = new float[32];
    public static volatile int funnelCount;
    /** Set on the client: takes what the server says about the disasters. */
    public static volatile Consumer<DisasterSync> disasters = d -> {};
    /** How much rain water has gathered at a block column (0 dry to 1 a full puddle), as the scene look worked it out: what the puddles on screen show. 0 without Illuminate. */
    public static volatile java.util.function.BiFunction<Integer, Integer, Float> puddleAt = (x, z) -> 0f;
    /** Water thrown onto land by a splash (x, z, radius in blocks, amount in blocks of water): the scene look wets the ground around it. */
    public static volatile java.util.function.Consumer<double[]> splashOnLand = a -> {};
    private static final long CLOCK_START = System.nanoTime();
    /** Seconds since the game started: smooth, never wraps (a wrap made every wave, caustic and light pattern jump at once). */
    public static float clock() { return (float) ((System.nanoTime() - CLOCK_START) / 1.0e9); }
}
