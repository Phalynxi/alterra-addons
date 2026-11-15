package dev.phi.alterra.kitchen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Two soft serves and a snowball make a twist. Two or more ice creams (round, swirl or an already stacked cone), up to five scoops in all, make one tall cone with the scoops in the order they lie in the grid. The spare cones come back. */
public record IceCreamStackRecipe(CraftingBookCategory category) implements CraftingRecipe {
    static List<Scoops.Scoop> scoopsOf(ItemStack st) {
        if (st.getItem() instanceof IceCreamItem) { Scoops s = st.get(AlterraKitchen.SCOOPS.get()); return s == null ? null : s.list(); }
        Scoops.Scoop one = Scoops.of(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
        return one == null || !BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace().equals(AlterraKitchen.ID) ? null : List.of(one);
    }
    private static boolean snowball(ItemStack st) { return st.is(net.minecraft.world.item.Items.SNOWBALL); }
    /** Two soft serves of different flavours and a snowball: one swirl in both colours. */
    private static List<Scoops.Scoop> twist(CraftingInput in) {
        List<Scoops.Scoop> two = new ArrayList<>(); int snow = 0;
        for (ItemStack st : in.items()) {
            if (st.isEmpty()) continue;
            if (snowball(st)) { snow++; continue; }
            List<Scoops.Scoop> s = st.getItem() instanceof IceCreamItem ? null : scoopsOf(st);
            if (s == null || !s.get(0).swirl() || s.get(0).twist()) return null;
            two.add(s.get(0));
        }
        if (snow != 1 || two.size() != 2 || two.get(0).flavor().equals(two.get(1).flavor())) return null;
        return List.of(new Scoops.Scoop(two.get(0).color(), true, two.get(0).flavor() + "-" + two.get(1).flavor(), two.get(1).color()));
    }
    private static List<Scoops.Scoop> parts(CraftingInput in) {
        int tier = Stations.tier();   // a prep table stacks up to three scoops; the kitchen counter five, and makes twists
        if (tier < 1) return null;
        for (ItemStack st : in.items()) if (snowball(st)) return tier < 2 ? null : twist(in);
        List<Scoops.Scoop> all = new ArrayList<>(); int items = 0;
        for (ItemStack st : in.items()) {
            if (st.isEmpty()) continue;
            List<Scoops.Scoop> s = scoopsOf(st);
            if (s == null) return null;
            all.addAll(s); items++;
        }
        return items < 2 || all.size() > (tier < 2 ? 3 : Scoops.MAX) ? null : all;
    }

    @Override public boolean matches(CraftingInput in, Level level) { return parts(in) != null; }

    /** Two soft serves of different flavours as one twist scoop (null if they do not make one). */
    static Scoops.Scoop twistOf(ItemStack a, ItemStack b) {
        List<Scoops.Scoop> x = a.getItem() instanceof IceCreamItem ? null : scoopsOf(a), y = b.getItem() instanceof IceCreamItem ? null : scoopsOf(b);
        if (x == null || y == null || !x.get(0).swirl() || !y.get(0).swirl() || x.get(0).twist() || y.get(0).twist() || x.get(0).flavor().equals(y.get(0).flavor())) return null;
        return new Scoops.Scoop(x.get(0).color(), true, x.get(0).flavor() + "-" + y.get(0).flavor(), y.get(0).color());
    }

    @Override public ItemStack assemble(CraftingInput in, HolderLookup.Provider registries) {
        List<Scoops.Scoop> all = parts(in);
        return all == null ? ItemStack.EMPTY : make(all);
    }
    /** The cone with these scoops on it. */
    static ItemStack make(List<Scoops.Scoop> all) {
        Scoops s = new Scoops(List.copyOf(all));
        ItemStack out = new ItemStack(AlterraKitchen.ICE_CREAM_STACK.get());
        out.set(AlterraKitchen.SCOOPS.get(), s);
        out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(s.modelCode()));
        out.set(DataComponents.FOOD, new FoodProperties(s.nutrition(), s.nutrition() * 0.18f, false, s.eatSeconds(), Optional.empty(), List.of()));
        return out;
    }

    /** Every cone but the one in the result is given back. */
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput in) {
        NonNullList<ItemStack> left = NonNullList.withSize(in.size(), ItemStack.EMPTY);
        boolean first = true;
        for (int i = 0; i < in.size(); i++) {
            if (in.getItem(i).isEmpty() || snowball(in.getItem(i))) continue;
            if (first) { first = false; continue; }
            left.set(i, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "wafer_cone"))));
        }
        return left;
    }

    @Override public boolean canCraftInDimensions(int w, int h) { return w * h >= 2; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return new ItemStack(AlterraKitchen.ICE_CREAM_STACK.get()); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return AlterraKitchen.ICE_CREAM_STACK_RECIPE.get(); }
}
