package dev.phi.alterra.music;

import dev.phi.alterra.api.AlterraApi;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Alterra Music. Which kind of track a biome gets is set by the biome tags alterra_music:music/*. */
@Mod(AlterraMusic.ID)
public final class AlterraMusic {
    public static final String ID = "alterra_music";
    public static final int TRACKS = 2;   // per kind
    public static final List<String> KINDS = List.of("cold", "temperate", "warm", "dry", "seas", "caves", "sky");
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    /** kind -> its tracks */
    public static final Map<String, List<Supplier<SoundEvent>>> MUSIC = new java.util.LinkedHashMap<>();
    static {
        for (String kind : KINDS) {
            List<Supplier<SoundEvent>> l = new ArrayList<>();
            for (int i = 1; i <= TRACKS; i++) {
                String name = "music." + kind + "_" + i;
                l.add(SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ID, name))));
            }
            MUSIC.put(kind, l);
        }
    }

    public AlterraMusic(IEventBus bus, ModContainer container) {
        SOUNDS.register(bus);
        container.registerConfig(ModConfig.Type.CLIENT, MusicConfig.SPEC);
        AlterraApi.addSettingsCategory("Music", "Alterra's music.", () -> new ItemStack(Items.JUKEBOX), List.of(
                AlterraApi.Option.bool("Alterra music", "Use Alterra's tracks in its biomes instead of the vanilla ones.", MusicConfig.ENABLED),
                AlterraApi.Option.bool("Sky music", "A track for the sky islands.", MusicConfig.SKY),
                AlterraApi.Option.integer("Shortest quiet", "Shortest pause between tracks, in minutes.", MusicConfig.MIN_MINUTES, 0, 30, ""),
                AlterraApi.Option.integer("Longest quiet", "Longest pause, in minutes.", MusicConfig.MAX_MINUTES, 1, 60, "")));
        if (FMLEnvironment.dist == Dist.CLIENT) MusicClient.init();
    }
}
