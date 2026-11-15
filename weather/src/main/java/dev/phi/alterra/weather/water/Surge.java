package dev.phi.alterra.weather.water;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

/** Where the storm surge puts the top of the sea: the physics side of the water grid, the same on the server and on the client. */
public final class Surge {
    private Surge() {}

    /** The surge at a point, in blocks above the normal sea (0 when there is none). */
    public static float at(Level level, double x, double z) {
        if (level.isClientSide) return SeaSurge.height(x, z);
        return level instanceof ServerLevel s ? SeaGrid.height(s, x, z) : 0f;
    }

    public static boolean active(Level level) { return level.isClientSide ? SeaSurge.active() : level instanceof ServerLevel s && SeaGrid.active(s); }

    /**
     * The raised top of the sea in the column at x, z, looking for its surface within two blocks of y; NaN when the surge does not raise it there.
     * Only open sea counts: water whose top is at sea level with nothing solid above it (not lakes up in the hills, not water under a roof).
     */
    public static double surface(Level level, double x, double y, double z) {
        if (!active(level)) return Double.NaN;
        float h = at(level, x, z);
        if (h < 0.01f) return Double.NaN;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y), sea = level.getSeaLevel();
        var p = new BlockPos.MutableBlockPos();
        for (int yy = Math.max(by - 2, sea - 2); yy <= Math.min(by + 2, sea); yy++) {
            p.set(bx, yy, bz);
            var fs = level.getFluidState(p);
            if (!fs.is(FluidTags.WATER)) continue;
            p.setY(yy + 1);
            if (!level.getFluidState(p).isEmpty() || !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
            p.setY(yy);
            return yy + fs.getHeight(level, p) + h;
        }
        return Double.NaN;
    }
}
