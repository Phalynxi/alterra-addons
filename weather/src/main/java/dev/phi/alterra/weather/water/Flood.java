package dev.phi.alterra.weather.water;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.storm.HeavyStorm;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.PriorityQueue;

/**
 * Real water from the weather, put down as water blocks and taken away again afterwards:
 * <ul>
 * <li>The coast floods: where the storm surge (SeaGrid) has lifted the sea far enough, the water creeps from the shore into low land at sea level, a block at a time and up to
 * 24 blocks inland, and pours down into holes and cellars there. When the surge goes down it drains away.</li>
 * <li>Rain fills holes: dips and pits open to the sky (found by filling the ground around the player up to where water would spill over) collect the rain that falls on them and
 * around them, and fill a layer at a time, so a small pit fills in a storm while a wide hollow hardly gets a puddle. In dry weather it slowly dries up.</li>
 * </ul>
 * Every block it places is remembered (saved with the world), so all of it can be taken away again; it only ever fills air and never touches anything a player built.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class Flood {
    private Flood() {}
    private static final int COAST_REACH = 24, RAIN_RADIUS = 24;

    /** What has been placed, kept with the world: coast flood blocks (with how far inland each is) and rain water. */
    public static final class Data extends SavedData {
        final Long2IntOpenHashMap coast = new Long2IntOpenHashMap(); final LongOpenHashSet rain = new LongOpenHashSet();
        final Long2IntOpenHashMap stored = new Long2IntOpenHashMap();   // rain gathered per hollow (its lowest column), in thousandths of a block of water
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            tag.put("coast", new LongArrayTag(coast.keySet().toLongArray())); tag.put("rain", new LongArrayTag(rain.toLongArray()));
            return tag;
        }
        static Data load(CompoundTag tag, HolderLookup.Provider p) {
            Data d = new Data();
            for (long l : tag.getLongArray("coast")) d.coast.put(l, COAST_REACH);
            for (long l : tag.getLongArray("rain")) d.rain.add(l);
            return d;
        }
    }
    /** Is this a place the storm surge has flooded and still holds up (endless water while it lasts)? */
    public static boolean surging(ServerLevel level, BlockPos p) {
        if (!SeaGrid.active(level)) return false;
        Data d = data(level);
        return d.coast.containsKey(p.asLong()) && Surge.at(level, p.getX() + 0.5, p.getZ() + 0.5) >= 0.3f;
    }

    private static Data data(ServerLevel level) { return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), "alterra_weather_flood"); }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !level.dimensionType().hasSkyLight() || level.players().isEmpty()) return;
        long t = level.getGameTime();
        if (t % 20 == 0) coast(level, data(level));
        if (t % 100 == 50) rain(level, data(level));
    }

    private static boolean water(ServerLevel l, BlockPos p) { return l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER); }
    private static boolean placeable(ServerLevel l, BlockPos p) { return l.isLoaded(p) && l.getBlockState(p).isAir(); }
    private static boolean source(ServerLevel l, BlockPos p) { var fs = l.getFluidState(p); return fs.isSource() && fs.is(net.minecraft.tags.FluidTags.WATER) && l.getBlockState(p).is(Blocks.WATER); }

    // ---------------------------------------------------------------- the coast
    private static void coast(ServerLevel level, Data d) {
        int sea = level.getSeaLevel(), placed = 0;
        var p = new BlockPos.MutableBlockPos(); var q = new BlockPos.MutableBlockPos();
        boolean surging = SeaGrid.active(level);
        if (surging) for (ServerPlayer pl : level.players()) {
            int px = pl.getBlockX(), pz = pl.getBlockZ();
            for (int dz = -40; dz <= 40 && placed < 24; dz++) for (int dx = -40; dx <= 40 && placed < 24; dx++) {
                p.set(px + dx, sea, pz + dz);
                if (d.coast.containsKey(p.asLong()) || !placeable(level, p)) continue;
                q.set(p.getX(), sea - 1, p.getZ());
                if (!(water(level, q) || !level.getBlockState(q).getCollisionShape(level, q).isEmpty())) continue;   // something under it to hold water (or the sea itself)
                int reach = -1;
                for (Direction dir : Direction.Plane.HORIZONTAL) {   // next to the raised sea, or to water the flood already brought
                    q.set(p.getX() + dir.getStepX(), sea, p.getZ() + dir.getStepZ());
                    int prev = d.coast.getOrDefault(q.asLong(), -1);
                    if (prev > 0) reach = Math.max(reach, prev - 1);
                    q.setY(sea - 1);
                    if (level.getBlockState(q.above()).isAir() && water(level, q) && Surge.at(level, q.getX() + 0.5, q.getZ() + 0.5) >= 0.45f) reach = Math.max(reach, COAST_REACH);
                }
                if (reach <= 0 || Surge.at(level, p.getX() + 0.5, p.getZ() + 0.5) < 0.2f && reach < COAST_REACH - 2) continue;   // inland it goes as far as the surge carries it
                level.setBlock(p, Blocks.WATER.defaultBlockState(), 3); d.coast.put(p.asLong(), reach); d.setDirty(); placed++;
            }
        }
        // draining: where the surge has gone down (or everywhere, once the storm is over), farthest inland first. With finite water the water itself runs back into the sea.
        if (d.coast.isEmpty()) return;
        if (FiniteWater.on()) { if (!surging) { d.coast.clear(); d.setDirty(); } return; }
        LongArrayList gone = new LongArrayList();
        for (var en : d.coast.long2IntEntrySet()) {
            BlockPos b = BlockPos.of(en.getLongKey());
            if (!level.isLoaded(b)) continue;
            if (!surging || Surge.at(level, b.getX() + 0.5, b.getZ() + 0.5) < 0.3f) gone.add(en.getLongKey());
        }
        gone.sort((a, b) -> Integer.compare(d.coast.get(a), d.coast.get(b)));
        int removed = 0;
        for (long l : gone) {
            if (removed++ > 32) break;
            BlockPos b = BlockPos.of(l); d.coast.remove(l); d.setDirty();
            if (source(level, b)) level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            for (Direction dir : Direction.Plane.HORIZONTAL) {   // water that formed between flood blocks on its own (two sources make a third) goes with them
                BlockPos n = b.relative(dir);
                if (!d.coast.containsKey(n.asLong()) && source(level, n) && !water(level, n.below()) && level.getBlockState(n.above()).isAir()) level.setBlock(n, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    // ---------------------------------------------------------------- rain in hollows
    private static void rain(ServerLevel level, Data d) {
        float storm = Math.max(level.getRainLevel(1f), HeavyStorm.level(level) * 1.5f) + 0.5f * level.getThunderLevel(1f);
        if (level.isRaining() && storm > 0.2f) for (ServerPlayer pl : level.players()) fill(level, d, pl.getBlockX(), pl.getBlockZ(), storm);
        else if (!d.rain.isEmpty()) { if (FiniteWater.on()) { d.rain.clear(); d.stored.clear(); d.setDirty(); } else dry(level, d); }   // finite water dries up by itself
    }

    /** Fill the hollows around a point: the ground (where rain lands) is filled up to where water would spill out of the area, and each hollow collects the rain on it and around it. */
    private static void fill(ServerLevel level, Data d, int cx, int cz, float storm) {
        int n = RAIN_RADIUS * 2 + 1, ox = cx - RAIN_RADIUS, oz = cz - RAIN_RADIUS, sea = level.getSeaLevel();
        int[] g = new int[n * n], w = new int[n * n]; boolean[] seen = new boolean[n * n];
        var p = new BlockPos.MutableBlockPos();
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            int wx = ox + x, wz = oz + z;
            if (!level.hasChunk(wx >> 4, wz >> 4)) { g[x + z * n] = Integer.MIN_VALUE; continue; }
            g[x + z * n] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);   // the first air above whatever stops rain
        }
        // priority flood: from the edge inwards, every column's water level is the lowest it could drain at
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) if (x == 0 || z == 0 || x == n - 1 || z == n - 1 || g[x + z * n] == Integer.MIN_VALUE) {
            int i = x + z * n; seen[i] = true; w[i] = g[i]; pq.add(new int[] {i, w[i]});
        }
        while (!pq.isEmpty()) {
            int[] c = pq.poll(); int i = c[0], x = i % n, z = i / n;
            int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
            for (int[] b : nb) {
                if (b[0] < 0 || b[1] < 0 || b[0] >= n || b[1] >= n) continue;
                int j = b[0] + b[1] * n; if (seen[j]) continue;
                seen[j] = true; w[j] = Math.max(g[j], c[1]); pq.add(new int[] {j, w[j]});
            }
        }
        // each hollow (columns joined that hold water): its lowest column is its name; rain gathers there and fills it a layer at a time
        boolean[] done = new boolean[n * n]; int placed = 0;
        Biome.Precipitation[] rainHere = {null};
        for (int s = 0; s < n * n && placed < 48; s++) {
            if (done[s] || w[s] - g[s] < 1 || g[s] <= sea) continue;   // not a hollow, or the sea's business
            java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>(); java.util.List<Integer> cells = new java.util.ArrayList<>();
            stack.push(s); done[s] = true; int low = s;
            while (!stack.isEmpty()) {
                int i = stack.pop(); cells.add(i); if (g[i] < g[low]) low = i;
                int x = i % n, z = i / n;
                int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
                for (int[] b : nb) {
                    if (b[0] < 0 || b[1] < 0 || b[0] >= n || b[1] >= n) continue;
                    int j = b[0] + b[1] * n;
                    if (!done[j] && w[j] - g[j] >= 1 && w[j] == w[s]) { done[j] = true; stack.push(j); }
                }
            }
            p.set(ox + low % n, g[low], oz + low / n);
            if (level.getBiome(p).value().getPrecipitationAt(p) != Biome.Precipitation.RAIN || !level.canSeeSky(p)) continue;
            long key = p.asLong();
            int gained = Math.round(cells.size() * 3 * storm * 10);   // the rain on the hollow and on the slopes that run into it, in thousandths of a block
            int have = d.stored.get(key) + gained;
            // the next layer up: every column of the hollow whose ground is at or below it and that is still air there
            for (int y = g[low]; y < w[s] && placed < 48; y++) {
                java.util.List<BlockPos> layer = new java.util.ArrayList<>();
                for (int i : cells) if (g[i] <= y) { BlockPos b = new BlockPos(ox + i % n, y, oz + i / n); if (!water(level, b)) layer.add(b); }
                if (layer.isEmpty()) continue;   // this layer is full already
                if (have < layer.size() * 1000) break;   // not enough rain yet for the whole layer
                boolean ok = true; for (BlockPos b : layer) if (!placeable(level, b)) { ok = false; break; }
                if (!ok) break;   // something is in the way: leave it
                for (BlockPos b : layer) { level.setBlock(b, Blocks.WATER.defaultBlockState(), 3); d.rain.add(b.asLong()); placed++; }
                have -= layer.size() * 1000; d.setDirty();
            }
            d.stored.put(key, Math.min(have, cells.size() * 2000));
        }
    }

    /** Dry weather: rain water goes down a little each time, the top first. */
    private static void dry(ServerLevel level, Data d) {
        d.stored.clear();
        LongArrayList all = new LongArrayList(d.rain);
        all.sort((a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a)));
        int removed = 0;
        for (long l : all) {
            BlockPos b = BlockPos.of(l);
            if (!level.isLoaded(b)) continue;
            if (removed++ >= 6) break;
            d.rain.remove(l); d.setDirty();
            if (source(level, b) && !water(level, b.above())) level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
