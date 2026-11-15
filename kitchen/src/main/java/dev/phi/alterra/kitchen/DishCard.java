package dev.phi.alterra.kitchen;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** What the tooltip card of a food, drink or dish shows (drawn by ClientDishCard): the taste, the hunger and saturation, a drink's thirst and temperature, and a dish's flavours (null if it has none); known: you have eaten it, so the secrets show (otherwise the taste, saturation, flavours and poison are hidden); toxic: it has poison in it. */
public record DishCard(int taste, int hunger, float saturation, int hydration, int temperature, int[] flavors, boolean known, boolean toxic) implements TooltipComponent {}
