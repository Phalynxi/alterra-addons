package dev.phi.alterra.illuminate.look;

import dev.phi.alterra.illuminate.Illuminate;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** After a resource reload the pictures in the block atlas have moved: the sway mask is made again. */
@EventBusSubscriber(modid = Illuminate.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FoliageReload {
    private FoliageReload() {}
    @SubscribeEvent
    public static void listeners(RegisterClientReloadListenersEvent e) { e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> Foliage.invalidateMask()); }
}
