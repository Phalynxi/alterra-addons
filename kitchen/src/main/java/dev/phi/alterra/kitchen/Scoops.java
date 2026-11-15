package dev.phi.alterra.kitchen;

import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The scoops on a stacked cone, bottom to top: each has a colour, a shape (round or swirl) and a flavour name. */
public record Scoops(List<Scoop> list) {
    public static final int MAX = 5;
    /** color2 is a twist's second flavour (-1 for none: a plain round scoop or swirl). */
    public record Scoop(int color, boolean swirl, String flavor, int color2) {
        Scoop(int color, boolean swirl, String flavor) { this(color, swirl, flavor, -1); }
        public boolean twist() { return color2 >= 0; }
        static final Codec<Scoop> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.INT.fieldOf("color").forGetter(Scoop::color), Codec.BOOL.fieldOf("swirl").forGetter(Scoop::swirl), Codec.STRING.fieldOf("flavor").forGetter(Scoop::flavor), Codec.INT.optionalFieldOf("color2", -1).forGetter(Scoop::color2)).apply(i, Scoop::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Scoop> STREAM = StreamCodec.composite(ByteBufCodecs.INT, Scoop::color, ByteBufCodecs.BOOL, Scoop::swirl, ByteBufCodecs.STRING_UTF8, Scoop::flavor, ByteBufCodecs.INT, Scoop::color2, Scoop::new);
    }
    public static final Codec<Scoops> CODEC = Scoop.CODEC.listOf().xmap(Scoops::new, Scoops::list);
    public static final StreamCodec<RegistryFriendlyByteBuf, Scoops> STREAM_CODEC = Scoop.STREAM.apply(ByteBufCodecs.list()).map(Scoops::new, Scoops::list);

    private static Map<String, Scoop> singles;
    /** The scoop that a single ice cream or soft serve is (from alterra_kitchen_scoops.json), by item id path. */
    public static Scoop of(String path) {
        if (singles == null) {
            Map<String, List<Object>> raw = AlterraKitchen.read("alterra_kitchen_scoops.json", new TypeToken<Map<String, List<Object>>>() {});
            singles = new HashMap<>();
            raw.forEach((k, v) -> singles.put(k, new Scoop(((Number) v.get(0)).intValue(), ((Number) v.get(1)).intValue() != 0, (String) v.get(2))));
        }
        return singles.get(path);
    }

    private int distinct() { return (int) list.stream().map(Scoop::flavor).distinct().count(); }
    /** Which model the stack uses: each scoop adds a base-3 digit (0 round, 1 swirl, 2 twist); the number of scoops is the last digit. */
    public int modelCode() {
        int t = 0, p = 1;
        for (Scoop sc : list) { t += (sc.twist() ? 2 : sc.swirl() ? 1 : 0) * p; p *= 3; }
        return t * 10 + list.size();
    }
    public int nutrition() { return 3 + 2 * list.size(); }
    public float eatSeconds() { return 1.6f + 0.4f * (list.size() - 1); }
    /** Variety makes a stack taste better, repeats a little worse. */
    public int taste() { return Math.max(2, Math.min(100, 72 + 7 * distinct() - 5 * (list.size() - distinct()) + 4 * (int) list.stream().filter(Scoop::twist).count())); }

    public String name() {
        int n = list.size();
        if (n == 1 && list.get(0).twist()) { String t = list.get(0).flavor() + " Twist"; return t.length() <= 22 ? t : "Twist Cone"; }
        String[] counts = {"", "", "Double", "Triple", "Four-Scoop", "Five-Scoop"};
        if (distinct() == 1) return counts[Math.max(2, n)] + " " + list.get(0).flavor();
        if (n == 2) { String t = list.get(0).flavor() + " and " + list.get(1).flavor() + " Double"; return t.length() <= 22 ? t : "Double Scoop"; }
        return n == 3 ? "Triple Scoop" : (n == 4 ? "Four-Scoop Tower" : "Five-Scoop Tower");
    }
}
