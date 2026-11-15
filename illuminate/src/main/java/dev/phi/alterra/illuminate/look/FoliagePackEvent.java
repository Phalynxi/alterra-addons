package dev.phi.alterra.illuminate.look;

import dev.phi.alterra.illuminate.Illuminate;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Adds the pack with the patched foliage shaders (client resources). */
@EventBusSubscriber(modid = Illuminate.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FoliagePackEvent {
    private FoliagePackEvent() {}
    @SubscribeEvent public static void packs(AddPackFindersEvent e) { Foliage.packs(e); }
}
