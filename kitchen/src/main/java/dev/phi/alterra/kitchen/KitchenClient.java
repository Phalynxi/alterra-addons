package dev.phi.alterra.kitchen;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Client side: juices as fluids look like water in the juice's colour (in tanks and pipes). */
@EventBusSubscriber(modid = AlterraKitchen.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KitchenClient {
    private KitchenClient() {}

    @SubscribeEvent
    public static void fluidLook(RegisterClientExtensionsEvent e) {
        for (var j : AlterraKitchen.JUICES) {
            int tint = 0xFF000000 | j.color();
            e.registerFluidType(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
                @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
                @Override public int getTintColor() { return tint; }
            }, j.type().get());
        }
    }
}
