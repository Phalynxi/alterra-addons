package dev.phi.alterra.kitchen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A poisonous plant part (nightshade berries, hemlock or oleander leaves, a deathcap, poison fern fronds): it looks edible, and you only find out what it does by eating it. */
public final class PoisonItem extends Item {
    private final String path, title; private final int taste;
    public PoisonItem(String path, String title, int taste) {
        super(new Item.Properties().food(new FoodProperties(1, 0.1f, true, 1.2f, java.util.Optional.empty(), java.util.List.of())));
        this.path = path; this.title = title; this.taste = taste;
    }
    @Override public Component getName(ItemStack st) { return Component.literal(title); }
    public int taste() { return taste; }
    @Override public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack st) {
        return java.util.Optional.of(new DishCard(taste, 1, 0.1f, 0, 0, null, ClientKnowledge.known(st), true));
    }
    @Override public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity who) {
        TasteKnowledge.learn(who, st);
        if (!level.isClientSide) Poisons.apply(path, who, 0);
        return super.finishUsingItem(st, level, who);
    }
}
