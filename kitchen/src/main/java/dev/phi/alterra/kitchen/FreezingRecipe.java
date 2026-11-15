package dev.phi.alterra.kitchen;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** What the ice cream machine does: one item in, one out, like a furnace recipe (it runs on snow and ice instead of fuel). */
public class FreezingRecipe extends AbstractCookingRecipe {
    public FreezingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int time) {
        super(AlterraKitchen.FREEZING.get(), group, category, ingredient, result, experience, time);
    }
    @Override public ItemStack getToastSymbol() { return new ItemStack(AlterraKitchen.ICE_MACHINE.get()); }
    @Override public RecipeSerializer<?> getSerializer() { return AlterraKitchen.FREEZING_SERIALIZER.get(); }
}
