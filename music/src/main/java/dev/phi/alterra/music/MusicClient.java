package dev.phi.alterra.music;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Client: swaps the game's situational music for Alterra's track when the player is in the right place. */
final class MusicClient {
    private MusicClient() {}
    private static String lastKind = "", lastTrack = "";
    private static Music chosen;

    static void init() { NeoForge.EVENT_BUS.addListener(MusicClient::select); }

    private static TagKey<Biome> tag(String kind) { return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(AlterraMusic.ID, "music/" + kind)); }

    private static void select(SelectMusicEvent e) {
        if (!MusicConfig.get(MusicConfig.ENABLED)) return;
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level; var player = mc.player;
        if (level == null || player == null || level.dimension() != Level.OVERWORLD) { chosen = null; return; }
        // only where the game would play ordinary background music (not a jukebox / boss / menu / credits)
        Music original = e.getOriginalMusic();
        if (original == null) return;
        String kind = null;
        if (MusicConfig.get(MusicConfig.SKY) && player.getY() > 600) kind = "sky";
        else {
            var biome = level.getBiome(player.blockPosition());
            for (String k : AlterraMusic.KINDS) if (!k.equals("sky") && biome.is(tag(k))) { kind = k; break; }
        }
        if (kind == null) { chosen = null; return; }
        // keep the same track while one is playing; pick another (not the last one) when the game asks for the next
        if (chosen == null || !kind.equals(lastKind) || e.getPlayingMusic() == null) {
            List<Supplier<SoundEvent>> tracks = AlterraMusic.MUSIC.get(kind);
            int i = level.random.nextInt(tracks.size());
            for (int tries = 0; tries < 4 && tracks.get(i).get().getLocation().toString().equals(lastTrack) && tracks.size() > 1; tries++) i = level.random.nextInt(tracks.size());
            SoundEvent ev = tracks.get(i).get();
            int min = MusicConfig.get(MusicConfig.MIN_MINUTES) * 1200, max = Math.max(min + 1200, MusicConfig.get(MusicConfig.MAX_MINUTES) * 1200);
            chosen = new Music(Holder.direct(ev), min, max, true);
            lastTrack = ev.getLocation().toString(); lastKind = kind;
        }
        e.overrideMusic(chosen);
    }
}
