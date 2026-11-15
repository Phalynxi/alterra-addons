package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disturbances on the water: rings from boats, swimmers and mobs that move through it (a trail of them is a wake), a bigger ring when something enters the water, and where a waterfall lands. They are kept as a short list
 * of events that the water shader draws as rings that spread, widen and fade. The game's own splash particles are added to.
 */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class Ripples {
    private Ripples() {}
    private static final Map<Integer, double[]> last = new HashMap<>();   // entity id: last x, z, was in water (1 or 0)
    private static final List<BlockPos> falls = new ArrayList<>();
    private static long fallsAt = -100;

    public static final int MAX = 48;
    private static final double[] ex = new double[MAX], ez = new double[MAX], et = new double[MAX]; private static final float[] es = new float[MAX];
    private static int count, head;

    public static double now() { return System.nanoTime() / 1.0e9; }
    /** A ring (strength 0 to 1) at a world position: the water shader makes it spread, widen and fade. */
    public static void add(double x, double z, float strength) {
        if (SubLevels.inside(x, z)) return;   // not inside a hull
        ex[head] = x; ez[head] = z; et[head] = now(); es[head] = strength; head = (head + 1) % MAX; count = Math.min(MAX, count + 1);
    }

    /** A ring from something that is moving: it carries the heading (as a code in the strength), so the shader draws the arms of a wake and the churned trail, not a full circle. */
    public static void addDir(double x, double z, float strength, double dx, double dz, boolean hull) {
        double l = Math.hypot(dx, dz);
        if (l < 1e-4) { if (hull) addAt(x, z, strength); else add(x, z, strength); return; }
        int idx = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * 256) & 255;
        float s = Math.min(3.9f, strength) + 4f * (idx + 1);
        if (hull) addAt(x, z, s); else add(x, z, s);
    }
    /** The same, also where a hull is (the ring is only seen outside it, since the water inside a hull is cut away): one ring for a whole ship or object. */
    public static void addAt(double x, double z, float strength) {
        ex[head] = x; ez[head] = z; et[head] = now(); es[head] = strength; head = (head + 1) % MAX; count = Math.min(MAX, count + 1);
    }

    /** The rings, as x and z relative to the camera, age in seconds and strength; returns how many. */
    public static int snapshot(float[] out, Vec3 cam) {
        double t = now(); int n = 0;
        for (int i = 0; i < count; i++) {
            double age = t - et[i];
            if (age > 4.0 || es[i] <= 0f) continue;
            out[n * 4] = (float) (ex[i] - cam.x); out[n * 4 + 1] = (float) (ez[i] - cam.z); out[n * 4 + 2] = (float) age; out[n * 4 + 3] = es[i]; n++;
        }
        return n;
    }

    /** Falling trees, falling blocks and the like: heavy things that make a splash when they hit the water. */
    private static boolean heavy(Entity x) { return x instanceof net.minecraft.world.entity.item.FallingBlockEntity || x.getClass().getName().contains("FallingTree") || x instanceof net.minecraft.world.entity.item.PrimedTnt; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance(); ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !WeatherHooks.shaderWater.getAsBoolean() || !WeatherHooks.shaderWater.getAsBoolean()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity en : level.getEntitiesOfClass(Entity.class, AABB.ofSize(cam, 64, 48, 64), x -> x instanceof LivingEntity || x instanceof Boat || heavy(x))) {
            double[] st = last.computeIfAbsent(en.getId(), k -> new double[] {en.getX(), en.getZ(), 0, en.getY(), 0});   // x, z, was in water, y, ticks in the air
            boolean isHeavy = heavy(en);
            boolean wet = isHeavy ? en.isInWater() : en.isInWater() && !en.isUnderWater();   // swimming or floating at the surface, not diving
            double moved = Math.hypot(en.getX() - st[0], en.getZ() - st[1]), vy = en.getY() - st[3];
            float mass = Math.max(en.getBbWidth() * en.getBbWidth() * en.getBbHeight(), 0.05f);
            float big = en instanceof Boat ? 1.3f : isHeavy ? Math.max(0.6f, Math.min(3f, (float) Math.sqrt(mass))) : Math.max(0.5f, Math.min(1.2f, en.getBbWidth()));
            if (!wet && !en.onGround()) st[4]++; else if (!wet) st[4] = 0;
            if (wet && st[2] == 0) {   // entered the water: by falling in, or by coming down from a jump (a boat over a wave)
                float fall = (float) Math.max(Math.abs(en.getDeltaMovement().y), -vy);
                if (st[4] >= 7 || isHeavy || fall > 0.4f) { add(en.getX(), en.getZ(), Math.min(3f, (0.5f + fall * 1.5f) * big)); Splashes.add(en.getX(), Splashes.surface(level, en.getX(), en.getY(), en.getZ()), en.getZ(), Math.min(0.45 + fall * 1.6 + big * 0.4, isHeavy ? 4.5 : 1.6)); }
                else add(en.getX(), en.getZ(), 0.4f * big);
                st[4] = 0;
            }
            else if (!wet && st[2] == 1) add(en.getX(), en.getZ(), 0.35f * big);                                                                                       // left it
            else if (wet && moved > 0.22) {   // a trail of single rings as it moves: with enough speed they line up into the two arms of a wake behind it
                float s = Math.min(1f, (float) moved * 2.4f) * big; double sp = Math.hypot(en.getDeltaMovement().x, en.getDeltaMovement().z);
                if (sp > 0.1) { addDir(en.getX(), en.getZ(), s, en.getX() - st[0], en.getZ() - st[1], false); } else add(en.getX(), en.getZ(), s);
                st[0] = en.getX(); st[1] = en.getZ(); }              // a trail of rings as it moves: the wake
            else if (!wet) { st[0] = en.getX(); st[1] = en.getZ(); }
            if (wet) spray(level, en, moved);
            st[2] = wet ? 1 : 0; st[3] = en.getY();
        }
        if (level.getGameTime() % 600 == 0) last.keySet().removeIf(id -> level.getEntity(id) == null);
        waterfalls(level, cam);
        Propellers.tick(level, cam);
        SubLevels.tick(level);   // ships and other physics objects
    }

    /** A small splash now and then from something moving through the water: a boat throws a sheet up at the bow once it is fast, swimmers and mobs a little plop. */
    private static void spray(ClientLevel level, Entity en, double moved) {
        long gt = level.getGameTime();
        if (en instanceof Boat) {
            if (moved < 0.2 || gt % 4 != 0) return;
            double yaw = Math.toRadians(en.getYRot()), fx = -Math.sin(yaw), fz = Math.cos(yaw);   // the way the boat points
            double x = en.getX() + fx * 1.0, z = en.getZ() + fz * 1.0;
            Splashes.bow(x, Splashes.surface(level, x, en.getY(), z), z, 0.2 + Math.min(0.6, (moved - 0.2) * 1.6), fx, fz);
        } else if (moved > 0.3 && gt % 8 == 0) {
            Splashes.add(en.getX(), Splashes.surface(level, en.getX(), en.getY(), en.getZ()), en.getZ(), 0.15 + Math.min(0.3, (moved - 0.3) * 0.5));
        }
    }

    /** Where falling water lands: found every half second near the camera; each lands a ring now and then and throws a little mist. */
    private static void waterfalls(ClientLevel level, Vec3 cam) {
        long gt = level.getGameTime();
        if (gt - fallsAt >= 10 || gt < fallsAt) {
            fallsAt = gt; falls.clear();
            var p = new BlockPos.MutableBlockPos(); int cx = (int) cam.x, cz = (int) cam.z;
            for (int x = cx - 18; x <= cx + 18 && falls.size() < 14; x++) for (int z = cz - 18; z <= cz + 18 && falls.size() < 14; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, drop = 0;
                p.set(x, y, z);
                while (drop < 40 && falling(level, p)) { p.move(0, -1, 0); drop++; }
                if (drop >= 3 && level.getFluidState(p.above()).is(FluidTags.WATER)) falls.add(p.immutable().above());   // water fell at least three blocks and lands here
            }
        }
        for (BlockPos f : falls) {
            if (level.random.nextInt(7) == 0) add(f.getX() + 0.5 + (level.random.nextDouble() - 0.5), f.getZ() + 0.5 + (level.random.nextDouble() - 0.5), 0.55f);
            if (level.random.nextInt(8) == 0) Splashes.add(f.getX() + 0.5 + (level.random.nextDouble() - 0.5), f.getY() + 0.9, f.getZ() + 0.5 + (level.random.nextDouble() - 0.5), 0.45);
        }
    }
    private static boolean falling(ClientLevel level, BlockPos p) {
        var fs = level.getFluidState(p);
        return fs.is(FluidTags.WATER) && !fs.isSource() && fs.getOwnHeight() > 0 && level.getFluidState(p.below()).is(FluidTags.WATER) && fs.hasProperty(net.minecraft.world.level.material.FlowingFluid.FALLING) && fs.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING);
    }
}
