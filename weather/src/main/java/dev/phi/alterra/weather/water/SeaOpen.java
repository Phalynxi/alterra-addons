package dev.phi.alterra.weather.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** How open the sea is at one block column, 0 on land and in sheltered shallows to 1 over deep open ocean. The client's sea map and the server's water grid both use this, so they agree. */
public final class SeaOpen {
    private SeaOpen() {}

    public static float biome(Level level, BlockPos pos) {
        String p = level.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (p.contains("river") || p.contains("swamp") || p.contains("mangrove") || p.contains("lagoon") || p.contains("bay") || p.contains("marsh") || p.contains("beach")) return 0.15f;
        if (p.contains("deep") || p.contains("abyss") || p.contains("trench")) return 1.0f;
        if (p.contains("ocean") || p.contains("sea")) return 0.8f;
        return 0.5f;
    }

    /** -1 when the chunk is not loaded. */
    public static float at(Level level, int x, int z, BlockPos.MutableBlockPos pos) {
        if (!level.hasChunk(x >> 4, z >> 4)) return -1f;
        int sea = level.getSeaLevel(), depth = sea - level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        if (depth < 3) return 0f;
        pos.set(x, sea - 1, z);
        return Math.min(1f, (depth - 3f) / 38f) * (0.55f + 0.45f * biome(level, pos));
    }
}
