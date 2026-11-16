package dev.phi.alterra.illuminate.snow;

import dev.phi.alterra.illuminate.Illuminate;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Whatever walks on the snow near the camera presses its feet into the fine dents (see {@link SnowDents}): a print a stride, left and right, deeper for something heavy. */
@EventBusSubscriber(modid = Illuminate.ID, value = Dist.CLIENT)
public final class SnowStamper {
    private SnowStamper() {}
    private static final Int2ObjectOpenHashMap<double[]> LAST = new Int2ObjectOpenHashMap<>();   // per entity: where it last stamped (x, z) and which foot was next
    private static int tick;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance(); var lv = mc.level;
        if (lv == null || mc.player == null || mc.isPaused() || !SnowSurface.active()) { if (lv == null) { LAST.clear(); SnowDents.clear(); } return; }
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        if (++tick % 100 == 0 && SnowDents.any()) SnowDents.relax(lv.getRainLevel(1f) > 0.1f ? 0.985f : 0.994f);   // a snowfall fills them in several times faster
        for (var en : lv.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || le instanceof ArmorStand || le.isSpectator() || le.isInvisible() || le.isPassenger() || !le.onGround() || le.isInWaterOrBubble()) continue;
            if (le.distanceToSqr(cam.x, cam.y, cam.z) > 40 * 40) continue;
            float h = SnowSurface.heightAt(lv, le.getX(), le.getZ());
            if (Float.isNaN(h) || Math.abs(le.getY() - h) > 0.6) continue;   // not standing on the snow
            double[] last = LAST.get(le.getId());
            if (last == null) { LAST.put(le.getId(), new double[] {le.getX(), le.getZ(), 0}); continue; }
            double dx = le.getX() - last[0], dz = le.getZ() - last[1], d = Math.sqrt(dx * dx + dz * dz);
            float w = Math.max(0.4f, le.getBbWidth());
            if (d < 0.55 * Math.max(1f, w / 0.6f)) continue;   // a stride
            if (d > 6) { last[0] = le.getX(); last[1] = le.getZ(); continue; }   // teleported
            double yaw = Math.toRadians(le.yBodyRot), side = (last[2] == 0 ? -1 : 1) * 0.13 * Math.max(1f, w / 0.6f);
            double px = le.getX() - Math.cos(yaw) * side, pz = le.getZ() - Math.sin(yaw) * side;
            float mass = Math.min(1f, w * w * le.getBbHeight() * 0.18f);
            SnowDents.stamp(px, pz, 0.16f * Math.max(1f, w / 0.6f) + 0.02f, 0.08f + 0.25f * mass);
            last[0] = le.getX(); last[1] = le.getZ(); last[2] = last[2] == 0 ? 1 : 0;
        }
        if (LAST.size() > 400) LAST.clear();
    }

    @SubscribeEvent public static void left(ClientPlayerNetworkEvent.LoggingOut e) { LAST.clear(); SnowDents.clear(); }
}
