package dev.phi.alterra.kitchen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.resources.ResourceLocation;

/**
 * What bad food does to you. Food poisoning burns your hunger and now and then makes you throw up; toxin is poison that can kill (milk cures it);
 * nerve damage scrambles your controls and twitches your view (KitchenEffectsClient) and now and then flips your hotbar to another slot.
 */
public final class KitchenEffects {
    private KitchenEffects() {}
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, AlterraKitchen.ID);

    private static class Named extends MobEffect {
        private final String title;
        Named(String title, MobEffectCategory category, int colour) { super(category, colour); this.title = title; }
        @Override public Component getDisplayName() { return Component.literal(title); }
    }

    public static final DeferredHolder<MobEffect, MobEffect> FOOD_POISONING = EFFECTS.register("food_poisoning", () -> new Named("Food Poisoning", MobEffectCategory.HARMFUL, 0xA8B23A) {
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 40 == 0; }
        @Override public boolean applyEffectTick(LivingEntity who, int amplifier) {
            if (who instanceof Player p) {
                p.causeFoodExhaustion(2.5f * (amplifier + 1));
                if (!p.level().isClientSide && p.getRandom().nextFloat() < 0.18f + 0.1f * amplifier) {   // throws up
                    p.getFoodData().setFoodLevel(Math.max(0, p.getFoodData().getFoodLevel() - 2));
                    p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_BURP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.5f);
                    if (p.level() instanceof ServerLevel sl) sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SNEEZE, p.getX(), p.getEyeY() - 0.2, p.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
                }
            }
            return true;
        }
    });

    public static final DeferredHolder<MobEffect, MobEffect> TOXIN = EFFECTS.register("toxin", () -> {
        MobEffect e = new Named("Toxin", MobEffectCategory.HARMFUL, 0x3E7A1E) {
            @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { int every = Math.max(5, 20 >> amplifier); return duration % every == 0; }
            @Override public boolean applyEffectTick(LivingEntity who, int amplifier) { who.hurt(who.damageSources().magic(), 1.0f); return true; }   // unlike poison, it can kill
        };
        e.addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "toxin_slow"), -0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        return e;
    });

    public static final DeferredHolder<MobEffect, MobEffect> NERVE_DAMAGE = EFFECTS.register("nerve_damage", () -> {
        MobEffect e = new Named("Nerve Damage", MobEffectCategory.HARMFUL, 0x8A1FD0) {
            @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 10 == 0; }
            @Override public boolean applyEffectTick(LivingEntity who, int amplifier) {
                if (amplifier >= 1 && !who.level().isClientSide && who.getRandom().nextFloat() < 0.12f * amplifier) who.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 120 + 40 * amplifier, 0, false, false, false));   // the world swims
                if (amplifier >= 2 && !who.level().isClientSide && who.getRandom().nextFloat() < 0.1f * amplifier) who.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 60 + 20 * amplifier, 0, false, false, false));   // the light fades out and back
                if (amplifier >= 3 && !who.level().isClientSide && who.getRandom().nextFloat() < 0.15f) who.hurt(who.damageSources().magic(), amplifier - 2f);   // and at the worst it is killing you
                if (who instanceof ServerPlayer p && p.getRandom().nextFloat() < 0.04f * (amplifier + 1)) {   // the hand grabs another slot
                    int slot = p.getRandom().nextInt(9);
                    p.getInventory().selected = slot;
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));
                }
                return true;
            }
        };
        e.addAttributeModifier(Attributes.ATTACK_SPEED, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "nerve_attack"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        e.addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "nerve_legs"), -0.07, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        e.addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "nerve_grip"), -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        return e;
    });

    /** After nerve damage wears off you black out for a few seconds: you drop where you stand and cannot move (see Blackout). Hidden: it has no icon. */
    public static final DeferredHolder<MobEffect, MobEffect> UNCONSCIOUS = EFFECTS.register("unconscious", () -> {
        MobEffect e = new Named("Unconscious", MobEffectCategory.HARMFUL, 0x2A0A10);
        e.addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "unconscious_still"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        e.addAttributeModifier(Attributes.JUMP_STRENGTH, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "unconscious_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        return e;
    });

    public static final DeferredHolder<MobEffect, MobEffect> HALLUCINATION = EFFECTS.register("hallucination", () -> new Named("Hallucination", MobEffectCategory.HARMFUL, 0xE060C8));

    public static final DeferredHolder<MobEffect, MobEffect> PARALYSIS = EFFECTS.register("paralysis", () -> {
        MobEffect e = new Named("Paralysis", MobEffectCategory.HARMFUL, 0x6E7E8E);
        e.addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "paralysis_slow"), -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        return e;
    });

    public static final DeferredHolder<MobEffect, MobEffect> CARDIAC = EFFECTS.register("cardiac", () -> new Named("Cardiac Strain", MobEffectCategory.HARMFUL, 0xC02840) {
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 20 == 0; }
        @Override public boolean applyEffectTick(LivingEntity who, int amplifier) {   // the heart misses a beat now and then
            if (who.getRandom().nextFloat() < 0.12f + 0.08f * amplifier) {
                who.hurt(who.damageSources().magic(), 2.0f);
                who.level().playSound(null, who.blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.7f);
            }
            return true;
        }
    });
}
