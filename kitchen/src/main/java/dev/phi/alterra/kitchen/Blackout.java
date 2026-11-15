package dev.phi.alterra.kitchen;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * When nerve damage runs out (not when milk cures it) you pass out where you stand for eight seconds: you collapse (everyone sees you lying there), cannot move, and wake up in the same spot, starving and parched.
 * The fall and the dark are drawn by KitchenEffectsClient.
 */
@EventBusSubscriber(modid = AlterraKitchen.ID)
public final class Blackout {
    private Blackout() {}
    static final int TICKS = 160;
    /** How long you are out, by how strong the nerve damage was. */
    static int ticks(int amplifier) { return TICKS + 80 * amplifier; }

    @SubscribeEvent
    public static void expired(MobEffectEvent.Expired e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getEffectInstance() == null) return;
        if (e.getEffectInstance().is(KitchenEffects.NERVE_DAMAGE) && !p.isCreative() && !p.isSpectator())
            p.addEffect(new MobEffectInstance(KitchenEffects.UNCONSCIOUS, ticks(e.getEffectInstance().getAmplifier()), e.getEffectInstance().getAmplifier(), false, false, false));   // out longer the worse it was
        else if (e.getEffectInstance().is(KitchenEffects.UNCONSCIOUS)) wake(p);
    }

    /** Lying down while out, on both sides (the pose others see comes from the server). */
    @SubscribeEvent
    public static void pose(PlayerTickEvent.Pre e) {
        Player p = e.getEntity();
        Pose want = p.hasEffect(KitchenEffects.UNCONSCIOUS) ? Pose.SWIMMING : null;
        if (p.getForcedPose() != want) p.setForcedPose(want);
    }

    /** Waking up: most of the hunger and (with Legendary Survival Overhaul) most of the thirst is gone. */
    private static void wake(ServerPlayer p) {
        var food = p.getFoodData();
        food.setFoodLevel(Math.max(2, food.getFoodLevel() / 4)); food.setSaturation(0f);
        try {
            Object t = Class.forName("sfiomn.legendarysurvivaloverhaul.util.AttachmentUtil").getMethod("getThirstAttachment", Player.class).invoke(null, p);
            if (t != null) {
                int hyd = (int) t.getClass().getMethod("getHydrationLevel").invoke(t);
                t.getClass().getMethod("setHydrationLevel", int.class).invoke(t, Math.max(2, hyd / 4));
                t.getClass().getMethod("setSaturation", float.class).invoke(t, 0f);
                t.getClass().getMethod("setDirty").invoke(t);
            }
        } catch (Throwable ignored) { }   // no thirst mod
    }
}
