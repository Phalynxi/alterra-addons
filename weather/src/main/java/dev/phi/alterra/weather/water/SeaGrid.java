package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.net.SeaSync;
import dev.phi.alterra.weather.storm.HeavyStorm;

import dev.phi.alterra.api.AlterraApi;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The sea as a grid of water columns (8 by 8 blocks each) around the players, run on the server: every column has a height above the normal sea and a flow to its east and south neighbours (shallow water equations).
 * Water runs from high columns to low ones, the wind pushes it along and piles it up against the coast, land is a wall, and in a really bad storm the whole open sea is lifted (a storm surge, up to about a block).
 * Calm weather leaves it flat and costs nothing. The players get the square around them twice a second; entities swim and boats float against the raised surface on both sides (see {@link Surge}).
 * The water blocks themselves are never changed.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class SeaGrid {
    public static final int CELL = 8, T = 16;   // blocks per cell, cells per tile side
    private static final float G = 9.8f, DEPTH = 4f, DAMP = 0.6f, RELAX = 20f, WIND = 0.03f, WET = 0.05f, DT = 0.05f, MAX = 1.25f;
    private static final Map<ResourceKey<Level>, SeaGrid> GRIDS = new HashMap<>();

    private static final class Tile {
        final int tx, tz; final float[] open = new float[T * T], h = new float[T * T], u = new float[T * T], v = new float[T * T];
        long seen, openAt = Long.MIN_VALUE;
        Tile(int tx, int tz) { this.tx = tx; this.tz = tz; }
    }

    private final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
    private final Set<UUID> sentWater = new HashSet<>();
    private volatile boolean active;

    private static long key(int tx, int tz) { return ((long) tx << 32) ^ (tz & 0xffffffffL); }

    public static boolean active(ServerLevel level) { var g = GRIDS.get(level.dimension()); return g != null && g.active; }

    /** The surge at a point, in blocks (bilinear between column centres). */
    public static float height(ServerLevel level, double x, double z) {
        var g = GRIDS.get(level.dimension());
        if (g == null || !g.active) return 0f;
        double fx = x / CELL - 0.5, fz = z / CELL - 0.5;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz); double tx = fx - x0, tz = fz - z0;
        double a = g.h(x0, z0) + (g.h(x0 + 1, z0) - g.h(x0, z0)) * tx, b = g.h(x0, z0 + 1) + (g.h(x0 + 1, z0 + 1) - g.h(x0, z0 + 1)) * tx;
        return (float) (a + (b - a) * tz);
    }

    private float h(int cx, int cz) {
        Tile t = tiles.get(key(Math.floorDiv(cx, T), Math.floorDiv(cz, T)));
        return t == null ? 0f : t.h[Math.floorMod(cx, T) + Math.floorMod(cz, T) * T];
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !level.dimensionType().hasSkyLight()) return;
        if (level.players().isEmpty()) { GRIDS.remove(level.dimension()); return; }
        GRIDS.computeIfAbsent(level.dimension(), k -> new SeaGrid()).run(level);
    }

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) { GRIDS.clear(); }

    private void run(ServerLevel level) {
        long now = level.getGameTime();
        var pos = new BlockPos.MutableBlockPos();
        for (ServerPlayer p : level.players()) {   // keep the tiles around every player, and learn where the sea is
            int ptx = Math.floorDiv(p.getBlockX(), CELL * T), ptz = Math.floorDiv(p.getBlockZ(), CELL * T);
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                Tile t = tiles.computeIfAbsent(key(ptx + dx, ptz + dz), k -> new Tile((int) (k >> 32), (int) k));
                t.seen = now;
                if (now - t.openAt >= 400) { t.openAt = now; open(level, t, pos); }
            }
        }
        tiles.values().removeIf(t -> now - t.seen > 200);

        float storm = Math.max(HeavyStorm.level(level), Math.max(0.7f * level.getThunderLevel(1f), 0.35f * level.getRainLevel(1f)));
        float target = 1.1f * smooth(0.55f, 1.0f, storm);   // only a really bad storm lifts the sea
        float push = WIND * storm;
        boolean moving = false;
        for (Tile t : tiles.values()) for (int i = 0; i < T * T; i++) if (Math.abs(t.h[i]) > 0.002f || Math.abs(t.u[i]) > 0.002f || Math.abs(t.v[i]) > 0.002f) { moving = true; break; }
        if (target > 0.001f || moving) { step(level, target, push); active = true; }
        else if (active) { for (Tile t : tiles.values()) { java.util.Arrays.fill(t.h, 0f); java.util.Arrays.fill(t.u, 0f); java.util.Arrays.fill(t.v, 0f); } active = false; }
        if (now % 10 == 0) send(level);
    }

    private static float smooth(float a, float b, float x) { float t = Math.max(0f, Math.min(1f, (x - a) / (b - a))); return t * t * (3f - 2f * t); }

    private static void open(ServerLevel level, Tile t, BlockPos.MutableBlockPos pos) {
        for (int j = 0; j < T; j++) for (int i = 0; i < T; i++) {
            float o = SeaOpen.at(level, (t.tx * T + i) * CELL + CELL / 2, (t.tz * T + j) * CELL + CELL / 2, pos);
            t.open[i + j * T] = Math.max(0f, o);   // not loaded yet: land for now, looked at again later
        }
    }

    private void step(ServerLevel level, float target, float push) {
        float damp = 1f - DAMP * DT;
        for (Tile t : tiles.values()) {   // flow: pushed down the slope of the surface and along the wind; none into land
            Tile east = tiles.get(key(t.tx + 1, t.tz)), south = tiles.get(key(t.tx, t.tz + 1));
            var w = AlterraApi.wind(level, (t.tx * T + T / 2.0) * CELL, (t.tz * T + T / 2.0) * CELL);
            float wx = (float) w.x * push, wz = (float) w.z * push;
            for (int j = 0; j < T; j++) for (int i = 0; i < T; i++) {
                int k = i + j * T; float o = t.open[k];
                Tile et = i < T - 1 ? t : east; int ek = i < T - 1 ? k + 1 : j * T;
                if (o < WET || et == null || et.open[ek] < WET) t.u[k] = 0f;
                else t.u[k] = (t.u[k] + DT * (-G * (et.h[ek] - t.h[k]) / CELL + wx * 0.5f * (o + et.open[ek]))) * damp;
                Tile st = j < T - 1 ? t : south; int sk = j < T - 1 ? k + T : i;
                if (o < WET || st == null || st.open[sk] < WET) t.v[k] = 0f;
                else t.v[k] = (t.v[k] + DT * (-G * (st.h[sk] - t.h[k]) / CELL + wz * 0.5f * (o + st.open[sk]))) * damp;
            }
        }
        for (Tile t : tiles.values()) {   // height: what flows in minus what flows out, and the open sea drawn towards the storm's level
            Tile west = tiles.get(key(t.tx - 1, t.tz)), north = tiles.get(key(t.tx, t.tz - 1));
            for (int j = 0; j < T; j++) for (int i = 0; i < T; i++) {
                int k = i + j * T; float o = t.open[k];
                if (o < WET) { t.h[k] = 0f; continue; }
                float uw = i > 0 ? t.u[k - 1] : west == null ? 0f : west.u[T - 1 + j * T];
                float vn = j > 0 ? t.v[k - T] : north == null ? 0f : north.v[i + (T - 1) * T];
                float hk = t.h[k] - DT * DEPTH * ((t.u[k] - uw) + (t.v[k] - vn)) / CELL;
                hk += (target * o - hk) * DT / RELAX;
                t.h[k] = Math.max(-MAX, Math.min(MAX, hk));
            }
        }
    }

    private void send(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (!p.connection.hasChannel(SeaSync.TYPE) || (!active && !sentWater.contains(p.getUUID()))) continue;   // a game without this addon; or calm, and that player already knows
            int N = SeaSync.N, ox = Math.floorDiv(p.getBlockX(), CELL) - N / 2, oz = Math.floorDiv(p.getBlockZ(), CELL) - N / 2;
            byte[] b = new byte[N * N]; boolean any = false;
            for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) { int q = Math.round(h(ox + i, oz + j) * 100f); b[i + j * N] = (byte) Math.max(-127, Math.min(127, q)); any |= q != 0; }
            if (any) sentWater.add(p.getUUID()); else sentWater.remove(p.getUUID());
            PacketDistributor.sendToPlayer(p, new SeaSync(ox, oz, b));
        }
    }
}
