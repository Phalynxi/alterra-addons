package dev.phi.alterra.weather.snow;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.alterra.weather.storm.Storms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What a storm really moves. In a sandstorm the wind lifts sand off exposed edges (a block at a time) and throws it downwind, where it lands as part of a dune;
 * in a blizzard a layer of snow is carried downwind into a drift. Near the players only, in the biomes the storm belongs to, and as strong as the storm is.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class WindBlown {
    private WindBlown() {}
    private static TagKey<Biome> tag(Storms.Kind k) { return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(AlterraWeather.ID, "weather/" + k.tag)); }

    private static float level(ServerLevel l, ServerPlayer pl, Storms.Kind k) {
        if (!l.getBiome(pl.blockPosition()).is(tag(k))) return 0f;
        long t = l.getGameTime();
        return Storms.forced(k, t) ? 1f : Storms.level(k, t, Storms.region(pl.getX()), Storms.region(pl.getZ()));
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !HeavyConfig.get(HeavyConfig.WIND_MOVES_LOOSE) || l.getGameTime() % 10 != 7 || !l.dimensionType().hasSkyLight()) return;
        for (ServerPlayer pl : l.players()) {
            float sand = level(l, pl, Storms.Kind.SANDSTORM), snow = level(l, pl, Storms.Kind.BLIZZARD);
            if (sand < 0.3f && snow < 0.3f) continue;
            Storms.Kind k = sand >= snow ? Storms.Kind.SANDSTORM : Storms.Kind.BLIZZARD; float lvl = Math.max(sand, snow);
            double a = Storms.wind(k, l.getGameTime(), Storms.region(pl.getX()), Storms.region(pl.getZ())), wx = Math.cos(a), wz = Math.sin(a);
            int tries = 6 + Math.round(10 * lvl);
            for (int i = 0; i < tries; i++) {
                int x = pl.getBlockX() + l.random.nextInt(57) - 28, z = pl.getBlockZ() + l.random.nextInt(57) - 28;
                if (!l.hasChunkAt(new BlockPos(x, pl.getBlockY(), z))) continue;
                if (k == Storms.Kind.SANDSTORM) blowSand(l, x, z, wx, wz, lvl); else blowSnow(l, x, z, wx, wz, lvl);
            }
        }
    }

    private static void blowSand(ServerLevel l, int x, int z, double wx, double wz, float lvl) {
        if (l.random.nextFloat() > 0.07f * lvl) return;
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos p = new BlockPos(x, top, z); var st = l.getBlockState(p);
        if (!st.is(BlockTags.SAND) || !l.canSeeSky(p.above()) || l.getBlockState(p.below()).isAir()) return;
        int ux = x - (int) Math.round(wx), uz = z - (int) Math.round(wz);   // the upwind side must not stand higher: only exposed edges and windward slopes erode
        if (!l.hasChunkAt(new BlockPos(ux, top, uz)) || l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ux, uz) - 1 > top) return;
        FallingBlockEntity fb = FallingBlockEntity.fall(l, p, st);   // takes the block out and lets it fly
        fb.setDeltaMovement(wx * (0.45 + l.random.nextDouble() * 0.3), 0.22 + l.random.nextDouble() * 0.12, wz * (0.45 + l.random.nextDouble() * 0.3));
        fb.dropItem = false;   // if it finds no place to land it is simply gone, never an item on the ground
    }

    private static void blowSnow(ServerLevel l, int x, int z, double wx, double wz, float lvl) {
        if (l.random.nextFloat() > 0.14f * lvl) return;
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos s = SnowPhysics.snowAt(l, x, z, top);
        if (s == null || !l.canSeeSky(s.above())) return;
        int d = 3 + l.random.nextInt(7), nx = x + (int) Math.round(wx * d), nz = z + (int) Math.round(wz * d);
        if (!l.hasChunkAt(new BlockPos(nx, top, nz))) return;
        if (SnowPhysics.give(l, nx, nz, l.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz))) SnowPhysics.take(l, s);
    }
}
