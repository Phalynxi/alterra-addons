package dev.phi.alterra.weather.disaster;

import dev.phi.alterra.weather.AlterraWeather;
import dev.phi.alterra.weather.Wind;
import dev.phi.alterra.weather.config.DisasterConfig;
import dev.phi.alterra.weather.net.DisasterSync;
import dev.phi.alterra.weather.storm.HeavyStorm;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The disasters that move: tornadoes (in heavy thunderstorms), dust devils (hot desert days) and vortexes (whirlpools in the open sea).
 * Each is one event with a position that travels, pulls things in and round, and is told to the clients near it (DisasterSync), where Alterra Illuminate draws the funnel.
 * They only happen where Illuminate is installed. Hail lives in {@link Hail}.
 */
@EventBusSubscriber(modid = AlterraWeather.ID)
public final class Disasters {
    private Disasters() {}
    public static final int TORNADO = 0, DUST_DEVIL = 1, VORTEX = 2;
    private static final String[] NAMES = {"tornado", "dustdevil", "vortex"};

    /** A block the tornado has torn loose: it is a falling block that is steered every tick (orbit, lift) until it is let go and flung. The marker start position tells the client to spin it. */
    static final class Debris { net.minecraft.world.entity.item.FallingBlockEntity fb; int age, life; float lift, speed; }
    public static final BlockPos SPIN_MARK = new BlockPos(0, -999, 0);   // a falling block's start position is synced to the client: this one marks "tumbling debris"

    public static final class Event {
        int id, type, age, life; double x, y, z, vx, vz; float radius, top, heading, strength, spin = 1f; boolean still, fixed; final List<Debris> debris = new ArrayList<>();   // still: stays where it started (for testing)
    }
    private static final Map<net.minecraft.resources.ResourceKey<Level>, List<Event>> EVENTS = new HashMap<>();
    private static int nextId = 1;

    /** Disasters are drawn by Alterra Illuminate, so they only happen where it is installed (on a server: the clients need it, the server lists it as optional). */
    public static boolean available() { return ModList.get().isLoaded("alterra_illuminate"); }

    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { EVENTS.clear(); }

    private static List<Event> list(ServerLevel l) { return EVENTS.computeIfAbsent(l.dimension(), k -> new ArrayList<>()); }

    // ---------------------------------------------------------------- starting them
    /** Start one at a spot; returns it. */
    static Event start(ServerLevel l, int type, double x, double z) {
        Event e = new Event(); e.id = nextId++; e.type = type; e.x = x; e.z = z; e.heading = l.random.nextFloat() * 6.28f;
        e.y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);   // the ground, or the top of the water for a whirlpool
        switch (type) {
            case TORNADO -> { e.radius = 3.5f + l.random.nextFloat() * 3f; e.top = 130f; e.life = 1500 + l.random.nextInt(2400); }
            case DUST_DEVIL -> { e.radius = 0.8f + l.random.nextFloat() * 0.8f; e.top = 16f + l.random.nextFloat() * 14f; e.life = 500 + l.random.nextInt(700); }
            default -> { e.radius = 6f + l.random.nextFloat() * 4f; e.top = 14f; e.life = 1500 + l.random.nextInt(1500); }
        }
        if (type == VORTEX) e.spin = l.random.nextBoolean() ? 1f : -1f;
        list(l).add(e);
        org.slf4j.LoggerFactory.getLogger("Alterra Weather").info("[disaster] {} {} starts at {} {} {} (radius {}, height {}, lasts {} ticks)", e.id, NAMES[type], (int) e.x, (int) e.y, (int) e.z, e.radius, e.top, e.life);
        return e;
    }

    /** Put down a whirlpool (a swirl field on the water): for other mods, commands and machines. radius in blocks (3 to 30), strength 0 to 1, ticks it lasts (Integer.MAX_VALUE: for good), spin 1 or -1 (the way it turns). */
    public static Event addVortex(ServerLevel l, double x, double z, float radius, float strength, int ticks, int spin) {
        Event e = start(l, VORTEX, x, z); e.radius = Math.max(2f, Math.min(40f, radius)); e.strength = strength; e.life = ticks; e.spin = spin >= 0 ? 1f : -1f; e.still = true; e.fixed = true;
        return e;
    }

    private static int count(ServerLevel l, int type) { int n = 0; for (Event e : list(l)) if (e.type == type) n++; return n; }

    private static boolean nearAny(ServerLevel l, double x, double z, double d) { for (Event e : list(l)) if ((e.x - x) * (e.x - x) + (e.z - z) * (e.z - z) < d * d) return true; return false; }

    /** Now and then, near a player, if the conditions are right. Checked once a second. */
    private static void roll(ServerLevel l) {
        float rate = DisasterConfig.get(DisasterConfig.RATE).floatValue();
        if (rate <= 0f) return;
        float heavy = HeavyStorm.level(l);
        for (ServerPlayer pl : l.players()) {
            double a = l.random.nextDouble() * Math.PI * 2, d = 45 + l.random.nextDouble() * 65;
            double x = pl.getX() + Math.cos(a) * d, z = pl.getZ() + Math.sin(a) * d;
            if (!l.hasChunkAt(BlockPos.containing(x, 64, z)) || nearAny(l, x, z, 60)) continue;
            BlockPos ground = new BlockPos((int) x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z) - 1, (int) z);
            var st = l.getBlockState(ground);
            if (DisasterConfig.get(DisasterConfig.TORNADOES) && heavy > 0.55f && count(l, TORNADO) < 2 && st.getFluidState().isEmpty()
                    && l.random.nextFloat() < 0.0012f * heavy * rate) start(l, TORNADO, x, z);
            else if (DisasterConfig.get(DisasterConfig.DUST_DEVILS) && !l.isRaining() && l.isDay() && st.is(BlockTags.SAND) && count(l, DUST_DEVIL) < 3
                    && l.random.nextFloat() < 0.004f * rate) start(l, DUST_DEVIL, x, z);
            else if (DisasterConfig.get(DisasterConfig.VORTEXES) && count(l, VORTEX) < 1 && l.random.nextFloat() < 0.0015f * rate) {
                var b = l.getBiome(ground);
                BlockPos sea = new BlockPos((int) x, l.getSeaLevel() - 1, (int) z);
                if (b.is(net.minecraft.tags.BiomeTags.IS_OCEAN) && l.getFluidState(sea).is(net.minecraft.tags.FluidTags.WATER) && l.getFluidState(sea.below(5)).is(net.minecraft.tags.FluidTags.WATER)) start(l, VORTEX, x, z);
            }
        }
    }

    // ---------------------------------------------------------------- every tick
    @SubscribeEvent
    public static void tick(LevelTickEvent.Post ev) {
        if (!(ev.getLevel() instanceof ServerLevel l) || !l.dimensionType().hasSkyLight() || !available()) return;
        if (l.getGameTime() % 20 == 11) roll(l);
        var events = EVENTS.get(l.dimension());
        if (events == null || events.isEmpty()) { Vortex.refresh(java.util.List.of()); return; }
        var it = events.iterator();
        while (it.hasNext()) {
            Event e = it.next();
            if (++e.age > e.life || l.players().isEmpty()) { if (e.type == VORTEX) { Vortex.refresh(java.util.List.of()); Vortex.release(l, e); } for (Debris d : e.debris) if (!d.fb.isRemoved()) d.fb.setDeltaMovement(d.fb.getDeltaMovement().add(0.2, 0.1, 0.2)); it.remove(); continue; }
            float ramp = Math.min(1f, Math.min(e.age / 120f, (e.life - e.age) / 120f)); if (!e.fixed) e.strength = ramp;
            move(l, e);
            if (!l.hasChunkAt(BlockPos.containing(e.x, 64, e.z))) { it.remove(); continue; }
            if (DisasterConfig.get(DisasterConfig.PULL_THINGS)) { if (e.type == VORTEX) Vortex.apply(l, e); else pull(l, e); }
            if (e.type == TORNADO && DisasterConfig.get(DisasterConfig.TORNADO_BLOCKS)) { lift(l, e); orbit(l, e); }
            if (l.getGameTime() % 24 == e.id % 24) l.playSound(null, BlockPos.containing(e.x, e.y + 6, e.z), AlterraWeather.WIND_DUST.get(), net.minecraft.sounds.SoundSource.WEATHER, 3f + e.radius * 0.3f, e.type == TORNADO ? 0.55f : 0.8f);
        }
        Vortex.refresh(events);
        if (l.getGameTime() % 2 == 0) sync(l, events);
    }

    private static void move(ServerLevel l, Event e) {
        if (e.still) return;
        if (e.type == VORTEX) { e.x += Math.cos(e.heading) * 0.03; e.z += Math.sin(e.heading) * 0.03; return; }   // a whirlpool hardly drifts
        Vec3 w = Wind.at(l.getGameTime(), e.x, e.z, l.getRainLevel(1f), l.getThunderLevel(1f), HeavyStorm.level(l));
        e.heading += (l.random.nextFloat() - 0.5f) * 0.05f;
        double speed = e.type == TORNADO ? 0.20 : 0.10;   // blocks a tick: a tornado is faster than you walk, slower than you run
        e.vx += (w.x * speed + Math.cos(e.heading) * 0.04 - e.vx) * 0.05; e.vz += (w.z * speed + Math.sin(e.heading) * 0.04 - e.vz) * 0.05;
        e.x += e.vx; e.z += e.vz;
        if (e.age % 4 == 0) e.y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) e.x, (int) e.z);
        if (e.age % 20 == 0 && e.type == TORNADO) {   // it dies over the sea
            var b = l.getBiome(BlockPos.containing(e.x, e.y, e.z));
            if (b.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || l.getFluidState(BlockPos.containing(e.x, e.y - 1, e.z)).is(net.minecraft.tags.FluidTags.WATER)) e.life = Math.min(e.life, e.age + 80);
        }
    }

    /** Pull and spin everything free near it. */
    private static void pull(ServerLevel l, Event e) {
        double reach = e.radius * 3 + 14;
        AABB box = new AABB(e.x - reach, e.y - 6, e.z - reach, e.x + reach, e.y + (e.type == TORNADO ? 70 : e.top + 6), e.z + reach);
        for (Entity en : l.getEntities((Entity) null, box, x -> !x.isSpectator() && !(x instanceof Player p && p.getAbilities().flying) && !x.isPassenger())) {
            double dx = en.getX() - e.x, dz = en.getZ() - e.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < 0.05) continue;
            if (e.type == VORTEX && Math.abs(en.getY() - e.y) > 4) continue;   // only what is at the surface
            double f = 1 - d / reach; f = f * f * e.strength;
            double tx = -dz / d, tz = dx / d;   // round
            Vec3 m = en.getDeltaMovement(); Vec3 push;
            switch (e.type) {
                case TORNADO -> push = new Vec3(tx * 0.17 * f - dx / d * 0.08 * f, d < e.radius * 2.2 + 3 ? 0.15 * f : 0.025 * f, tz * 0.17 * f - dz / d * 0.08 * f);
                case DUST_DEVIL -> push = new Vec3(tx * 0.03 * f - dx / d * 0.01 * f, 0.02 * f, tz * 0.03 * f - dz / d * 0.01 * f);
                default -> push = new Vec3(tx * 0.09 * f - dx / d * 0.07 * f, d < e.radius * 0.5 ? -0.05 * f : 0, tz * 0.09 * f - dz / d * 0.07 * f);
            }
            Vec3 n = m.add(push);
            double sp = n.length(); if (sp > 1.3) n = n.scale(1.3 / sp);
            en.setDeltaMovement(n); en.hurtMarked = true;
            if (e.type == TORNADO && d < e.radius * 1.3 && en instanceof LivingEntity le && e.age % 10 == 0) le.hurt(l.damageSources().generic(), 2f * e.strength);
        }
    }

    /** Tear blocks off the ground and the trees: soft ones first (leaves, plants, snow, carpet, glass), and in a strong tornado also loose ground (grass, dirt, sand, gravel; one block at a time off the top).
     *  Each becomes debris that the tornado then steers (see orbit). Never wood, stone or anything hard, never things with contents, and only where mob griefing is on. */
    private static void lift(ServerLevel l, Event e) {
        if (!l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || e.strength < 0.3f || e.debris.size() >= 70) return;
        float cap = 0.3f + 1.4f * e.strength * e.strength;
        int tries = 1 + Math.round(2f * e.strength);
        for (int i = 0; i < tries; i++) {
            double a = l.random.nextDouble() * Math.PI * 2, r = l.random.nextDouble() * e.radius * 2.6;
            int bx = Mth(e.x + Math.cos(a) * r), bz = Mth(e.z + Math.sin(a) * r);
            if (!l.hasChunkAt(new BlockPos(bx, 64, bz))) continue;
            int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            for (int dy = 1; dy <= 14; dy++) {
                BlockPos p = new BlockPos(bx, top - dy, bz); BlockState st = l.getBlockState(p);
                if (st.isAir()) continue;
                float hard = st.getDestroySpeed(l, p);
                boolean ground = st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(net.minecraft.world.level.block.Blocks.GRAVEL);
                if (hard < 0f || hard > cap || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.is(BlockTags.LOGS) || ground && (e.strength < 0.7f || l.random.nextInt(4) != 0)) break;   // only the topmost thing in the column, if it is soft enough
                FallingBlockEntity fb = FallingBlockEntity.fall(l, p, st);
                fb.dropItem = false; fb.disableDrop();   // it is gone when it finally lands
                fb.setStartPos(SPIN_MARK);
                Debris d = new Debris(); d.fb = fb; d.life = 90 + l.random.nextInt(220); d.lift = 0.07f + l.random.nextFloat() * 0.07f; d.speed = 0.38f + l.random.nextFloat() * 0.3f;
                fb.setDeltaMovement(0, 0.35, 0);
                e.debris.add(d);
                break;
            }
        }
    }

    /** Steer the debris: round the funnel on a widening spiral (faster nearer the middle), up while the tornado holds it, then let go: it is flung outwards along the way it was going. */
    private static void orbit(ServerLevel l, Event e) {
        var it = e.debris.iterator();
        while (it.hasNext()) {
            Debris d = it.next(); var fb = d.fb;
            if (fb.isRemoved()) { it.remove(); continue; }
            d.age++;
            double dx = fb.getX() - e.x, dz = fb.getZ() - e.z, r = Math.max(0.6, Math.sqrt(dx * dx + dz * dz)), ux = dx / r, uz = dz / r, tx = -uz, tz = ux;
            Vec3 m = fb.getDeltaMovement();
            if (d.age >= d.life || e.strength < 0.3f) {   // let go: out along the tangent, and up a little
                fb.setDeltaMovement(m.x * 0.5 + tx * 0.6 + ux * 0.35, Math.max(0.12, m.y * 0.4), m.z * 0.5 + tz * 0.6 + uz * 0.35);
                it.remove(); continue;
            }
            double wantR = e.radius * (1.0 + 2.2 * Math.min(1.0, d.age / 140.0));   // the spiral opens out as it climbs
            double speed = d.speed * e.strength * (1.0 + 0.9 * Math.max(0.0, 1.0 - r / (e.radius * 3.0)));
            double radial = (wantR - r) * 0.07;   // pulled towards its orbit radius
            double climb = fb.getY() < e.y + e.top * 0.45 ? d.lift : -0.01;
            double gx = tx * speed + ux * radial, gz = tz * speed + uz * radial, gy = climb + 0.04;   // (+0.04 cancels the fall of the falling block this tick)
            fb.setDeltaMovement(m.x * 0.55 + gx * 0.45, m.y * 0.6 + gy * 0.4, m.z * 0.55 + gz * 0.45);
            fb.hurtMarked = true;
            fb.setStartPos(SPIN_MARK);
        }
    }
    private static int Mth(double v) { return (int) Math.floor(v); }

    private static void sync(ServerLevel l, List<Event> events) {
        for (ServerPlayer pl : l.players()) {
            if (!pl.connection.hasChannel(DisasterSync.TYPE)) continue;
            List<Float> v = new ArrayList<>();
            for (Event e : events) {
                if (v.size() >= 56) break;
                if (Math.abs(pl.getX() - e.x) > 600 || Math.abs(pl.getZ() - e.z) > 600) continue;
                v.add((float) e.id); v.add((float) e.type); v.add((float) e.x); v.add((float) e.y); v.add((float) e.z); v.add(e.radius); v.add(e.type == VORTEX ? e.spin * 14f : e.top); v.add(e.strength);   // (a whirlpool has no height: the sign says which way it turns)
            }
            if (!v.isEmpty()) PacketDistributor.sendToPlayer(pl, new DisasterSync(v));
        }
    }

    // ---------------------------------------------------------------- /alterradisaster
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("alterradisaster").requires(s -> s.hasPermission(2))
                .then(Commands.argument("what", StringArgumentType.word()).suggests((c, b) -> { for (String n : NAMES) b.suggest(n); b.suggest("hail"); b.suggest("clear"); return b.buildFuture(); })
                        .then(Commands.literal("still").executes(c -> run(c, true)))
                        .executes(c -> run(c, false))));
    }

    /** /alterradisaster <tornado|dustdevil|vortex|hail|clear> [still]: start one a little way ahead (still: it stays where it starts). */
    private static int run(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, boolean still) {
        String what = StringArgumentType.getString(c, "what").toLowerCase(java.util.Locale.ROOT);
        var src = c.getSource(); ServerLevel l = src.getLevel();
        if (!available()) { src.sendFailure(Component.literal("Disasters need Alterra Illuminate (it draws them).")); return 0; }
        if (what.equals("clear")) { list(l).clear(); Hail.forcedUntil = 0; src.sendSuccess(() -> Component.literal("Disasters cleared."), true); return 1; }
        if (what.equals("hail")) { Hail.forcedUntil = l.getGameTime() + 1200; src.sendSuccess(() -> Component.literal("Hail for a minute."), true); return 1; }
        for (int t = 0; t < NAMES.length; t++) if (NAMES[t].equals(what)) {
            Vec3 look = src.getEntity() != null ? src.getEntity().getLookAngle() : new Vec3(0, 0, 1);
            double d = t == DUST_DEVIL ? 18 : t == VORTEX ? 30 : 38, x = src.getPosition().x + look.x * d, z = src.getPosition().z + look.z * d;
            Event ev = start(l, t, x, z); ev.still = still;
            src.sendSuccess(() -> Component.literal("A " + what + " about " + (int) d + " blocks ahead."), true);
            return 1;
        }
        src.sendFailure(Component.literal("Try: tornado, dustdevil, vortex, hail, clear"));
        return 0;
    }
}
