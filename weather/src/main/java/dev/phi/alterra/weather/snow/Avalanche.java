package dev.phi.alterra.weather.snow;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.alterra.weather.storm.HeavyStorm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Avalanches: snow on a steep slope breaks loose and slides down as falling snow, piling up where it lands (and taking more snow with it on the way).
 * Heavy snowfall sets them off now and then near the players (more in thunder and heavy storms), and an explosion near snowy slopes sets them off around it.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class Avalanche {
    private Avalanche() {}
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static boolean snowy(BlockState st) { return st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.SNOW) || st.is(Blocks.POWDER_SNOW); }

    /** The way downhill from the top of this column, if the slope is steep (two blocks or more down within two), else null. */
    private static Direction downhill(ServerLevel l, int x, int z, int top) {
        Direction best = null; int drop = 1;
        for (Direction d : SIDES) {
            int h = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x + d.getStepX() * 2, z + d.getStepZ() * 2);
            if (top - h > drop) { drop = top - h; best = d; }
        }
        return best;
    }

    /** Let the snow around a spot go, if it lies on a steep slope: up to radius blocks around it. Returns how many blocks slid. */
    static int release(ServerLevel l, BlockPos at, int radius) {
        int moved = 0;
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            if (dx * dx + dz * dz > radius * radius || l.random.nextInt(3) == 0) continue;
            int x = at.getX() + dx, z = at.getZ() + dz;
            if (!l.hasChunkAt(new BlockPos(x, at.getY(), z))) continue;
            int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos p = new BlockPos(x, top, z);   // snow layers sit in the first air (they do not block motion), snow blocks are the top itself
            BlockState st = l.getBlockState(p);
            if (!snowy(st)) { p = p.below(); st = l.getBlockState(p); }
            if (!snowy(st)) continue;
            Direction d = downhill(l, x, z, top);
            if (d == null) continue;
            boolean block = !st.is(Blocks.SNOW);
            int layers = block ? 4 : st.getValue(SnowLayerBlock.LAYERS);   // a snow block sheds its top half, a layer goes whole
            FallingBlockEntity fb = FallingBlockEntity.fall(l, p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
            if (block) { l.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4), 3); fb.setPos(fb.getX(), p.getY() + 0.55, fb.getZ()); }
            fb.setDeltaMovement(new Vec3(d.getStepX() * (0.25 + l.random.nextDouble() * 0.2), 0.05, d.getStepZ() * (0.25 + l.random.nextDouble() * 0.2)));   // sliding down the slope
            fb.dropItem = false;
            fb.disableDrop();   // it does not set itself down as a block (that stacked whole blocks into towers): landed() merges it into the snow it lands on
            SLIDING.put(fb, layers);
            moved++;
        }
        if (moved > 0) l.playSound(null, at, SoundEvents.SNOW_BREAK, SoundSource.BLOCKS, Math.min(4f, 1f + moved * 0.15f), 0.5f);
        return moved;
    }

    /** Snow on its way down, and how many layers it carries. */
    private static final java.util.Map<FallingBlockEntity, Integer> SLIDING = new java.util.WeakHashMap<>();

    /** Sliding snow that has come to rest: its layers go into the snow where it stopped (topping up a layer first, then piling on), so it lands as a drift, not as blocks. */
    private static void landed(ServerLevel l) {
        if (SLIDING.isEmpty()) return;
        var it = SLIDING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next(); FallingBlockEntity fb = en.getKey();
            if (fb.level() != l) continue;
            if (!fb.isRemoved()) { if (fb.time > 400) { fb.discard(); it.remove(); } continue; }
            it.remove();
            if (fb.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED) deposit(l, fb.blockPosition(), en.getValue());
        }
    }

    private static void deposit(ServerLevel l, BlockPos at, int layers) {
        BlockPos p = at;
        for (int i = 0; i < 6 && l.getBlockState(p.below()).isAir(); i++) p = p.below();   // settle onto what is underneath
        for (int guard = 0; layers > 0 && guard < 4; guard++, p = p.above()) {
            BlockState st = l.getBlockState(p);
            if (st.is(Blocks.SNOW)) {
                int n = st.getValue(SnowLayerBlock.LAYERS), add = Math.min(8 - n, layers);
                if (add > 0) { l.setBlock(p, n + add >= 8 ? Blocks.SNOW_BLOCK.defaultBlockState() : st.setValue(SnowLayerBlock.LAYERS, n + add), 3); layers -= add; }
            } else if ((st.isAir() || st.canBeReplaced()) && st.getFluidState().isEmpty()) {
                BlockState put = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, layers));
                if (!put.canSurvive(l, p)) return;
                l.setBlock(p, put, 3); layers -= Math.min(8, layers);
            }
        }
    }

    /** Weight on a slope: someone running (or jumping) across steep snow can set it off, a walker rarely. Checked twice a second for each player. */
    private static void weight(ServerLevel l) {
        for (ServerPlayer pl : l.players()) {
            if (pl.isSpectator() || pl.isCreative() && pl.getAbilities().flying || !pl.onGround()) continue;
            BlockPos at = pl.blockPosition();
            int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
            if (SnowPhysics.snowAt(l, at.getX(), at.getZ(), top) == null || downhill(l, at.getX(), at.getZ(), top) == null) continue;   // only on steep snow
            double speed = Math.sqrt(pl.getDeltaMovement().x * pl.getDeltaMovement().x + pl.getDeltaMovement().z * pl.getDeltaMovement().z);
            float chance = pl.isSprinting() ? 0.10f : speed > 0.05 ? 0.015f : 0f;
            if (l.random.nextFloat() < chance) release(l, at, 2 + l.random.nextInt(3));
        }
    }

    /** Lightning shakes the snow loose around where it strikes. */
    @SubscribeEvent
    public static void struck(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel l && e.getEntity() instanceof net.minecraft.world.entity.LightningBolt && HeavyConfig.get(HeavyConfig.AVALANCHES)) release(l, e.getEntity().blockPosition(), 9);
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel sl) landed(sl);
        if (e.getLevel() instanceof ServerLevel w && HeavyConfig.get(HeavyConfig.AVALANCHES) && w.getGameTime() % 10 == 5) weight(w);
        if (!(e.getLevel() instanceof ServerLevel l) || !HeavyConfig.get(HeavyConfig.AVALANCHES) || !l.isRaining() || l.getGameTime() % 40 != 7) return;
        float storm = Math.max(l.getThunderLevel(1f), HeavyStorm.level(l));
        for (ServerPlayer pl : l.players()) {
            if (l.random.nextFloat() > 0.04f + 0.25f * storm) continue;   // heavy snowfall: now and then; a blizzard: often
            int x = pl.getBlockX() + l.random.nextInt(97) - 48, z = pl.getBlockZ() + l.random.nextInt(97) - 48;
            BlockPos top = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            if (!l.getBiome(top).value().coldEnoughToSnow(top) || !l.isRainingAt(top.above())) continue;
            release(l, top, 3 + l.random.nextInt(4));
        }
    }

    /** An explosion near snowy slopes shakes the snow loose around it. */
    @SubscribeEvent
    public static void blast(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !HeavyConfig.get(HeavyConfig.AVALANCHES)) return;
        var c = e.getExplosion().center();
        release(l, BlockPos.containing(c), 10);
    }
}
