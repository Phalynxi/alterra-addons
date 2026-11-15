package dev.phi.alterra.weather.snow;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.WeatherHooks;
import dev.phi.alterra.weather.config.WeatherConfig;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Prints where players and mobs walk on snow, sand and mud: a boot print every step, left and right, that stays a while (less in rain and wind)
 * and a puff of snow or sand with each step, a bigger one when something lands. Client only, drawn for everything near the camera.
 */
@EventBusSubscriber(modid = AlterraWeather.ID, value = Dist.CLIENT)
public final class Footprints {
    private Footprints() {}
    private static final float STRIDE = 0.5f;   // walkDist between two steps (a vanilla walk adds 0.6 per block moved: about 0.8 blocks a step)
    private static final Int2FloatOpenHashMap LAST = new Int2FloatOpenHashMap();   // per entity: the stride count at its last print
    private static final Int2FloatOpenHashMap FELL = new Int2FloatOpenHashMap();   // per entity: its downward speed a tick ago (for landings)
    private static boolean broken;

    private enum Ground { SNOW, SAND, MUD, SOFT, WET }   // SOFT: dirt and grass, WET: the same in the rain (darker, longer): a soft print, no dust

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (broken || !WeatherConfig.get(WeatherConfig.FOOTPRINTS)) return;
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) { LAST.clear(); FELL.clear(); return; }
        try { step(mc, level); }
        catch (Throwable t) { broken = true; org.slf4j.LoggerFactory.getLogger("Alterra Weather").error("Footprints failed; they are switched off until the game restarts", t); }
    }

    private static void step(Minecraft mc, ClientLevel level) {
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        float quality = mc.options.particles().get() == net.minecraft.client.ParticleStatus.ALL ? 1f : mc.options.particles().get() == net.minecraft.client.ParticleStatus.DECREASED ? 0.5f : 0.2f;
        float rain = level.getRainLevel(1f);
        int done = 0;
        for (var en : level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || le instanceof ArmorStand || le.isSpectator() || le.isInvisible() || le.isPassenger() || le.isInWaterOrBubble()) continue;
            if (le.distanceToSqr(cam.x, cam.y, cam.z) > 24 * 24) continue;
            int id = le.getId();
            float dyBefore = FELL.getOrDefault(id, 0f);
            FELL.put(id, (float) le.getDeltaMovement().y);
            if (!le.onGround()) continue;
            float puddle = WeatherHooks.puddleAt.apply(Mth.floor(le.getX()), Mth.floor(le.getZ()));
            boolean inPuddle = puddle > 0.2f;
            Ground g = inPuddle ? null : ground(level, le.getX(), le.getY(), le.getZ(), rain > 0.3f || puddle > 0.05f);
            if (dyBefore < -0.45f) {   // landed hard
                if (inPuddle) splash(level, le, puddle, (int) (9 * quality), 0.18f);
                else if (g != null && dusty(g)) puff(level, le, g, (int) (10 * quality), 0.05f);
            }
            if (g == null && !inPuddle) { LAST.put(id, le.walkDist / STRIDE); continue; }
            float strides = le.walkDist / STRIDE;
            if (!LAST.containsKey(id) || Math.abs(strides - LAST.get(id)) > 6f) { LAST.put(id, strides); continue; }
            int now = (int) Math.floor(strides), was = (int) Math.floor(LAST.get(id));
            if (now == was) continue;
            LAST.put(id, strides);
            if (done++ > 8) break;
            if (inPuddle) { splash(level, le, puddle, Math.round((le.isSprinting() ? 6f : 3f) * quality + 0.5f), le.isSprinting() ? 0.14f : 0.09f); continue; }   // water, not a print: rings and drops
            print(level, le, g, now & 1, rain);
            if (!dusty(g)) continue;
            float want = (le.isSprinting() ? 4f : 2f) * (g == Ground.SNOW ? 1.5f : 1f) * quality;   // a step kicks up a few specks, more at a run and in snow
            puff(level, le, g, (int) want + (level.random.nextFloat() < want % 1f ? 1 : 0), 0.02f);
        }
        if (LAST.size() > 400) { LAST.clear(); FELL.clear(); }
    }

    /** A step or a landing in a puddle: rings spreading on it and drops thrown up, more the deeper it is. */
    private static void splash(ClientLevel level, LivingEntity le, float depth, int drops, float up) {
        WeatherHooks.splashOnLand.accept(new double[] {le.getX(), le.getZ(), 1.0, 0.012});
        var r = level.random; double y = surface(level, le.getX(), le.getY(), le.getZ()) + 0.03;
        level.addParticle(AlterraWeather.RIPPLE.get(), le.getX() + (r.nextDouble() - 0.5) * 0.3, y, le.getZ() + (r.nextDouble() - 0.5) * 0.3, 0, 0, 0);
        for (int i = 0; i < drops; i++)
            level.addParticle(AlterraWeather.SPRAY.get(), le.getX() + (r.nextDouble() - 0.5) * 0.5, y, le.getZ() + (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.08, up * (0.6 + r.nextDouble() * 0.8), (r.nextDouble() - 0.5) * 0.08);
    }

    /** What the thing stands on, if it is snow, sand or mud. */
    private static Ground ground(ClientLevel level, double x, double y, double z, boolean wet) {
        BlockPos feet = BlockPos.containing(x, y + 0.01, z); BlockState st = level.getBlockState(feet);
        if (st.is(Blocks.SNOW)) return Ground.SNOW;
        BlockPos below = feet.below(); BlockState bs = level.getBlockState(below);
        if (bs.is(Blocks.SNOW_BLOCK)) return Ground.SNOW;
        if (bs.is(BlockTags.SAND)) return Ground.SAND;
        if (bs.is(Blocks.MUD)) return Ground.MUD;
        if (bs.is(BlockTags.DIRT) || bs.is(Blocks.GRAVEL) || bs.is(Blocks.CLAY)) return wet ? Ground.WET : Ground.SOFT;   // grass and dirt print too, more in the rain
        return null;
    }

    /** The height of the surface under a thing: a snow layer's own height, or the top of the block below. */
    private static double surface(ClientLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + 0.01, z); BlockState st = level.getBlockState(feet);
        if (st.is(Blocks.SNOW)) return feet.getY() + st.getShape(level, feet).max(net.minecraft.core.Direction.Axis.Y);
        BlockPos below = feet.below();
        return below.getY() + level.getBlockState(below).getShape(level, below).max(net.minecraft.core.Direction.Axis.Y);
    }

    private static int colour(ClientLevel level, double x, double y, double z, Ground g) {
        if (g == Ground.SNOW) return 0x8DA2BA;   // snow in shadow: a soft blue-grey
        BlockPos below = BlockPos.containing(x, y - 0.5, z);
        int c = level.getBlockState(below).getMapColor(level, below).col;
        float k = g == Ground.MUD ? 0.55f : g == Ground.WET ? 0.5f : g == Ground.SOFT ? 0.58f : 0.66f;
        return (Math.round(((c >> 16) & 255) * k) << 16) | (Math.round(((c >> 8) & 255) * k) << 8) | Math.round((c & 255) * k);
    }

    /** Does a step on this ground kick up dust or snow. */
    private static boolean dusty(Ground g) { return g == Ground.SNOW || g == Ground.SAND || g == Ground.MUD; }

    private static void print(ClientLevel level, LivingEntity le, Ground g, int foot, float rain) {
        if (g == Ground.SNOW && level.getBlockState(BlockPos.containing(le.getX(), le.getY() + 0.01, le.getZ())).is(Blocks.SNOW)) return;   // layers of snow are pressed down for real (SnowPhysics): no picture on top
        double yaw = Math.toRadians(le.yBodyRot), side = (foot == 0 ? -1 : 1) * 0.14 * Math.max(0.6, le.getBbWidth() / 0.6);
        double x = le.getX() - Math.cos(yaw) * side, z = le.getZ() - Math.sin(yaw) * side;   // sideways from where it faces (the facing vector is (-sin, cos))
        double y = surface(level, x, le.getY(), z) + 0.012;
        int life = Math.round((g == Ground.SNOW ? 900 : g == Ground.SAND ? 700 : g == Ground.WET ? 500 : g == Ground.SOFT ? 380 : 1200) * (1f - 0.65f * rain) * (0.85f + 0.3f * level.random.nextFloat()));
        level.addParticle(ColorParticleOption.create(AlterraWeather.PRINT.get(), 0xFF000000 | colour(level, x, le.getY(), z, g)), x, y, z, foot, Math.toRadians(le.yBodyRot), life);
    }

    private static void puff(ClientLevel level, LivingEntity le, Ground g, int n, float up) {
        if (n <= 0) return;
        double y = surface(level, le.getX(), le.getY(), le.getZ()) + 0.03;
        var r = level.random;
        for (int i = 0; i < n; i++) {
            int rgb = g == Ground.SNOW ? 0xF2F7FC : g == Ground.SAND ? (r.nextBoolean() ? 0xE4CB92 : 0xC9A56A) : 0x4A3626;
            level.addParticle(ColorParticleOption.create(AlterraWeather.DUST.get(), 0xFF000000 | rgb), le.getX() + (r.nextDouble() - 0.5) * 0.4, y, le.getZ() + (r.nextDouble() - 0.5) * 0.4,
                    (r.nextDouble() - 0.5) * 0.05, up * (0.4 + r.nextDouble()), (r.nextDouble() - 0.5) * 0.05);
        }
    }
}
