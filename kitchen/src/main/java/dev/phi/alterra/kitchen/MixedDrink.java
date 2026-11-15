package dev.phi.alterra.kitchen;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A drink made in the Drink Mixer from three or four drinks: which drinks went in (their ids, sorted). Everything else follows from that and is the same every time:
 * its glass and garnish (ember gets a flame, frost a crystal, sun and golden drinks a sparkle), its two colours (the drinks blended, the first one strongest; the last one tints the bottom)
 * and its name. So the same mix is always the same drink, and there are thousands of them.
 */
public record MixedDrink(List<String> drinks, List<String> herbs) {
    public static final Codec<MixedDrink> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("drinks").forGetter(MixedDrink::drinks),
            Codec.STRING.listOf().optionalFieldOf("herbs", List.of()).forGetter(MixedDrink::herbs)).apply(i, MixedDrink::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MixedDrink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MixedDrink::drinks, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MixedDrink::herbs, MixedDrink::new);

    public static MixedDrink of(List<String> drinks) { return of(drinks, List.of()); }
    public static MixedDrink of(List<String> drinks, List<String> herbs) {
        List<String> l = new ArrayList<>(drinks); java.util.Collections.sort(l);
        List<String> h = new ArrayList<>(new java.util.LinkedHashSet<>(herbs)); java.util.Collections.sort(h);
        return new MixedDrink(List.copyOf(l), List.copyOf(h.size() > 3 ? h.subList(0, 3) : h));
    }

    /** Herbs that can go in a drink: what they tint it, and what they do (a poison does what it always does, see Poisons). Nothing on the drink says which are poison. */
    public record Herb(int color, String effect, int ticks, String poison) {}
    public static final Map<String, Herb> HERBS = Map.ofEntries(
            Map.entry("alterra_kitchen:poison_fern_fronds", new Herb(0x6FA040, null, 0, "poison_fern_fronds")),
            Map.entry("alterra_kitchen:nightshade_berries", new Herb(0x3A4AA0, null, 0, "nightshade_berries")),
            Map.entry("alterra_kitchen:hemlock_leaves", new Herb(0x5AA048, null, 0, "hemlock_leaves")),
            Map.entry("alterra_kitchen:oleander_leaves", new Herb(0xD86AA0, null, 0, "oleander_leaves")),
            Map.entry("alterra_kitchen:deathcap", new Herb(0xD8C8A0, null, 0, "deathcap")),
            Map.entry("minecraft:sweet_berries", new Herb(0xC8303C, "minecraft:regeneration", 100, null)),
            Map.entry("minecraft:glow_berries", new Herb(0xF0B040, "minecraft:night_vision", 1200, null)),
            Map.entry("minecraft:kelp", new Herb(0x4A8A3A, "minecraft:water_breathing", 900, null)),
            Map.entry("minecraft:dried_kelp", new Herb(0x3A6030, "minecraft:water_breathing", 900, null)),
            Map.entry("minecraft:cocoa_beans", new Herb(0x7A4A2A, "minecraft:haste", 600, null)),
            Map.entry("minecraft:fern", new Herb(0x5A9A40, null, 0, null)),
            Map.entry("minecraft:blaze_powder", new Herb(0xF09030, "minecraft:fire_resistance", 1200, null)),
            Map.entry("minecraft:sugar", new Herb(0xFFFFFF, "minecraft:speed", 300, null)));
    public static boolean isHerb(net.minecraft.world.item.ItemStack st) { return HERBS.containsKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString()); }
    public static String herbId(net.minecraft.world.item.ItemStack st) { return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(); }

    /** What the herbs in it do when it is drunk. */
    public void herbEffects(net.minecraft.world.entity.LivingEntity who) {
        for (String id : herbs) {
            Herb h = HERBS.get(id); if (h == null) continue;
            if (h.poison() != null) Poisons.apply(h.poison(), who, 0);
            else if (h.effect() != null) net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceLocation.parse(h.effect())).ifPresent(e -> who.addEffect(new net.minecraft.world.effect.MobEffectInstance(e, h.ticks(), 0)));
        }
    }

    private static int glasses = 19, garnishes = 22, flame = 19, crystal = 20, sparkle = 21, plain = 19;
    static {   // the glasses and garnishes there are art for (tools/mixed_drink_art.py writes this list)
        try (var in = MixedDrink.class.getResourceAsStream("/alterra_kitchen_mixlook.json")) {
            if (in != null) {
                @SuppressWarnings("unchecked") Map<String, Object> m = new Gson().fromJson(new InputStreamReader(in), Map.class);
                glasses = ((List<?>) m.get("glasses")).size(); garnishes = ((List<?>) m.get("garnishes")).size();
                @SuppressWarnings("unchecked") Map<String, Number> sp = (Map<String, Number>) m.get("special");
                flame = sp.get("flame").intValue(); crystal = sp.get("crystal").intValue(); sparkle = sp.get("sparkle").intValue();
                plain = Math.min(flame, Math.min(crystal, sparkle));
            }
        } catch (Exception ignored) { }
    }

    private int hash() { int h = (String.join("+", drinks) + "|" + String.join("+", herbs)).hashCode(); h ^= h >>> 16; h *= 0x45d9f3b; h ^= h >>> 16; return h; }

    /** Which picture: glass x garnishes + garnish, for the item model's alterra_kitchen:look property (index / 1024). */
    public int look() {
        int h = hash(), glass = Math.floorMod(h, glasses), garnish;
        if (drinks.stream().anyMatch(d -> d.contains("ember"))) garnish = flame;
        else if (drinks.stream().anyMatch(d -> d.contains("frost"))) garnish = crystal;
        else if (drinks.stream().anyMatch(d -> d.contains("sun") || d.contains("gilded") || d.contains("golden"))) garnish = sparkle;
        else garnish = Math.floorMod(h >>> 8, plain);
        return glass * garnishes + garnish;
    }

    private static int colour(String drink) { for (var j : AlterraKitchen.JUICES) if (j.id().equals(drink)) return j.color(); return 0xE0A060; }
    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t), g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t), bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
    /** The top colour: all the drinks blended, leaning towards the one the mix starts with (by its own order, which the hash picks). */
    public int top() {
        int r = 0, g = 0, b = 0;
        for (String d : drinks) { int c = colour(d); r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; }
        int avg = ((r / drinks.size()) << 16) | ((g / drinks.size()) << 8) | (b / drinks.size());
        int top = mix(colour(drinks.get(Math.floorMod(hash() >>> 4, drinks.size()))), avg, 0.6f);
        for (String id : herbs) { Herb h = HERBS.get(id); if (h != null) top = mix(top, h.color(), 0.22f); }   // herbs tint it
        return top;
    }
    /** The bottom colour: another of the drinks showing through underneath. */
    public int bottom() { return mix(colour(drinks.get(Math.floorMod(hash() >>> 12, drinks.size()))), top(), 0.3f); }

    private static final String[] STYLES = {"Cooler", "Punch", "Fizz", "Splash", "Cup", "Sling", "Spritz", "Smash", "Twist", "Breeze", "Tonic", "Sunset", "Medley", "Crush"};
    private static String word(String drink) {
        String n = drink.replace("_juice", "").replace("_nectar", "").replace("_water", "").replace("_drink", "").replace("_smoothie", "");
        StringBuilder sb = new StringBuilder();
        for (String w : n.split("_")) if (!w.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }
    /** "Mango, Cherry and Fig Punch". */
    public Component name() {
        List<String> w = drinks.stream().map(MixedDrink::word).toList();
        String list = w.size() > 3 ? w.get(0) + ", " + w.get(1) + " and " + (w.size() - 2) + " More"   // a long mix keeps a short name
                : w.size() == 1 ? w.get(0) : String.join(", ", w.subList(0, w.size() - 1)) + " and " + w.get(w.size() - 1);
        return Component.literal(list + " " + STYLES[Math.floorMod(hash() >>> 20, STYLES.length)]);
    }
}
