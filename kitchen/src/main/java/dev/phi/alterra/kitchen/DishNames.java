package dev.phi.alterra.kitchen;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Names players give their dishes: kept with the player (persistent data, so it lasts through death), one per recipe. A recipe is the item plus what is in it
 * (the dish's fillings, seasoning and sauce, or an ice cream's scoops), so making the same thing again gives it the same name.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = AlterraKitchen.ID)
public final class DishNames {
    private DishNames() {}
    private static final String TAG = "alterra_kitchen_dish_names";

    /** Names stay after dying (and coming back from the End). */
    @net.neoforged.bus.api.SubscribeEvent
    public static void keep(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(TAG)) e.getEntity().getPersistentData().put(TAG, old.getCompound(TAG).copy());
    }

    static String key(ItemStack st) {
        Object what = st.get(AlterraKitchen.DISH.get()); if (what == null) what = st.get(AlterraKitchen.SCOOPS.get());
        if (what == null) return null;
        if (what instanceof Dish d) {   // the order the fillings went in does not matter
            var f = d.fillings().stream().map(Object::toString).sorted().toList();
            what = f + "|" + d.seasoning() + "|" + d.sauce();
        }
        return BuiltInRegistries.ITEM.getKey(st.getItem()) + "#" + Integer.toHexString(what.toString().hashCode());
    }

    private static CompoundTag names(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    /** The saved name for this dish, if the player named it before. */
    static String saved(Player p, ItemStack st) { String k = key(st); return k == null ? null : names(p).contains(k) ? names(p).getString(k) : null; }

    /** Save (or with an empty name, forget) the player's name for this dish. */
    static void save(Player p, ItemStack st, String name) {
        String k = key(st); if (k == null) return;
        name = name.strip();
        if (name.isEmpty()) names(p).remove(k); else names(p).putString(k, name.length() > 32 ? name.substring(0, 32) : name);
    }

    /** The dish with its saved name on it (not in italics, so it reads like a real name), or as it is. */
    static ItemStack named(Player p, ItemStack st) {
        String n = st.isEmpty() ? null : saved(p, st);
        if (n == null) return st;
        st.set(DataComponents.CUSTOM_NAME, Component.literal(n).withStyle(s -> s.withItalic(false)));
        return st;
    }
}
