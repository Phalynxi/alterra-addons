package dev.phi.alterra.kitchen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import java.util.Optional;

/** A made-up dish: noodles, spaghetti, macaroni, flatbread, a sandwich or a stuffed potato, with whatever was put in it. Its name, colours and food value come from the {@link Dish} on the stack. */
public final class DishItem extends Item {
    private final String noun;
    public final int baseNutrition;

    public DishItem(String noun, int baseNutrition) {
        super(new Item.Properties().stacksTo(16).food(new FoodProperties(baseNutrition, baseNutrition * 0.45f, false, 1.6f, Optional.empty(), List.of())));
        this.noun = noun; this.baseNutrition = baseNutrition;
    }

    /** A name no wider than the taste line under it (about 22 letters): the seasoning's word and up to two fillings if they fit, else fewer ("Salted Beef Burger"). */
    @Override public Component getName(ItemStack stack) {
        Dish d = stack.get(AlterraKitchen.DISH.get());
        if (d == null || d.fillings().isEmpty()) return Component.literal(noun);
        String adj = Dish.adjective(d.seasoning());
        List<String> f = d.fillings().stream().distinct().limit(2).map(id -> plain(new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString())).toList();
        String[] tries = {
            (adj.isEmpty() ? "" : adj + " ") + String.join(" and ", f) + " " + noun,
            (adj.isEmpty() ? "" : adj + " ") + f.get(0) + " " + noun,
            f.get(0) + " " + noun, noun};
        for (String t : tries) if (t.length() <= 22) return Component.literal(t);
        return Component.literal(noun);
    }
    /** "Cooked Beef" is just "Beef" in a dish name. */
    private static String plain(String name) {
        for (String p : new String[] {"Cooked ", "Roasted ", "Baked ", "Boiled ", "Grilled ", "Dried ", "Smoked "}) if (name.startsWith(p)) return name.substring(p.length());
        return name;
    }

    /** The tooltip card: taste, hunger and saturation, and the flavours of the dish. */
    @Override public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        Dish d = stack.get(AlterraKitchen.DISH.get());
        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (d == null || food == null) return java.util.Optional.empty();
        return java.util.Optional.of(new DishCard(d.taste(), food.nutrition(), food.saturation(), 0, 0, d.flavors(), ClientKnowledge.known(stack), d.toxicCount() > 0));
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity who) {
        Dish d = stack.get(AlterraKitchen.DISH.get());
        TasteKnowledge.learn(who, stack);   // before the stack is used up
        ItemStack left = super.finishUsingItem(stack, level, who);
        if (!level.isClientSide && d != null) d.harm(who);
        return left;
    }
}
