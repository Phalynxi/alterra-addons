package dev.phi.alterra.kitchen;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Client side: juices as fluids look like water in the juice's colour (in tanks and pipes), and the made-up dishes are drawn from a texture painted for each one (see DishLook). */
@EventBusSubscriber(modid = AlterraKitchen.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KitchenClient {
    private KitchenClient() {}

    @SubscribeEvent
    public static void dishRenderer(RegisterClientExtensionsEvent e) {
        var renderer = new DishRenderer();
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        };
        e.registerItem(ext, AlterraKitchen.DISH_ITEMS.values().stream().map(i -> (net.minecraft.world.item.Item) i.get()).toArray(net.minecraft.world.item.Item[]::new));
    }

    @SubscribeEvent
    public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) { if (net.neoforged.fml.ModList.get().isLoaded("create")) CreateCompat.renderers(e); }

    /** A mixed drink: its liquid in two tints (top and bottom), and which glass and garnish it has. */
    @SubscribeEvent
    public static void mixedDrinkColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item e) {
        e.register((stack, layer) -> {
            MixedDrink m = stack.get(AlterraKitchen.MIXED.get());
            if (m == null || m.drinks().isEmpty() || layer > 1) return 0xFFFFFFFF;
            return 0xFF000000 | (layer == 0 ? m.top() : m.bottom());
        }, AlterraKitchen.MIXED_DRINK.get());
    }

    @SubscribeEvent
    public static void mixedDrinkLook(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
        e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(AlterraKitchen.MIXED_DRINK.get(), ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "look"),
                (st, level, who, seed) -> { MixedDrink m = st.get(AlterraKitchen.MIXED.get()); return m == null || m.drinks().isEmpty() ? 0f : m.look() / 1024f; }));
    }

    /** Each scoop's grey layer is tinted with its flavour's colour (layer 0, the cone, is not); a twist has a second layer in its other colour. */
    @SubscribeEvent
    public static void scoopColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item e) {
        e.register((stack, layer) -> {
            Scoops s = stack.get(AlterraKitchen.SCOOPS.get());
            if (s == null || layer < 1 || layer > 2 * s.list().size()) return 0xFFFFFFFF;
            Scoops.Scoop sc = s.list().get((layer - 1) / 2);
            return 0xFF000000 | (layer % 2 == 1 ? sc.color() : sc.color2());   // odd layers are the scoops, even ones a twist's second colour
        }, AlterraKitchen.ICE_CREAM_STACK.get());
    }

    @SubscribeEvent
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) { e.register(AlterraKitchen.KITCHEN_MENU.get(), AssemblyScreen::new); }

    @SubscribeEvent
    public static void dishCard(net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent e) { e.register(DishCard.class, ClientDishCard::new); }

    @SubscribeEvent
    public static void effectOverlay(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) { e.registerBelowAll(ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "nerve_blur"), KitchenEffectsClient::blur); e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "bad_food"), KitchenEffectsClient::overlay); }

    @SubscribeEvent
    public static void dishReload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> DishLook.clear());
    }

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
