package dev.phi.alterra.kitchen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Anyone's own dish: one base (cooked noodles, spaghetti or macaroni, flatbread, bread or a baked potato), one to six fillings (anything edible)
 * and optionally one seasoning and one sauce, in any arrangement. The same ingredients always make the same dish; different ones make a different one.
 */
public record DishRecipe(CraftingBookCategory category) implements CraftingRecipe {
    public static final TagKey<Item> SAUCES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "sauces"));
    public static final TagKey<Item> UNCOOKED = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "uncooked"));
    public static final TagKey<Item> SEASONINGS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "seasonings"));
    private static final Map<String, String> BASES = new java.util.HashMap<>();
    static {
        BASES.put("alterra_kitchen:cooked_noodles", "noodles");
        BASES.put("alterra_kitchen:cooked_spaghetti", "spaghetti");
        BASES.put("alterra_kitchen:cooked_macaroni", "macaroni");
        BASES.put("alterra_kitchen:flatbread", "flatbread");
        BASES.put("alterra_kitchen:bun", "burger");
        BASES.put("minecraft:bread", "burger");
        BASES.put("minecraft:baked_potato", "stuffed_potato");
        BASES.put("alterra_kitchen:tortilla", "wrap");
        BASES.put("alterra_kitchen:pastry_crust", "pie");
        BASES.put("farmersdelight:pie_crust", "pie");
        BASES.put("farmersdelight:cooked_rice", "rice_bowl");
        BASES.put("minecraft:stick", "skewer");
        BASES.put("alterra_kitchen:dumpling_wrapper", "dumplings"); BASES.put("farmersdelight:cabbage_leaf", "rolls"); BASES.put("minecraft:dried_kelp", "sushi");
    }

    private record Parts(String base, List<ResourceLocation> fillings, ResourceLocation seasoning, ResourceLocation sauce) {}

    private static final List<String> BASE_ORDER = List.of("alterra_kitchen:cooked_noodles", "alterra_kitchen:cooked_spaghetti", "alterra_kitchen:cooked_macaroni", "alterra_kitchen:flatbread", "alterra_kitchen:bun", "minecraft:bread", "minecraft:baked_potato", "alterra_kitchen:tortilla", "alterra_kitchen:pastry_crust", "farmersdelight:pie_crust", "farmersdelight:cooked_rice", "alterra_kitchen:dumpling_wrapper", "farmersdelight:cabbage_leaf", "minecraft:dried_kelp", "minecraft:stick");

    private static Parts parts(CraftingInput in) {
        // the base is the first of the list that is in the grid (so bread or a potato can still be a filling next to pasta)
        String baseId = null;
        for (String b : BASE_ORDER) {
            for (ItemStack st : in.items()) if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(b)) { baseId = b; break; }
            if (baseId != null) break;
        }
        if (baseId == null) return null;
        boolean baseTaken = false; ResourceLocation seasoning = Dish.NONE, sauce = Dish.NONE; List<ResourceLocation> fillings = new ArrayList<>();
        for (ItemStack st : in.items()) {
            if (st.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
            if (st.getItem() instanceof DishItem) return null;
            if (!baseTaken && id.toString().equals(baseId)) { baseTaken = true; continue; }
            if (Dish.isUncooked(id) || st.is(UNCOOKED)) return null;   // nothing raw goes into a dish: cook it first
            if (st.is(SAUCES)) { if (!sauce.equals(Dish.NONE)) return null; sauce = id; continue; }
            if (st.is(SEASONINGS)) { if (!seasoning.equals(Dish.NONE)) return null; seasoning = id; continue; }
            if (st.get(DataComponents.FOOD) != null) { fillings.add(id); continue; }
            return null;
        }
        if (fillings.isEmpty() || fillings.size() > 6) return null;
        int tier = Stations.tier();   // a prep table makes simple dishes (up to three fillings, no sauce); the kitchen counter all of them
        if (tier < 1 || (tier < 2 && (fillings.size() > 3 || !sauce.equals(Dish.NONE)))) return null;
        return new Parts(BASES.get(baseId), fillings, seasoning, sauce);
    }

    /** Cooked pasta, flatbread, bread or a baked potato: the things a dish is built on. */
    public static boolean isBase(ItemStack st) { return BASES.containsKey(BuiltInRegistries.ITEM.getKey(st.getItem()).toString()); }
    /** Anything cooked and edible that is not a sauce or seasoning, or itself a dish. */
    public static boolean isFilling(ItemStack st) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
        return st.get(DataComponents.FOOD) != null && !(st.getItem() instanceof DishItem) && !Dish.isUncooked(id) && !st.is(UNCOOKED) && !st.is(SAUCES) && !st.is(SEASONINGS);
    }

    @Override public boolean matches(CraftingInput in, Level level) { return parts(in) != null; }

    @Override public ItemStack assemble(CraftingInput in, HolderLookup.Provider registries) {
        Parts p = parts(in);
        if (p == null) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(AlterraKitchen.DISH_ITEMS.get(p.base()).get());
        Dish dish = new Dish(List.copyOf(p.fillings()), p.seasoning(), p.sauce());
        out.set(AlterraKitchen.DISH.get(), dish);
        out.set(DataComponents.FOOD, dish.food(((DishItem) out.getItem()).baseNutrition));
        return out;
    }

    @Override public boolean canCraftInDimensions(int w, int h) { return w * h >= 2; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return new ItemStack(AlterraKitchen.DISH_ITEMS.get("noodles").get()); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return AlterraKitchen.DISH_RECIPE.get(); }
}
