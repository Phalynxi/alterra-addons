package dev.phi.alterra.kitchen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Any bottled drinks together in the crafting grid (two to four: juices, wines, mixes, secret drinks, mixed drinks) make a mixed drink, a bottle for each bottle put in,
 * whenever no written recipe takes them. A mixed drink put in brings all the drinks it was made of, so mixes of mixes keep growing (up to eight drinks).
 */
public final class DrinkMixRecipe extends CustomRecipe {
    public DrinkMixRecipe(CraftingBookCategory c) { super(c); }

    private static String drink(ItemStack st) {
        var id = BuiltInRegistries.ITEM.getKey(st.getItem());
        if (!id.getNamespace().equals(AlterraKitchen.ID)) return null;
        return AlterraKitchen.JUICES.stream().anyMatch(j -> j.id().equals(id.getPath())) ? id.getPath() : null;
    }

    private record Mix(List<String> drinks, List<String> herbs, int bottles) {}
    private static List<String> drinks(CraftingInput in) { Mix m = mix(in); return m == null ? null : m.drinks(); }
    private static Mix mix(CraftingInput in) {
        List<String> all = new ArrayList<>(), herbs = new ArrayList<>(); int bottles = 0;
        for (int i = 0; i < in.size(); i++) {
            ItemStack st = in.getItem(i);
            if (st.isEmpty()) continue;
            if (MixedDrink.isHerb(st)) { herbs.add(MixedDrink.herbId(st)); continue; }   // herbs go in too: colour, or an effect (or poison)
            bottles++;
            if (st.is(AlterraKitchen.MIXED_DRINK.get())) { MixedDrink m = st.get(AlterraKitchen.MIXED.get()); if (m == null) return null; for (String d : m.drinks()) if (!all.contains(d)) all.add(d); herbs.addAll(m.herbs()); }
            else { String d = drink(st); if (d == null) return null; if (!all.contains(d)) all.add(d); }
        }
        boolean ok = bottles >= 1 && bottles <= 4 && (all.size() >= 2 || (all.size() >= 1 && !herbs.isEmpty())) && herbs.size() <= 3;   // two drinks, or one drink with herbs
        return ok ? new Mix(all.size() > 8 ? all.subList(0, 8) : all, herbs, bottles) : null;
    }

    @Override public boolean matches(CraftingInput in, Level level) {
        if (drinks(in) == null) return false;
        for (var r : level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING))   // a written recipe for these comes first
            if (r.value() != this && !(r.value() instanceof DrinkMixRecipe) && r.value().matches(in, level)) return false;
        return true;
    }

    @Override public ItemStack assemble(CraftingInput in, HolderLookup.Provider p) {
        Mix m = mix(in);
        ItemStack out = new ItemStack(AlterraKitchen.MIXED_DRINK.get(), m.bottles());
        out.set(AlterraKitchen.MIXED.get(), MixedDrink.of(m.drinks(), m.herbs()));
        return out;
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput in) { return NonNullList.withSize(in.size(), ItemStack.EMPTY); }   // the bottles carry the drink
    @Override public boolean canCraftInDimensions(int w, int h) { return w * h >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return AlterraKitchen.DRINK_MIX.get(); }

    static RecipeSerializer<DrinkMixRecipe> serializer() { return new SimpleCraftingRecipeSerializer<>(DrinkMixRecipe::new); }
}
