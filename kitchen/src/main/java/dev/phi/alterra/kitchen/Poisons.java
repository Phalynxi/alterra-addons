package dev.phi.alterra.kitchen;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;

/** Which poisonous things do what: rotten food and spider eyes, poisonous potatoes, pufferfish, and the wild plants (nightshade, hemlock, deathcap, oleander, the poison fern). Used when one is eaten and when a dish made with it is. */
public final class Poisons {
    private Poisons() {}
    private record Dose(Holder<MobEffect> effect, int ticks, int amplifier) {}
    private static Map<String, List<Dose>> table;

    private static Map<String, List<Dose>> table() {
        if (table == null) table = Map.ofEntries(
            Map.entry("rotten_flesh", List.of(new Dose(KitchenEffects.FOOD_POISONING, 400, 0))),
            Map.entry("rotten_tomato", List.of(new Dose(KitchenEffects.FOOD_POISONING, 400, 0))),
            Map.entry("spider_eye", List.of(new Dose(KitchenEffects.TOXIN, 160, 0))),
            Map.entry("poisonous_potato", List.of(new Dose(KitchenEffects.TOXIN, 200, 0), new Dose(KitchenEffects.FOOD_POISONING, 300, 0))),
            Map.entry("pufferfish", List.of(new Dose(KitchenEffects.NERVE_DAMAGE, 1200, 0), new Dose(KitchenEffects.TOXIN, 160, 0))),
            Map.entry("poison_fern_fronds", List.of(new Dose(KitchenEffects.NERVE_DAMAGE, 1800, 0))),
            Map.entry("nightshade_berries", List.of(new Dose(KitchenEffects.HALLUCINATION, 1500, 0), new Dose(KitchenEffects.TOXIN, 140, 0))),
            Map.entry("hemlock_leaves", List.of(new Dose(KitchenEffects.PARALYSIS, 1200, 0), new Dose(KitchenEffects.TOXIN, 200, 0))),
            Map.entry("deathcap", List.of(new Dose(KitchenEffects.TOXIN, 260, 1), new Dose(KitchenEffects.FOOD_POISONING, 800, 1))),
            Map.entry("oleander_leaves", List.of(new Dose(KitchenEffects.CARDIAC, 1400, 0), new Dose(KitchenEffects.TOXIN, 140, 0))));
        return table;
    }
    public static boolean isToxic(String path) { return table().containsKey(path); }
    /** Gives the effects of one poisonous thing; `bonus` makes them stronger (more poisons in the same dish). */
    public static void apply(String path, LivingEntity who, int bonus) {
        for (Dose d : table().getOrDefault(path, List.of())) {   // doses add up: every bite on top of the last one makes it a level stronger (up to 5) and last longer
            MobEffectInstance cur = who.getEffect(d.effect);
            int amp = Math.min(4, d.amplifier + bonus + (cur == null ? 0 : cur.getAmplifier() + 1));
            int ticks = d.ticks * (1 + bonus / 2) + (cur == null ? 0 : cur.getDuration() / 2);
            who.addEffect(new MobEffectInstance(d.effect, ticks, amp));
        }
    }
}
