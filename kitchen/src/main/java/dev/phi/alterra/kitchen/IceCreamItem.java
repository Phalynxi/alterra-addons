package dev.phi.alterra.kitchen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** A tall cone with several scoops: its name, food value and taste come from the {@link Scoops} on the stack. */
public final class IceCreamItem extends Item {
    public IceCreamItem() { super(new Item.Properties().stacksTo(16)); }

    @Override public Component getName(ItemStack st) {
        Scoops s = st.get(AlterraKitchen.SCOOPS.get());
        return Component.literal(s == null ? "Stacked Ice Cream" : s.name());
    }
    @Override public Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack st) {
        Scoops s = st.get(AlterraKitchen.SCOOPS.get()); var food = st.get(net.minecraft.core.component.DataComponents.FOOD);
        if (s == null || food == null) return Optional.empty();
        return Optional.of(new DishCard(s.taste(), food.nutrition(), food.saturation(), LsoStats.hydration("ice_cream_stack"), LsoStats.temperature("ice_cream_stack"), null, ClientKnowledge.known(st), false));
    }
    @Override public ItemStack finishUsingItem(ItemStack st, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity who) {
        TasteKnowledge.learn(who, st);
        return super.finishUsingItem(st, level, who);
    }
}
