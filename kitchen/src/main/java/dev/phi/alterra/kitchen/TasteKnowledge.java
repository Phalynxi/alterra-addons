package dev.phi.alterra.kitchen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/**
 * Taste, flavour and poison are a secret until you have eaten that very food (a plain food or drink by its kind, a dish by its kind and exactly what is in it)
 * and written it in an Explorer's Journal you carry: the notes live on the book. Lose the book and what you found out is gone; anyone holding it can read them. Creative players are no exception.
 */
public final class TasteKnowledge {
    private TasteKnowledge() {}
    private static final net.minecraft.resources.ResourceLocation JOURNAL = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("alterra", "field_guide");

    public static int key(ItemStack st) {
        int id = BuiltInRegistries.ITEM.getKey(st.getItem()).hashCode();
        Dish d = st.get(AlterraKitchen.DISH.get());
        Scoops sc = st.get(AlterraKitchen.SCOOPS.get());
        if (sc != null) return 31 * id + sc.hashCode();
        return d == null ? id : 31 * id + d.hashCode();
    }
    private static boolean isJournal(ItemStack st) { return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).equals(JOURNAL); }
    private static java.util.List<ItemStack> journals(Player p) {
        var l = new ArrayList<ItemStack>();
        for (ItemStack st : p.getInventory().items) if (isJournal(st)) l.add(st);
        for (ItemStack st : p.getInventory().offhand) if (isJournal(st)) l.add(st);
        return l;
    }
    public static int notes(ItemStack journal) { var s = journal.get(AlterraKitchen.JOURNAL_NOTES.get()); return s == null ? 0 : s.keys().size(); }
    /** Does any journal this player carries have the food in it? */
    public static boolean knows(Player p, ItemStack st) {
        int k = key(st);
        for (ItemStack j : journals(p)) { var s = j.get(AlterraKitchen.JOURNAL_NOTES.get()); if (s != null && s.keys().contains(k)) return true; }
        return false;
    }
    /** Called when something is eaten (before the stack is used up): the first journal the player carries gets the note; with no journal, nothing is remembered. */
    public static void learn(LivingEntity who, ItemStack st) {
        if (!(who instanceof ServerPlayer p) || knows(p, st)) return;
        var js = journals(p);
        if (js.isEmpty()) { p.displayClientMessage(net.minecraft.network.chat.Component.literal("You have no journal to write this down in.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY), true); return; }
        ItemStack j = js.get(0);
        var have = j.get(AlterraKitchen.JOURNAL_NOTES.get());
        var l = new ArrayList<>(have == null ? java.util.List.<Integer>of() : have.keys()); l.add(key(st));
        j.set(AlterraKitchen.JOURNAL_NOTES.get(), new TastedSet(l));
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("Noted in your Explorer's Journal.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
    }
}
