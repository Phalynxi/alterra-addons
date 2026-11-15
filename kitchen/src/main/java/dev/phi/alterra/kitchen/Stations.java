package dev.phi.alterra.kitchen;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.function.Supplier;

/** Which kitchen station is being used: the dish, ice cream and twist recipes only work at a prep table (tier 1) or a kitchen counter (tier 2), never at a plain crafting table. */
public final class Stations {
    private Stations() {}
    private static final ThreadLocal<Integer> TIER = ThreadLocal.withInitial(() -> 0);
    /** The tier of the station whose menu is being worked on right now on this thread (0 anywhere else). */
    public static int tier() { return TIER.get(); }
    public static <T> T at(int tier, Supplier<T> action) {
        int before = TIER.get(); TIER.set(tier);
        try { return action.get(); } finally { TIER.set(before); }
    }
    public static void at(int tier, Runnable action) { at(tier, () -> { action.run(); return null; }); }

    /** How many ticks of freezing an item gives the ice cream machine (0 if it is no use). */
    public static int cold(ItemStack st) {
        if (st.is(Items.SNOWBALL)) return 100;
        if (st.is(Items.SNOW_BLOCK)) return 400;
        if (st.is(Items.ICE)) return 800;
        if (st.is(Items.PACKED_ICE)) return 1600;
        if (st.is(Items.BLUE_ICE)) return 3200;
        return 0;
    }
}
