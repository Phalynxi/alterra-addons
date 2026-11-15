package dev.phi.alterra.kitchen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** What bad food looks and feels like: nerve damage scrambles the controls for a few ticks at a time and twitches the view and the screen fills with static; toxin pulses the edges of the screen a sick green. */
@EventBusSubscriber(modid = AlterraKitchen.ID, value = Dist.CLIENT)
public final class KitchenEffectsClient {
    private KitchenEffectsClient() {}
    private static int scrambleTicks, scrambleMode;

    private static MobEffectInstance nerve(LocalPlayer p) { return p == null ? null : p.getEffect(KitchenEffects.NERVE_DAMAGE); }
    private static MobEffectInstance out(LocalPlayer p) { return p == null ? null : p.getEffect(KitchenEffects.UNCONSCIOUS); }
    private static float smooth(float a, float b, float x) { float t = Math.max(0f, Math.min(1f, (x - a) / (b - a))); return t * t * (3f - 2f * t); }
    private static float lockYaw = Float.NaN, lockPitch;

    @SubscribeEvent
    public static void heartbeat(ClientTickEvent.Post e) {   // strong nerve damage: you hear your own heart, faster the worse it is
        LocalPlayer p = Minecraft.getInstance().player; MobEffectInstance n = nerve(p);
        if (n == null || n.getAmplifier() < 2 || Minecraft.getInstance().isPaused()) return;
        int every = Math.max(12, 30 - 6 * n.getAmplifier());
        if (p.tickCount % every == 0) p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f + 0.1f * n.getAmplifier(), 0.9f, false);
    }

    @SubscribeEvent
    public static void passedOut(ClientTickEvent.Post e) {   // while out the view stays put, and you hear yourself hit the ground
        LocalPlayer p = Minecraft.getInstance().player; MobEffectInstance o = out(p);
        if (o == null) { lockYaw = Float.NaN; return; }
        if (Float.isNaN(lockYaw)) { lockYaw = p.getYRot(); lockPitch = p.getXRot(); }
        p.setYRot(lockYaw); p.yRotO = lockYaw; p.setXRot(lockPitch); p.xRotO = lockPitch;
        if (Blackout.ticks(o.getAmplifier()) - o.getDuration() == 14) p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_BIG_FALL, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.8f, false);
    }

    private static final net.minecraft.resources.ResourceLocation BLOODSHOT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "textures/gui/bloodshot.png");
    private static java.lang.reflect.Field blurField; private static boolean blurMissing;

    /** Under the HUD (registered in KitchenClient): the world goes soft and out of focus with nerve damage, breathing in and out, and most of all while coming to. */
    public static void blur(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player;
        if (p == null || blurMissing) return;
        long ms = net.minecraft.Util.getMillis(); float radius = 0f;
        MobEffectInstance n = nerve(p);
        if (n != null) radius = (1.5f + 3f * (float) (0.5 + 0.5 * Math.sin(ms / 1400.0))) * (1f + 0.4f * n.getAmplifier());
        MobEffectInstance o = out(p);
        if (o != null) radius = Math.max(radius, 10f * smooth(0f, 40f, o.getDuration()));
        if (radius < 0.5f) return;
        try {
            if (blurField == null) { blurField = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("blurEffect"); blurField.setAccessible(true); }
            var post = (net.minecraft.client.renderer.PostChain) blurField.get(mc.gameRenderer);
            if (post == null) return;
            g.flush();
            post.setUniform("Radius", radius); post.process(delta.getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(false);
        } catch (Throwable t) { blurMissing = true; }
    }

    private static final net.minecraft.resources.ResourceLocation VIGNETTE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "textures/gui/vignette.png");
    /** A soft round darkening (or colouring) of the edges of the screen; size 1 is the usual, more reaches further in. */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha, float size) {
        if (alpha <= 0.01f) return;
        int mx = (int) (w * (1f - size) * 0.5f), my = (int) (h * (1f - size) * 0.5f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Math.min(1f, alpha));
        g.blit(VIGNETTE, mx, my, w - 2 * mx, h - 2 * my, 0f, 0f, 128, 128, 128, 128);
        if (mx > 0 || my > 0) {   // smaller than the screen: what is outside it is fully dark
            int c = (Math.min(255, (int) (alpha * 255)) << 24) | rgb;
            g.setColor(1f, 1f, 1f, 1f);
            if (my > 0) { g.fill(0, 0, w, my, c); g.fill(0, h - my, w, h, c); }
            if (mx > 0) { g.fill(0, Math.max(0, my), mx, h - Math.max(0, my), c); g.fill(w - mx, Math.max(0, my), w, h - Math.max(0, my), c); }
        }
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void bloodshot(GuiGraphics g, int w, int h, float alpha, float grow) {
        grow = Math.max(0.02f, grow);   // never smaller than the screen
        if (alpha <= 0.01f) return;
        int mx = (int) (w * grow), my = (int) (h * grow);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, Math.min(1f, alpha));
        g.blit(BLOODSHOT, -mx, -my, w + 2 * mx, h + 2 * my, 0f, 0f, 128, 128, 128, 128);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static MobEffectInstance toxin(LocalPlayer p) { return p == null ? null : p.getEffect(KitchenEffects.TOXIN); }

    @SubscribeEvent
    public static void controls(MovementInputUpdateEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer p)) return;
        MobEffectInstance n = nerve(p);
        if (n == null) { scrambleTicks = 0; return; }
        RandomSource r = p.getRandom();
        if (scrambleTicks <= 0 && r.nextFloat() < 0.03f + 0.03f * n.getAmplifier()) { scrambleTicks = 6 + r.nextInt(14); scrambleMode = r.nextInt(4); }
        if (scrambleTicks-- > 0) {
            var in = e.getInput();
            switch (scrambleMode) {
                case 0 -> { in.forwardImpulse = -in.forwardImpulse; in.leftImpulse = -in.leftImpulse; }   // everything backwards
                case 1 -> { in.forwardImpulse = 0; in.leftImpulse = 0; in.up = false; in.down = false; in.left = false; in.right = false; }   // the legs stop answering
                case 2 -> { float f = in.forwardImpulse; in.forwardImpulse = in.leftImpulse; in.leftImpulse = f; }   // left is forward
                default -> in.jumping = true;   // a spasm
            }
        }
    }

    @SubscribeEvent
    public static void paralysis(MovementInputUpdateEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer p) || !(frozen(p) || out(p) != null)) return;
        var in = e.getInput(); in.forwardImpulse = 0; in.leftImpulse = 0; in.up = false; in.down = false; in.left = false; in.right = false; in.jumping = false; in.shiftKeyDown = false;
    }
    /** Paralysis comes in bursts: locked for 2 seconds (more at higher levels) out of every 6. */
    private static boolean frozen(LocalPlayer p) {
        MobEffectInstance x = p == null ? null : p.getEffect(KitchenEffects.PARALYSIS);
        return x != null && net.minecraft.Util.getMillis() % 6000 < 2000 + 700L * x.getAmplifier();
    }

    @SubscribeEvent
    public static void hallucinate(ClientTickEvent.Post e) {   // sounds that are not there: behind you, close by
        Minecraft mc = Minecraft.getInstance(); LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused() || !p.hasEffect(KitchenEffects.HALLUCINATION) || p.getRandom().nextInt(140) != 0) return;
        var r = p.getRandom(); double a = p.getYRot() * Math.PI / 180 + Math.PI + (r.nextDouble() - 0.5);
        var sound = new net.minecraft.sounds.SoundEvent[] {net.minecraft.sounds.SoundEvents.CREEPER_PRIMED, net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT, net.minecraft.sounds.SoundEvents.SKELETON_AMBIENT, net.minecraft.sounds.SoundEvents.ENDERMAN_STARE, net.minecraft.sounds.SoundEvents.WARDEN_NEARBY_CLOSE, net.minecraft.sounds.SoundEvents.GRASS_STEP}[r.nextInt(6)];
        mc.level.playLocalSound(p.getX() - Math.sin(a) * 4, p.getY(), p.getZ() + Math.cos(a) * 4, sound, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.8f + r.nextFloat() * 0.4f, false);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && p.hasEffect(KitchenEffects.HALLUCINATION)) e.setFOV(e.getFOV() + Math.sin(net.minecraft.Util.getMillis() / 700.0) * 9 + Math.sin(net.minecraft.Util.getMillis() / 230.0) * 3);
    }

    @SubscribeEvent
    public static void twitch(ClientTickEvent.Post e) {
        LocalPlayer p = Minecraft.getInstance().player;
        MobEffectInstance n = nerve(p);
        if (n == null || Minecraft.getInstance().isPaused()) return;
        RandomSource r = p.getRandom();
        if (r.nextFloat() < 0.025f + 0.02f * n.getAmplifier()) { p.setYRot(p.getYRot() + (r.nextFloat() - 0.5f) * 14f * (n.getAmplifier() + 1)); p.setXRot(Math.max(-90f, Math.min(90f, p.getXRot() + (r.nextFloat() - 0.5f) * 8f))); }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        LocalPlayer p = Minecraft.getInstance().player;
        MobEffectInstance n = nerve(p);
        if (n != null) {
            double t = net.minecraft.Util.getMillis() / 1000.0; float k = 1f + n.getAmplifier();
            e.setRoll(e.getRoll() + (float) (Math.sin(t * 7.3) + Math.sin(t * 13.1) * 0.6) * 1.2f * k);
            e.setPitch(e.getPitch() + (float) Math.sin(t * 9.7) * 0.6f * k);
        }
        MobEffectInstance x = toxin(p);
        if (x != null) e.setRoll(e.getRoll() + (float) Math.sin(net.minecraft.Util.getMillis() / 900.0) * 1.5f);   // the world sways
        MobEffectInstance o = out(p);
        if (o != null) {   // passing out: the view tips over as you fall (with a little bounce when you hit the ground) and rights itself as you come to
            float pt = (float) e.getPartialTick(), el = Blackout.ticks(o.getAmplifier()) - o.getDuration() + pt, left = o.getDuration() - pt;
            float f = smooth(0f, 16f, el), drop = f < 0.85f ? f / 0.85f : 1f + (float) Math.sin((f - 0.85f) / 0.15f * Math.PI) * 0.06f;
            float k = drop * smooth(0f, 26f, left);
            e.setRoll(e.getRoll() + 78f * k); e.setPitch(e.getPitch() + 18f * k);
        }
    }

    /** Drawn over everything (registered in KitchenClient). */
    public static void overlay(GuiGraphics g, DeltaTracker delta) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        int w = g.guiWidth(), h = g.guiHeight(); long ms = net.minecraft.Util.getMillis();
        MobEffectInstance x = toxin(p);
        if (x != null) {   // a sick green creeps in from the edges and beats like a pulse
            float beat = (float) (0.5 + 0.5 * Math.sin(ms / 330.0)) * (0.6f + 0.2f * x.getAmplifier());
            vignette(g, w, h, 0x0C2A06, (40 + 90 * beat) / 255f * 2.2f, 1.0f);
        }
        if (p.hasEffect(KitchenEffects.HALLUCINATION)) {   // colours that drift
            float hh = (ms % 5000) / 5000f; int col = java.awt.Color.HSBtoRGB(hh, 0.7f, 1f) & 0xFFFFFF, col2 = java.awt.Color.HSBtoRGB(hh + 0.5f, 0.7f, 1f) & 0xFFFFFF;
            g.fillGradient(0, 0, w, h, 0x30000000 | col, 0x30000000 | col2);
        }
        if (frozen(p)) vignette(g, w, h, 0x000000, 0.85f, 0.75f);   // a tunnel closes in while you cannot move
        MobEffectInstance heart = p.getEffect(KitchenEffects.CARDIAC);
        if (heart != null) {   // a red pulse with each beat
            float beat = (float) Math.pow(0.5 + 0.5 * Math.sin(ms / 190.0), 6);
            vignette(g, w, h, 0x600010, (20 + 110 * beat) / 255f * 2f, 1.1f);
        }
        MobEffectInstance n = nerve(p);
        if (n != null) {   // bloodshot: dark edges with red veins, beating slowly and creeping in and out
            float beat = (float) Math.pow(0.5 + 0.5 * Math.sin(ms / 520.0), 3), creep = (float) (0.5 + 0.5 * Math.sin(ms / 2300.0));
            int lv = n.getAmplifier();
            bloodshot(g, w, h, 0.45f + 0.15f * lv + 0.2f * beat, 0.08f - 0.04f * creep);   // the veins breathe in and out
            if (lv >= 2) vignette(g, w, h, 0x000000, Math.min(1f, 0.3f * (lv - 1)) * (0.4f + 0.6f * beat), 1.15f - 0.1f * lv);   // worse: the edges go dark with every heartbeat
            if (lv >= 3 && (ms / 120) % 23 == 0) g.fill(0, 0, w, h, 0x60000000);   // and the worst: the sight cuts out for a moment now and then
        }
        MobEffectInstance o = out(p);
        if (o != null) {   // out cold: everything goes dark as you fall; coming to, the eyes open, close once more and open, the veins still there
            float pt = delta.getGameTimeDeltaPartialTick(false), el = Blackout.ticks(o.getAmplifier()) - o.getDuration() + pt, left = o.getDuration() - pt;
            if (left > 40f) { int a = (int) (255 * smooth(6f, 34f, el)); g.fill(0, 0, w, h, a << 24); }
            else {
                float open = smooth(36f, 8f, left) * (1f - 0.85f * (float) Math.exp(-Math.pow((left - 16f) / 3f, 2)));
                int lid = (int) (h * 0.5f * (1f - open)), soft = Math.max(4, h / 12);
                g.fill(0, 0, w, lid, 0xFF000000); g.fillGradient(0, lid, w, lid + soft, 0xFF000000, 0);
                g.fill(0, h - lid, w, h, 0xFF000000); g.fillGradient(0, h - lid - soft, w, h - lid, 0, 0xFF000000);
                bloodshot(g, w, h, 0.9f * smooth(0f, 40f, left), 0.02f);
            }
        }
    }
}
