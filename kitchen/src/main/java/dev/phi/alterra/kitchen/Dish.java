package dev.phi.alterra.kitchen;

import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What a made-up dish is: the fillings that went in (in grid order, which is the stacking order of a burger) and the seasoning on it, if any. */
public record Dish(List<ResourceLocation> fillings, ResourceLocation seasoning, ResourceLocation sauce) {
    public static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("air");
    public static final Codec<Dish> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("fillings").forGetter(Dish::fillings),
            ResourceLocation.CODEC.optionalFieldOf("seasoning", NONE).forGetter(Dish::seasoning),
            ResourceLocation.CODEC.optionalFieldOf("sauce", NONE).forGetter(Dish::sauce)).apply(i, Dish::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Dish> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Dish::fillings, ResourceLocation.STREAM_CODEC, Dish::seasoning, ResourceLocation.STREAM_CODEC, Dish::sauce, Dish::new);

    // ------------------------------------------------------------ colours
    private static Map<String, Integer> colors;
    /** Brightens a colour (brightness at least 0.85, saturation at least 0.4) so the grey layers multiplied with it do not turn muddy. */
    public static int vivid(int c) {
        float[] hsb = java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
        return java.awt.Color.HSBtoRGB(hsb[0], Math.max(hsb[1], 0.4f), Math.max(hsb[2], 0.88f)) & 0xFFFFFF;
    }
    public static int fillingColor(ResourceLocation id) { return vivid(colorOf(id)); }
    private static int colorOf(ResourceLocation id) {
        if (colors == null) {
            Map<String, Double> raw = AlterraKitchen.read("alterra_kitchen_colors.json", new TypeToken<Map<String, Double>>() {});
            colors = new HashMap<>(); raw.forEach((k, v) -> colors.put(k, v.intValue()));
        }
        Integer c = colors.get(id.toString());
        if (c != null) return c;
        int h = id.toString().hashCode();   // an item we do not know: a colour from its name, never too dark or too bright
        return ((110 + (h & 0x7f)) << 16) | ((90 + ((h >> 8) & 0x7f)) << 8) | (80 + ((h >> 16) & 0x7f));
    }
    /** The average colour of the fillings: the sauce and chunks are tinted with it. */
    public int sauceColor() {
        if (fillings.isEmpty()) return 0xC8A060;
        int r = 0, g = 0, b = 0;
        for (ResourceLocation f : fillings) { int c = colorOf(f); r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; }
        int n = fillings.size();
        if (!sauce.equals(NONE)) { int c = colorOf(sauce); r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; n++; }
        return vivid((r / n << 16) | (g / n << 8) | b / n);
    }
    /** The flecks: the seasoning's colour (a pale version of the sauce when there is none). */
    public int seasoningColor() {
        return switch (seasoning.getPath()) {
            case "salt" -> 0xF2F2EE; case "smoked_salt" -> 0xB08C6E; case "ember_flakes" -> 0xD8402A; case "frost_crystals" -> 0xB4E6F8;
            case "sun_zest" -> 0xF8D048; case "herb_salt" -> 0x7CB464; case "sugar" -> 0xFFFFFF; case "honey_bottle" -> 0xE8A028;
            case "kelp_salt" -> 0x6CA082; case "cocoa_sugar" -> 0x7A4E34; case "berry_dust" -> 0xAA3C6E; case "autumn_spice" -> 0xBE6E32; case "tart_zest" -> 0xA0D450; case "onion_salt" -> 0xDEC4A0; case "glow_spice" -> 0xFAD264;
            default -> { if (!seasoning.equals(NONE)) yield colorOf(seasoning);   // a dust of a fruit has that fruit's colour
                int c = sauceColor(); yield (Math.min(255, (c >> 16 & 255) + 70) << 16) | (Math.min(255, (c >> 8 & 255) + 70) << 8) | Math.min(255, (c & 255) + 70); }
        };
    }

    // ------------------------------------------------------------ the burger's bands: what each filling looks like
    /** 0 patty (meat), 1 round slices (fruit), 2 shreds (vegetables), 3 sauce (everything else). */
    public static int shape(ResourceLocation id) {
        String p = id.getPath();
        for (String m : new String[] {"beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon", "ribs", "kangaroo", "lobster", "catfish", "fish", "steak", "meat", "bacon", "mince", "patty", "sausage", "ham"}) if (p.contains(m)) return 0;
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id).builtInRegistryHolder().is(net.minecraft.tags.ItemTags.create(ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "fruits")))) return 1;
        for (String v : new String[] {"carrot", "potato", "beetroot", "kelp", "cabbage", "tomato", "onion", "lettuce", "mushroom", "pumpkin", "leaf"}) if (p.contains(v)) return 2;
        return 3;
    }
    /** The first filling of a given shape (see {@link #shape}), if there is one: it colours that part of a burger. */
    public java.util.Optional<ResourceLocation> firstOfShape(int shape) { return fillings.stream().filter(f -> shape(f) == shape).findFirst(); }

    /** Meat keeps its brown (brightened only a little); the rest is made bright so the grey shapes do not turn muddy. */
    public static int bandColor(ResourceLocation id) {
        int c = colorOf(id);
        if (shape(id) != 0) return vivid(c);
        float[] hsb = java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
        return java.awt.Color.HSBtoRGB(hsb[0], Math.max(hsb[1], 0.5f), Math.max(hsb[2], 0.72f)) & 0xFFFFFF;
    }

    // ------------------------------------------------------------ what each part of a dish's picture is made of
    /** The look of an ingredient's texture: fibres for red meat, flakes for fish, smooth for poultry and eggs, segments for fruit, speckles for vegetables, smooth for the rest. */
    public static String pattern(ResourceLocation id) {
        String p = id.getPath();
        for (String m : new String[] {"beef", "porkchop", "mutton", "ribs", "steak", "bacon", "ham", "kangaroo", "moose", "bison", "venison", "mince", "patty"}) if (p.contains(m)) return "fibers";
        for (String m : new String[] {"cod", "salmon", "fish", "catfish", "lobster", "shrimp", "squid", "crab", "tuna"}) if (p.contains(m)) return "flakes";
        for (String m : new String[] {"chicken", "rabbit", "duck", "egg", "turkey"}) if (p.contains(m)) return "smooth";
        int s = shape(id);
        return s == 1 ? "segments" : s == 2 ? "speckle" : "smooth";
    }
    /**
     * Which ingredients colour each zone of a dish's picture (an empty list: the zone keeps the colour of the picture it was cut from).
     * A burger or wrap: a vegetable, a fruit and the meat go to their own parts, and the sauce (or anything else) to the slice. A pie or flatbread: all the fillings and the sauce together. Everything else: the fillings in order, repeating.
     */
    public List<List<ResourceLocation>> zoneSources(String kind, int zones) {
        List<List<ResourceLocation>> out = new java.util.ArrayList<>();
        java.util.function.IntFunction<List<ResourceLocation>> shape = s -> firstOfShape(s).map(List::of).orElse(List.of());
        for (int i = 0; i < zones; i++) {
            List<ResourceLocation> z;
            switch (kind) {
                case "dish_burger" -> z = i == 0 ? shape.apply(2) : i == 1 ? shape.apply(1) : i == 2 ? shape.apply(0) : (!sauce.equals(NONE) ? List.of(sauce) : shape.apply(3));
                case "dish_wrap" -> z = i == 0 ? shape.apply(2) : i == 1 ? shape.apply(0) : (!sauce.equals(NONE) ? List.of(sauce) : shape.apply(3));
                case "dish_pie", "dish_flatbread" -> { z = new java.util.ArrayList<>(fillings); if (!sauce.equals(NONE)) z.add(sauce); }
                default -> z = fillings.isEmpty() ? List.of() : List.of(fillings.get(i % fillings.size()));
            }
            out.add(z);
        }
        return out;
    }

    // ------------------------------------------------------------ raw food
    private static final java.util.Set<String> UNCOOKED = java.util.Set.of("beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon", "tropical_fish", "potato", "egg",
            "minced_beef", "chicken_cuts", "mutton_chops", "bacon", "ham", "cod_slice", "salmon_slice", "beef_patty", "raw_pasta", "rice", "wheat_dough", "dough", "moose_ribs", "kangaroo_meat", "lobster_tail", "raw_catfish", "flying_fish", "blobfish", "emu_egg");
    /** Food that has to be cooked first: it cannot go into a dish. (Also anything in the tag alterra_kitchen:uncooked.) */
    public static boolean isUncooked(ResourceLocation id) { return UNCOOKED.contains(id.getPath()) || id.getPath().startsWith("raw_"); }

    // ------------------------------------------------------------ poison
    /** How many different poisonous things went into the dish (rotten flesh, spider eye, poisonous potato, pufferfish, a rotten tomato, nightshade, hemlock, oleander, a deathcap, poison fern). They are allowed on purpose: a dish made with them is a poisoned dish. */
    public int toxicCount() { return (int) fillings.stream().distinct().filter(f -> Poisons.isToxic(f.getPath())).count(); }
    /** What eating the dish does: a very bad dish gives food poisoning; each poison in it does its own thing (see Poisons), stronger the more of them there are. */
    public void harm(net.minecraft.world.entity.LivingEntity who) {
        int toxic = toxicCount();
        if (toxic == 0) {
            if (taste() < 20) who.addEffect(new net.minecraft.world.effect.MobEffectInstance(KitchenEffects.FOOD_POISONING, 400, 0));
            return;
        }
        for (ResourceLocation f : fillings.stream().distinct().toList()) Poisons.apply(f.getPath(), who, Math.min(2, toxic - 1));
    }

    // ------------------------------------------------------------ taste
    private static final java.util.Set<String> SAVORY = java.util.Set.of("salt", "smoked_salt", "herb_salt", "kelp_salt", "onion_salt", "ember_flakes", "glow_spice");
    /** How good a dish tastes, 0 to 100: variety and a good match of fillings, seasoning and sauce raise it; repeats and clashes (fish with fruit, sugar on meat) lower it. */
    public int taste() {
        int s = 38 - 30 * toxicCount();
        List<ResourceLocation> distinct = fillings.stream().distinct().toList();
        s += 9 * Math.min(distinct.size(), 5);
        s -= 7 * (fillings.size() - distinct.size());
        int meat = 0, fruit = 0, veg = 0; boolean fish = false;
        for (ResourceLocation f : distinct) { switch (shape(f)) { case 0 -> meat++; case 1 -> fruit++; case 2 -> veg++; default -> { } } if (pattern(f).equals("flakes")) fish = true; }
        if (meat > 0 && veg > 0) s += 8;
        if (meat > 0 && fruit > 0) s += 3;
        if (fruit > 0 && veg > 0) s += 3;
        if (fish && fruit > 0) s -= 10;
        String sp = seasoning.getPath();
        if (!seasoning.equals(NONE)) {
            boolean savory = SAVORY.contains(sp), sweet = !savory;
            s += 8;
            if (savory && (meat > 0 || veg > 0)) s += 8;
            if (sweet && fruit > 0) s += 8;
            if (savory && fruit > 0 && meat == 0 && veg == 0) s -= 6;
            if (sweet && meat > 0 && fruit == 0) s -= 8;
        }
        if (!sauce.equals(NONE)) { s += 7; if (sauce.getPath().equals("tomato_sauce") && (meat > 0 || veg > 0)) s += 4; }
        return Math.max(2, Math.min(130, s));   // above 100 is a secret: 120 and more is Divine
    }
    /** "Taste: ★★★☆☆ Tasty" in a colour that goes with the word; Divine (120 and more) has every letter in its own, moving, colour. */
    public static net.minecraft.network.chat.Component tasteLine(int score) {
        int stars = Math.max(1, Math.min(5, Math.round(Math.min(score, 100) / 20f)));
        if (score >= 120) {
            String text = "Taste: " + "★".repeat(5) + " Divine";
            var out = net.minecraft.network.chat.Component.empty(); long ms = net.minecraft.Util.getMillis();
            for (int i = 0; i < text.length(); i++) out.append(net.minecraft.network.chat.Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(java.awt.Color.HSBtoRGB(((ms / 12 + i * 14) % 360) / 360f, 0.55f, 1f) & 0xFFFFFF)));
            return out;
        }
        String word; net.minecraft.ChatFormatting colour;
        if (score < 20) { word = "Awful"; colour = net.minecraft.ChatFormatting.DARK_RED; }
        else if (score < 40) { word = "Bland"; colour = net.minecraft.ChatFormatting.GRAY; }
        else if (score < 60) { word = "Fine"; colour = net.minecraft.ChatFormatting.WHITE; }
        else if (score < 80) { word = "Tasty"; colour = net.minecraft.ChatFormatting.YELLOW; }
        else if (score < 95) { word = "Delicious"; colour = net.minecraft.ChatFormatting.GOLD; }
        else { word = "Perfect"; colour = net.minecraft.ChatFormatting.LIGHT_PURPLE; }
        return net.minecraft.network.chat.Component.literal("Taste: " + "★".repeat(stars) + "☆".repeat(5 - stars) + " " + word).withStyle(colour);
    }

    // ------------------------------------------------------------ flavour and the other numbers
    /** The flavour profile, each 0 to 5, in the order sweet, savory, spicy, fresh, rich: worked out from the fillings, the seasoning and the sauce. */
    public int[] flavors() {
        int[] f = new int[5];   // 0 sweet, 1 savory, 2 spicy, 3 fresh, 4 rich
        for (ResourceLocation id : fillings.stream().distinct().toList()) {
            switch (shape(id)) { case 0 -> { f[1] += 2; f[4] += 1; } case 1 -> { f[0] += 2; f[3] += 1; } case 2 -> { f[1] += 1; f[3] += 1; } default -> f[4] += 1; }
        }
        String sp = seasoning.getPath();
        switch (sp) {
            case "salt", "smoked_salt", "onion_salt" -> f[1] += 2;
            case "herb_salt" -> { f[1] += 2; f[3] += 1; }
            case "kelp_salt" -> { f[1] += 1; f[3] += 1; }
            case "ember_flakes" -> f[2] += 4;
            case "glow_spice" -> { f[2] += 1; f[0] += 1; }
            case "autumn_spice" -> { f[2] += 2; f[0] += 1; }
            case "frost_crystals" -> f[3] += 4;
            case "tart_zest" -> { f[3] += 3; f[0] += 1; }
            case "sun_zest" -> { f[0] += 1; f[3] += 2; }
            case "cocoa_sugar" -> { f[0] += 2; f[4] += 1; }
            case "sugar", "berry_dust" -> f[0] += 2;
            default -> { if (sp.endsWith("_dust")) f[0] += 2; }
        }
        switch (sauce.getPath()) {
            case "none", "air" -> { }
            case "honey_bottle" -> { f[0] += 2; f[4] += 1; }
            case "milk_bucket", "milk_bottle" -> f[4] += 2;
            case "tomato_sauce" -> { f[1] += 1; f[3] += 1; }
            default -> f[0] += 2;
        }
        for (int i = 0; i < 5; i++) f[i] = Math.min(5, f[i]);
        return f;
    }
    private static final String[] FLAVOR_NAMES = {"Sweet", "Savory", "Spicy", "Fresh", "Rich"};
    private static final net.minecraft.ChatFormatting[] FLAVOR_COLOURS = {net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.YELLOW};
    /** "Savory 4, Rich 2" (the two strongest flavours, each in its own colour). */
    public net.minecraft.network.chat.Component flavorLine() {
        int[] f = flavors();
        Integer[] order = {0, 1, 2, 3, 4};
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(f[b], f[a]));
        var line = net.minecraft.network.chat.Component.empty();
        int shown = 0;
        for (int i : order) {
            if (f[i] <= 0 || shown >= 2) break;
            if (shown++ > 0) line.append(net.minecraft.network.chat.Component.literal(", ").withStyle(net.minecraft.ChatFormatting.GRAY));
            line.append(net.minecraft.network.chat.Component.literal(FLAVOR_NAMES[i] + " " + f[i]).withStyle(FLAVOR_COLOURS[i]));
        }
        if (shown == 0) line.append(net.minecraft.network.chat.Component.literal("Plain flavor").withStyle(net.minecraft.ChatFormatting.GRAY));
        return line;
    }
    /** "Hunger 9 · Sat 7.4" from the food on the stack (short, so it is not wider than the taste line). */
    public static void foodLine(ItemStack st, List<net.minecraft.network.chat.Component> tip) {
        FoodProperties fp = st.get(DataComponents.FOOD);
        if (fp == null) return;
        tip.add(net.minecraft.network.chat.Component.literal("Hunger " + fp.nutrition() + " · Sat " + String.format(java.util.Locale.ROOT, "%.1f", fp.saturation())).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------ the name
    private static String titleCase(String path) {
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) { if (w.isEmpty()) continue; if (sb.length() > 0) sb.append(' '); sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)); }
        return sb.toString();
    }
    public static String adjective(ResourceLocation seasoning) {
        return switch (seasoning.getPath()) {
            case "salt" -> "Salted"; case "smoked_salt" -> "Smoked"; case "ember_flakes" -> "Fiery"; case "frost_crystals" -> "Frosty";
            case "sun_zest" -> "Zesty"; case "herb_salt" -> "Herbed"; case "sugar" -> "Sweet"; case "honey_bottle" -> "Honeyed";
            case "kelp_salt" -> "Briny"; case "cocoa_sugar" -> "Chocolatey"; case "berry_dust" -> "Berried"; case "autumn_spice" -> "Spiced"; case "tart_zest" -> "Tangy"; case "onion_salt" -> "Savory"; case "glow_spice" -> "Radiant";
            default -> seasoning.getPath().endsWith("_dust") && !seasoning.getPath().equals("berry_dust") ? titleCase(seasoning.getPath().substring(0, seasoning.getPath().length() - 5)) + "-Dusted" : "";
        };
    }

    // ------------------------------------------------------------ what it feeds
    /** Nutrition and saturation: the base, plus most of what the fillings give, a little more for variety, and the seasoning's twist. No potion effects. */
    public FoodProperties food(int baseNutrition) {
        float nutrition = baseNutrition, sat = baseNutrition * 0.45f;
        for (ResourceLocation f : fillings) {
            FoodProperties fp = new ItemStack(BuiltInRegistries.ITEM.get(f)).get(DataComponents.FOOD);
            if (fp != null) { nutrition += fp.nutrition() * 0.8f; sat += fp.saturation() * 0.8f; }
        }
        if (fillings.stream().distinct().count() >= 3) nutrition += 1;
        switch (seasoning.getPath()) {
            case "salt" -> sat *= 1.15f; case "smoked_salt" -> sat *= 1.25f; case "herb_salt" -> sat *= 1.1f; case "frost_crystals" -> sat *= 1.1f;
            case "kelp_salt", "onion_salt" -> sat *= 1.2f; case "glow_spice" -> sat *= 1.1f;
            case "ember_flakes", "sun_zest", "sugar", "tart_zest", "berry_dust" -> nutrition += 1; case "cocoa_sugar", "autumn_spice" -> { nutrition += 1; sat *= 1.05f; }
            case "honey_bottle" -> nutrition += 2; default -> { }
        }
        if (!sauce.equals(NONE)) {
            switch (sauce.getPath()) { case "honey_bottle" -> nutrition += 2; case "milk_bucket" -> { nutrition += 1; sat *= 1.1f; } case "tomato_sauce" -> { nutrition += 1; sat *= 1.1f; } default -> { nutrition += 1; sat += 0.4f; } }
        }
        sat *= 0.85f + 0.3f * Math.min(100, taste()) / 100f;   // a tastier dish fills a little more
        int n = Math.min(24, Math.round(nutrition));
        return new FoodProperties(n, Math.min(n * 1.1f, sat), false, 1.6f + 0.15f * fillings.size(), Optional.empty(), List.of());
    }
}
