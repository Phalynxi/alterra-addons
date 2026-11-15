package dev.phi.alterra.kitchen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** The client's view of TasteKnowledge (a separate class so the common code never touches the client's Minecraft class on a server). */
public final class ClientKnowledge {
    private ClientKnowledge() {}
    public static boolean knows(ItemStack st) {
        var p = Minecraft.getInstance().player;
        return p == null || TasteKnowledge.knows(p, st);
    }
    public static boolean known(ItemStack st) { return net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? knows(st) : true; }
}
