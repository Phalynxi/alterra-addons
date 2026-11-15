package dev.phi.alterra.weather.water;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import org.joml.Vector3dc;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create Aeronautics' ships and other physics objects (Sable's sub-levels) in the water: they make wakes while they move, splash when they come down into it, and throw spray from the bow, like boats do.
 * Sable is not a dependency: its container, sub-levels, poses and bounding boxes are read by reflection (the API of its companion library), and if anything about them looks different than expected this does nothing.
 */
public final class SubLevels {
    private SubLevels() {}
    private static boolean failed, ready;
    private static Method getContainer, getAll, renderPose, boundingBox, position, uuid, minX, minY, minZ, maxX, maxY, maxZ, getPlot, plotBox, tPos, tNormal, iMinX, iMinY, iMinZ, iMaxX, iMaxY, iMaxZ;
    private static final Map<UUID, double[]> state = new HashMap<>();
    /** The bounding boxes of ships and objects near the camera (minX, minY, minZ, maxX, maxY, maxZ), rebuilt every tick: the water shader keeps the water from rising into them. */
    private static final java.util.List<double[]> boxes = new java.util.ArrayList<>(), next = new java.util.ArrayList<>();
    public static final int MAX_BOXES = 8;

    /** The boxes as (centre x, centre z, half width, half depth) and (min y, max y, 0, 0), relative to the camera; returns how many (the nearest eight). */
    public static synchronized int snapshot(float[] out, net.minecraft.world.phys.Vec3 cam) {
        var list = new java.util.ArrayList<>(boxes);
        list.sort(java.util.Comparator.comparingDouble(b -> Math.hypot((b[0] + b[3]) / 2 - cam.x, (b[2] + b[5]) / 2 - cam.z)));
        int n = Math.min(MAX_BOXES, list.size());
        for (int i = 0; i < n; i++) {
            double[] b = list.get(i);
            out[i * 8] = (float) ((b[0] + b[3]) / 2 - cam.x); out[i * 8 + 1] = (float) ((b[2] + b[5]) / 2 - cam.z); out[i * 8 + 2] = (float) ((b[3] - b[0]) / 2); out[i * 8 + 3] = (float) ((b[5] - b[2]) / 2);
            out[i * 8 + 4] = (float) (b[1] - cam.y); out[i * 8 + 5] = (float) (b[4] - cam.y); out[i * 8 + 6] = 0f; out[i * 8 + 7] = 0f;
        }
        return n;
    }
    private static final java.util.List<double[]> hulls = new java.util.ArrayList<>(), nextHulls = new java.util.ArrayList<>();
    public static final int MAX_HULLS = 4;

    /** The oriented boxes of the hulls: for each, (centre, 0), (axis x, half x), (axis y, half y), (axis z, half z), all relative to the camera; returns how many (the nearest four). The water shader cuts the water out of them. */
    public static synchronized int snapshotHulls(float[] out, net.minecraft.world.phys.Vec3 cam) {
        var list = new java.util.ArrayList<>(hulls);
        list.sort(java.util.Comparator.comparingDouble(h -> Math.hypot(h[0] - cam.x, h[2] - cam.z)));
        int n = Math.min(MAX_HULLS, list.size());
        for (int i = 0; i < n; i++) {
            double[] h = list.get(i); int o = i * 16;
            out[o] = (float) (h[0] - cam.x); out[o + 1] = (float) (h[1] - cam.y); out[o + 2] = (float) (h[2] - cam.z); out[o + 3] = 0f;
            for (int a = 0; a < 3; a++) { out[o + 4 + a * 4] = (float) h[3 + a * 3]; out[o + 5 + a * 4] = (float) h[4 + a * 3]; out[o + 6 + a * 4] = (float) h[5 + a * 3]; out[o + 7 + a * 4] = (float) h[12 + a]; }
        }
        return n;
    }
    /**
     * A drop at (x, y, z) moving with velocity (vx, vy, vz) that has ended up inside a hull's box: it is put back out through the nearest face and its velocity is reflected with some loss (it runs down the side or bounces off the underside);
     * returns {x, y, z, vx, vy, vz, landed} (landed 1 if it came down on top of the hull, where it should just stop), or null if it is not inside any hull.
     */
    public static synchronized double[] collide(double x, double y, double z, double vx, double vy, double vz) {
        for (double[] h : hulls) {
            double dx = x - h[0], dy = y - h[1], dz = z - h[2];
            double[] l = {dx * h[3] + dy * h[4] + dz * h[5], dx * h[6] + dy * h[7] + dz * h[8], dx * h[9] + dy * h[10] + dz * h[11]};
            double[] half = {h[12], h[13], h[14]};
            if (Math.abs(l[0]) >= half[0] || Math.abs(l[1]) >= half[1] || Math.abs(l[2]) >= half[2]) continue;
            int axis = 0; double best = Double.MAX_VALUE;
            for (int i = 0; i < 3; i++) { double pen = half[i] - Math.abs(l[i]); if (pen < best) { best = pen; axis = i; } }
            double sign = l[axis] >= 0 ? 1 : -1;
            double nx = h[3 + axis * 3] * sign, ny = h[4 + axis * 3] * sign, nz = h[5 + axis * 3] * sign;   // the face's normal, pointing out of the hull
            double push = best + 0.02, vn = vx * nx + vy * ny + vz * nz;
            if (vn < 0) { double k = 1.25 * vn; vx -= k * nx; vy -= k * ny; vz -= k * nz; }   // reflect with a quarter of the speed kept
            return new double[] {x + nx * push, y + ny * push, z + nz * push, vx * 0.8, vy, vz * 0.8, ny > 0.7 ? 1 : 0};
        }
        return null;
    }

    /** Is this spot (looking only at x and z) inside the footprint of a ship's hull: the world still has water there, but it is hidden, so splashes, foam and rings must not appear. */
    public static synchronized boolean inside(double x, double z) {
        for (double[] h : hulls) {
            double dx = x - h[0], dz = z - h[2];
            if (Math.abs(dx * h[3] + dz * h[5]) < h[12] && Math.abs(dx * h[6] + dz * h[8]) < h[13] && Math.abs(dx * h[9] + dz * h[11]) < h[14]) return true;
        }
        return false;
    }
    private static synchronized void publish() { boxes.clear(); boxes.addAll(next); next.clear(); hulls.clear(); hulls.addAll(nextHulls); nextHulls.clear(); }   // x, y, z, was in water (1/0), ticks in the air

    private static void init() throws Exception {
        Class<?> container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer"), sub = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel");
        getContainer = container.getMethod("getContainer", net.minecraft.world.level.Level.class);
        getAll = container.getMethod("getAllSubLevels");
        renderPose = sub.getMethod("renderPose", float.class);
        boundingBox = sub.getMethod("boundingBox");
        uuid = sub.getMethod("getUniqueId");
        Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc"), box = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
        position = pose.getMethod("position");
        minX = box.getMethod("minX"); minY = box.getMethod("minY"); minZ = box.getMethod("minZ"); maxX = box.getMethod("maxX"); maxY = box.getMethod("maxY"); maxZ = box.getMethod("maxZ");
        getPlot = sub.getMethod("getPlot"); plotBox = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot").getMethod("getBoundingBox");
        tPos = pose.getMethod("transformPosition", net.minecraft.world.phys.Vec3.class); tNormal = pose.getMethod("transformNormal", net.minecraft.world.phys.Vec3.class);
        Class<?> ibox = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
        iMinX = ibox.getMethod("minX"); iMinY = ibox.getMethod("minY"); iMinZ = ibox.getMethod("minZ"); iMaxX = ibox.getMethod("maxX"); iMaxY = ibox.getMethod("maxY"); iMaxZ = ibox.getMethod("maxZ");
        ready = true;
    }

    /** The y of the water's top near a point, or NaN if there is no water within a few blocks above or below it. */
    private static double waterTop(ClientLevel level, double x, double y, double z) {
        var p = new BlockPos.MutableBlockPos((int) Math.floor(x), (int) Math.floor(y) + 3, (int) Math.floor(z));
        for (int i = 0; i < 12; i++) {
            var fs = level.getFluidState(p);
            if (fs.is(FluidTags.WATER)) return p.getY() + fs.getHeight(level, p);
            p.move(0, -1, 0);
        }
        return Double.NaN;
    }

    public static void tick(ClientLevel level) {
        if (failed) return;
        try {
            if (!ready) init();
            Object container = getContainer.invoke(null, level);
            if (container == null) return;
            next.clear(); nextHulls.clear();
            for (Object sub : (List<?>) getAll.invoke(container)) {
                UUID id = (UUID) uuid.invoke(sub);
                Vector3dc pos = (Vector3dc) position.invoke(renderPose.invoke(sub, 1f));
                Object bb = boundingBox.invoke(sub);
                double x0 = (double) minX.invoke(bb), y0 = (double) minY.invoke(bb), z0 = (double) minZ.invoke(bb), x1 = (double) maxX.invoke(bb), y1 = (double) maxY.invoke(bb), z1 = (double) maxZ.invoke(bb);
                double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, w = x1 - x0, d = z1 - z0;
                next.add(new double[] {x0, y0, z0, x1, y1, z1});
                try {   // the hull's own oriented box: the plot's block bounds carried to the world by the pose
                    Object pose = renderPose.invoke(sub, 1f), plot = getPlot.invoke(sub), pb = plotBox.invoke(plot);
                    double l0 = (int) iMinX.invoke(pb), l1 = (int) iMaxX.invoke(pb) + 1, m0 = (int) iMinY.invoke(pb), m1 = (int) iMaxY.invoke(pb) + 1, n0 = (int) iMinZ.invoke(pb), n1 = (int) iMaxZ.invoke(pb) + 1;
                    var c = (net.minecraft.world.phys.Vec3) tPos.invoke(pose, new net.minecraft.world.phys.Vec3((l0 + l1) / 2, (m0 + m1) / 2, (n0 + n1) / 2));
                    net.minecraft.world.phys.Vec3 ax = ((net.minecraft.world.phys.Vec3) tNormal.invoke(pose, new net.minecraft.world.phys.Vec3(1, 0, 0))).normalize();
                    net.minecraft.world.phys.Vec3 ay = ((net.minecraft.world.phys.Vec3) tNormal.invoke(pose, new net.minecraft.world.phys.Vec3(0, 1, 0))).normalize();
                    net.minecraft.world.phys.Vec3 az = ((net.minecraft.world.phys.Vec3) tNormal.invoke(pose, new net.minecraft.world.phys.Vec3(0, 0, 1))).normalize();
                    nextHulls.add(new double[] {c.x, c.y, c.z, ax.x, ax.y, ax.z, ay.x, ay.y, ay.z, az.x, az.y, az.z, (l1 - l0) / 2, (m1 - m0) / 2, (n1 - n0) / 2});
                } catch (Throwable ignored) { }
                double[] st = state.computeIfAbsent(id, k -> new double[] {pos.x(), pos.y(), pos.z(), 0, 0});
                double dx = pos.x() - st[0], dz = pos.z() - st[2], speed = Math.hypot(dx, dz), vy = pos.y() - st[1];
                // is the object floating in water: some point under it has water whose surface is between its bottom and its top
                double top = Double.NaN;
                double[][] probes = {{cx, cz}, {cx - w * 0.3, cz - d * 0.3}, {cx + w * 0.3, cz - d * 0.3}, {cx - w * 0.3, cz + d * 0.3}, {cx + w * 0.3, cz + d * 0.3}};
                for (double[] pr : probes) { top = waterTop(level, pr[0], y0, pr[1]); if (!Double.isNaN(top)) break; }
                boolean wet = !Double.isNaN(top) && y0 < top - 0.05 && y1 > top + 0.05;
                float size = (float) Math.max(0.5, Math.min(3.0, (w + d) / 6.0));
                if (!wet && (Double.isNaN(top) || y0 >= top - 0.05)) st[4]++; else if (!wet) st[4] = 0;   // counts only while it is above the water
                long gt = level.getGameTime();
                if (wet) {
                    double sx = dx / Math.max(speed, 1e-4), sz = dz / Math.max(speed, 1e-4);   // the way it is going: bow ahead, stern behind
                    double[][] rim = {{cx + w / 2 + 0.5, cz}, {cx - w / 2 - 0.5, cz}, {cx, cz + d / 2 + 0.5}, {cx, cz - d / 2 - 0.5}};   // just outside the hull
                    if (st[3] == 0) {   // came down into the water
                        float fall = (float) Math.max(0, -vy);
                        if (st[4] >= 4 || fall > 0.2f) { Ripples.addAt(cx, cz, Math.min(3f, (0.5f + fall * 1.5f) * size)); for (double[] pr : rim) Splashes.add(pr[0], top, pr[1], (0.45 + fall * 2.4) * (0.6 + 0.3 * size)); }   // one ring for the object, as big as it is
                        else Ripples.addAt(cx, cz, 0.3f * size);
                        st[4] = 0;
                    } else if (speed > 0.04 && gt % 2 == 0) {   // wake: rings from the bow, stern and sides, outside the hull
                        float s = Math.min(1f, (float) speed * 2.4f) * Math.min(1.6f, size);
                        double half = Math.max(w, d) / 2.0 + 0.6;
                        Ripples.addDir(cx + sx * half * 0.4, cz + sz * half * 0.4, s, sx, sz, true);   // one ring per object (a ship made of several parts makes one for each part), carrying its heading so it draws as a wake
                        if (speed > 0.1 && gt % 2 == 0) Splashes.bow(cx + sx * half, top, cz + sz * half, 0.35 + Math.min(1.1, speed * 1.5) * Math.min(1.6, size), sx, sz);   // the bow splash, thrown forward and out
                    }
                }
                st[0] = pos.x(); st[1] = pos.y(); st[2] = pos.z(); st[3] = wet ? 1 : 0;
            }
            publish();
            if (level.getGameTime() % 1200 == 0) state.clear();
        } catch (Throwable t) { failed = true; }
    }
}
