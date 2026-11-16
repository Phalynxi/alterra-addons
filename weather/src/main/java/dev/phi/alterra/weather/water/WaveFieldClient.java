package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.storm.HeavyStorm;
import dev.phi.prismlib.api.PrismApi;
import dev.phi.prismlib.grid.ShallowWater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The water around the camera as a grid of its own ({@link ShallowWater}): 48 by 48 blocks at two cells to the block, following the camera. Things stir it (raindrops, swimmers, boats, splashes)
 * and the waves it carries run out, die away and bounce off the shore (land is a solid cell, so a ripple that reaches it turns back). The scene shader adds the slope of this grid to the water's
 * normal, so the ripples are lit and mirrored like the rest of the water.
 */
@EventBusSubscriber(modid = dev.phi.alterra.weather.AlterraWeather.ID, value = Dist.CLIENT)
public final class WaveFieldClient implements dev.phi.prismlib.api.WaveField {
    public static final WaveFieldClient INSTANCE = new WaveFieldClient();
    private WaveFieldClient() {}
    private static final int N = 96;
    private static ShallowWater grid; private static long activeUntil; private static int tick;

    @Override public ShallowWater grid() { return grid; }
    private static boolean live() { return grid != null && System.nanoTime() < activeUntil; }
    @Override public boolean active() { return live(); }
    private static void wake() { activeUntil = System.nanoTime() + 7_000_000_000L; }

    /** Something falls into the water at a point: a splash of the given scale (see {@link Splashes}). */
    public static void splash(double x, double z, double scale) {
        var g = grid; if (g == null || !g.inside(x, z)) return;
        g.impulse(x, z, (float) (0.45 + 0.3 * Math.min(3.0, scale)), (float) (-0.10 * Math.min(3.0, scale))); wake();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance(); ClientLevel level = mc.level;
        if (level == null || mc.player == null) { grid = null; return; }
        if (mc.isPaused() || !level.dimensionType().hasSkyLight() && false) return;
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        double sx = Math.round(cam.x / 8.0) * 8.0, sz = Math.round(cam.z / 8.0) * 8.0;   // the window moves in steps of eight blocks
        if (grid == null) { grid = new ShallowWater(N, 0.5f, sx, sz); grid.depth = 1.6f; grid.gravity = 9.8f; grid.drag = 0.9f; maskAll(level); }
        else if (Math.abs(grid.ox + N * grid.cell / 2.0 - sx) > 1e-6 || Math.abs(grid.oz + N * grid.cell / 2.0 - sz) > 1e-6) { grid.recentre(sx, sz); maskAll(level); }
        if (++tick % 40 == 0) maskAll(level);   // the shore changes (water flows, blocks are placed)
        var rnd = level.random;
        float rain = HeavyStorm.rainAt(level, cam.x, cam.z);
        if (rain > 0.1f) for (int i = 0, n = (int) (rain * 7); i < n; i++) {   // raindrops on the water
            double x = grid.ox + rnd.nextDouble() * N * grid.cell, z = grid.oz + rnd.nextDouble() * N * grid.cell;
            if (!water(grid, x, z)) continue;
            grid.impulse(x, z, 0.32f, -0.012f - 0.02f * rnd.nextFloat() * rain); wake();
        }
        for (Entity en : level.entitiesForRendering()) {   // swimmers, boats and anything dropped, while they move
            double dx = en.getX() - en.xOld, dz = en.getZ() - en.zOld, speed = Math.sqrt(dx * dx + dz * dz);
            boolean boat = en instanceof Boat, afloat = boat || en.isInWater() || en instanceof ItemEntity && en.isInWater();
            if (!afloat || speed < 0.04 || !grid.inside(en.getX(), en.getZ()) || !water(grid, en.getX(), en.getZ())) continue;
            float w = Math.max(0.4f, en.getBbWidth()) * (boat ? 0.55f : 0.7f), amt = (float) -Math.min(0.08, 0.22 * speed) * (boat ? 1.6f : 0.9f);
            grid.impulse(en.getX(), en.getZ(), w, amt);
            if (boat) { double k = -0.9 / Math.max(speed, 0.01); grid.impulse(en.getX() + dx * k, en.getZ() + dz * k, w * 0.8f, amt * 0.5f); }   // and the stern churns behind it
            wake();
        }
        if (live()) grid.step(0.05f);
        else if (grid.eta[N * N / 2 + N / 2] != 0f || tick % 100 == 0) { java.util.Arrays.fill(grid.eta, 0f); java.util.Arrays.fill(grid.u, 0f); java.util.Arrays.fill(grid.w, 0f); }
    }

    /** Is there water at the surface of this spot (anything else is a shore, a bank, a block: a solid cell for the waves)? */
    private static boolean water(ShallowWater g, double x, double z) { int i = (int) Math.floor((x - g.ox) / g.cell), j = (int) Math.floor((z - g.oz) / g.cell); return i >= 0 && j >= 0 && i < g.n && j < g.n && !g.solid[i + j * g.n]; }

    private static void maskAll(ClientLevel level) {
        var g = grid; var p = new BlockPos.MutableBlockPos();
        for (int j = 0; j < g.n; j += 2) for (int i = 0; i < g.n; i += 2) {   // one test per block, shared by its four cells
            int wx = (int) Math.floor(g.worldX(i)), wz = (int) Math.floor(g.worldZ(j)); boolean ok = false;
            if (level.hasChunk(wx >> 4, wz >> 4)) { int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1; ok = level.getFluidState(p.set(wx, top, wz)).is(FluidTags.WATER); }
            // the cells of the block starting at (i, j) when the grid origin is on a block corner (it is: the window moves in whole blocks)
            for (int dj = 0; dj < 2 && j + dj < g.n; dj++) for (int di = 0; di < 2 && i + di < g.n; di++) { g.solid[i + di + (j + dj) * g.n] = !ok; if (!ok) { g.eta[i + di + (j + dj) * g.n] = 0f; g.u[i + di + (j + dj) * g.n] = 0f; g.w[i + di + (j + dj) * g.n] = 0f; } }
        }
    }

    @SubscribeEvent public static void left(ClientPlayerNetworkEvent.LoggingOut e) { grid = null; }
}
