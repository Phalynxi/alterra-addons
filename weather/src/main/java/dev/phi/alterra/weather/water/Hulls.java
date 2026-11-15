package dev.phi.alterra.weather.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Inside a ship's hull is dry: Create Aeronautics' ships (Sable's sub-levels) float in the sea, and the sea's water is still there in the world where the hull is. A point counts as inside the hull when, in the ship's own blocks,
 * there is hull under it in the same column (a deck, a floor, the keel); so a hole in the bottom lets the water in at that spot, and swimming beside or under a ship is still swimming.
 * Entities there are not in water (no swimming, no slowdown, no drowning) and the camera does not see underwater fog. With Create: Deep Seas installed this does nothing: it has its own compartments and flooding.
 * Sable is read by reflection; if anything looks different than expected this does nothing.
 */
public final class Hulls {
    private Hulls() {}
    private static final boolean DEEP_SEAS = net.neoforged.fml.ModList.get().isLoaded("create_submarine");
    private static boolean failed, ready;
    public static boolean deepSeas() { return DEEP_SEAS; }
    private static Method getContainer, getAll, logicalPose, getPlot, plotBox, accessor, toLocal, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ;
    private record Ship(Object pose, BlockGetter blocks, int x0, int y0, int z0, int x1, int y1, int z1) {}
    private record Snap(long tick, List<Ship> ships) {}
    private static final Map<Level, Snap> SNAPS = new WeakHashMap<>();

    private static void init() throws Exception {
        Class<?> container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer"), sub = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel"), plot = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
        getContainer = container.getMethod("getContainer", Level.class); getAll = container.getMethod("getAllSubLevels");
        logicalPose = sub.getMethod("logicalPose"); getPlot = sub.getMethod("getPlot");
        plotBox = plot.getMethod("getBoundingBox"); accessor = plot.getMethod("getEmbeddedLevelAccessor");
        toLocal = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("transformPositionInverse", Vec3.class);
        Class<?> ibox = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
        bMinX = ibox.getMethod("minX"); bMinY = ibox.getMethod("minY"); bMinZ = ibox.getMethod("minZ"); bMaxX = ibox.getMethod("maxX"); bMaxY = ibox.getMethod("maxY"); bMaxZ = ibox.getMethod("maxZ");
        ready = true;
    }

    private static synchronized List<Ship> ships(Level level) throws Exception {
        long t = level.getGameTime();
        Snap s = SNAPS.get(level);
        if (s != null && s.tick == t) return s.ships;
        if (!ready) init();
        List<Ship> out = new ArrayList<>();
        Object c = getContainer.invoke(null, level);
        if (c != null) for (Object sub : (List<?>) getAll.invoke(c)) {
            try {
                Object plot = getPlot.invoke(sub), b = plotBox.invoke(plot);
                out.add(new Ship(logicalPose.invoke(sub), (BlockGetter) accessor.invoke(plot), (int) bMinX.invoke(b), (int) bMinY.invoke(b), (int) bMinZ.invoke(b), (int) bMaxX.invoke(b), (int) bMaxY.invoke(b), (int) bMaxZ.invoke(b)));
            } catch (Throwable ignored) { }
        }
        SNAPS.put(level, new Snap(t, out));
        return out;
    }

    /** Whether a world point is inside a ship's hull, where the sea does not reach. */
    public static boolean dry(Level level, double x, double y, double z) {
        if (DEEP_SEAS || failed) return false;
        try {
            List<Ship> list = ships(level);
            if (list.isEmpty()) return false;
            Vec3 w = new Vec3(x, y, z);
            var p = new BlockPos.MutableBlockPos();
            for (Ship s : list) {
                Vec3 l = (Vec3) toLocal.invoke(s.pose, w);
                int bx = (int) Math.floor(l.x), by = (int) Math.floor(l.y), bz = (int) Math.floor(l.z);
                if (bx < s.x0 || bx > s.x1 || bz < s.z0 || bz > s.z1 || by < s.y0 - 1 || by > s.y1 + 1) continue;
                for (int yy = Math.min(by, s.y1); yy >= s.y0; yy--) {   // hull under it in this column
                    p.set(bx, yy, bz);
                    if (!s.blocks.getBlockState(p).getCollisionShape(s.blocks, p).isEmpty()) return true;
                }
            }
            return false;
        } catch (Throwable t) { failed = true; return false; }
    }
}
