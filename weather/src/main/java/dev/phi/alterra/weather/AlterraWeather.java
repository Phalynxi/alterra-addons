package dev.phi.alterra.weather;

import dev.phi.alterra.weather.config.DisasterConfig;
import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.alterra.weather.config.WeatherConfig;
import dev.phi.alterra.weather.net.BlastSync;
import dev.phi.alterra.weather.net.DisasterSync;
import dev.phi.alterra.weather.net.SeaSync;
import dev.phi.alterra.weather.net.WaterSync;
import dev.phi.alterra.weather.net.WetSync;
import dev.phi.alterra.weather.storm.Blast;
import dev.phi.alterra.weather.storm.ForcedStorm;
import dev.phi.alterra.weather.storm.HeavyStorm;
import dev.phi.alterra.weather.storm.WetSubscribe;
import dev.phi.alterra.weather.storm.Wetness;
import dev.phi.alterra.weather.water.SeaSurge;
import dev.phi.alterra.weather.water.WaterBlocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/** Alterra Weather. Which biomes get which storm is set by the biome tags alterra_weather:weather/*. */
@Mod(AlterraWeather.ID)
public final class AlterraWeather {
    public static final String ID = "alterra_weather";
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ID);
    private static ParticleType<ColorParticleOption> colored() {
        return new ParticleType<>(false) {
            @Override public MapCodec<ColorParticleOption> codec() { return ColorParticleOption.codec(this); }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() { return ColorParticleOption.streamCodec(this); }
        };
    }
    /** All three are tinted by the particle's colour: DUST = grit and driven snow, PUFF = the big soft clouds of a storm, MOTE = slow pollen and ash. */
    public static final Supplier<ParticleType<ColorParticleOption>> DUST = PARTICLES.register("dust", AlterraWeather::colored),
            PUFF = PARTICLES.register("puff", AlterraWeather::colored), MOTE = PARTICLES.register("mote", AlterraWeather::colored),
            PRINT = PARTICLES.register("print", AlterraWeather::colored),
            DRIFT = PARTICLES.register("drift", AlterraWeather::colored);   // a low veil of sand or snow skimming the ground in a storm
    public static final Supplier<net.minecraft.core.particles.SimpleParticleType> RIPPLE = PARTICLES.register("ripple", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            FOAM = PARTICLES.register("foam", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            SPRAY = PARTICLES.register("spray", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            CLUMP = PARTICLES.register("clump", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            HAIL = PARTICLES.register("hail", () -> new net.minecraft.core.particles.SimpleParticleType(false)),
            DROP = PARTICLES.register("drop", () -> new net.minecraft.core.particles.SimpleParticleType(false));
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final Supplier<SoundEvent> WIND_DUST = sound("wind_dust"), WIND_SNOW = sound("wind_snow"), ASH_RUMBLE = sound("ash_rumble"), FOG_HUSH = sound("fog_hush"),
            PETAL_RUSTLE = sound("petal_rustle"), POLLEN_HUM = sound("pollen_hum"), AURORA_CHIME = sound("aurora_chime"),
            RAIN_LIGHT = sound("rain_light"), RAIN_HEAVY = sound("rain_heavy");
    private static Supplier<SoundEvent> sound(String name) { return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, name))); }

    public AlterraWeather(IEventBus bus, ModContainer container) {
        dev.phi.prismlib.service.Services.register(dev.phi.prismlib.api.DisturbanceListener.class, new dev.phi.alterra.weather.snow.SnowDisturbance(), 100, ID);   // snow answers every disturbance, from whatever mod
        PARTICLES.register(bus); SOUNDS.register(bus); WaterBlocks.register(bus);
        bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) -> e.registrar("1").optional()
                .playToClient(ForcedStorm.TYPE, ForcedStorm.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> ForcedStorm.apply(payload)))
                .playToServer(WetSubscribe.TYPE, WetSubscribe.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Wetness.subscribe((net.minecraft.server.level.ServerPlayer) ctx.player(), p.on())))
                .playToClient(WetSync.TYPE, WetSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Wetness.received(p.wet())))
                .playToClient(BlastSync.TYPE, BlastSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Blast.received(p)))
                .playToClient(SeaSync.TYPE, SeaSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> SeaSurge.received(p)))
                .playToClient(WaterSync.TYPE, WaterSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dev.phi.prismlib.api.Look.waterAmounts.accept(new dev.phi.prismlib.api.WaterAmounts(p.pos(), p.amount()))))
                .playToClient(DisasterSync.TYPE, DisasterSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> WeatherHooks.disasters.accept(p))));
        WeatherProviders.register();
        container.registerConfig(ModConfig.Type.CLIENT, WeatherConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, HeavyConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, DisasterConfig.SPEC, "alterra_weather-disasters.toml");
        dev.phi.alterra.weather.compat.LibrarySettings.register();   // the options, as pages of Prism Lib's settings screen
        if (FMLEnvironment.dist == Dist.CLIENT) dev.phi.prismlib.settings.SettingsClient.install(container, ID, "Alterra Weather");
        if (ModList.get().isLoaded("alterra")) dev.phi.alterra.weather.compat.AlterraLink.register();   // the wind for Alterra addons, and the settings pages
        if (FMLEnvironment.dist == Dist.CLIENT) WeatherClient.init(bus);
    }
}
