package dev.phi.alterra.kitchen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The tooltip card: the taste line, then a row of icons with numbers (hunger, saturation, and for drinks thirst and warm or cool), then for dishes the two strongest flavours. No row is wider than the taste line. */
public final class ClientDishCard implements ClientTooltipComponent {
    private static final String[] FLAVORS = {"sweet", "savory", "spicy", "fresh", "rich"};
    private static final int[] FLAVOR_COLOURS = {0xFF55FF, 0xFFAA00, 0xFF5555, 0x55FFFF, 0xFFFF55};
    private final DishCard card;
    public ClientDishCard(DishCard card) { this.card = card; }

    /** The taste line, or question marks until you have eaten it. */
    private net.minecraft.network.chat.Component line() { return card.known() ? Dish.tasteLine(card.taste()) : Component.literal("Taste: ?????").withStyle(net.minecraft.ChatFormatting.DARK_GRAY); }
    private static ResourceLocation icon(String name) { return ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "stat/" + name); }
    private static final int GAP = 5, FLAVOR_GAP = 8;
    private java.util.List<String[]> stats() {   // icon, number
        var l = new java.util.ArrayList<String[]>();
        l.add(new String[] {"hunger", String.valueOf(card.hunger())});
        l.add(new String[] {"satiety", card.known() ? String.format(java.util.Locale.ROOT, "%.1f", card.saturation()) : "?"});
        if (card.hydration() > 0) l.add(new String[] {"thirst", String.valueOf(card.hydration())});
        if (card.known() && card.toxic()) l.add(new String[] {"toxic", ""});
        if (card.temperature() != 0) l.add(new String[] {card.temperature() > 0 ? "warm" : "cool", ""});
        return l;
    }
    private java.util.List<int[]> flavors() {   // flavour index, value: the two strongest
        var l = new java.util.ArrayList<int[]>();
        if (card.flavors() == null || !card.known()) return l;
        Integer[] order = {0, 1, 2, 3, 4};
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(card.flavors()[b], card.flavors()[a]));
        for (int i : order) if (card.flavors()[i] > 0 && l.size() < 2) l.add(new int[] {i, card.flavors()[i]});
        return l;
    }
    private int statsWidth(Font font) { int s = 0; for (String[] st : stats()) s += 9 + (st[1].isEmpty() ? 0 : 1 + font.width(st[1])) + GAP; return s - GAP; }
    private int flavorWidth(Font font, java.util.List<int[]> fl) { int f = 0; for (int[] x : fl) f += 10 + font.width(String.valueOf(x[1])) + GAP; return f - GAP; }
    /** How many of the flavours fit on the stats row without making the card wider than the taste line (the rest go on a row of their own). */
    private int inline(Font font) {
        var fl = flavors();
        for (int k = fl.size(); k > 0; k--) if (statsWidth(font) + FLAVOR_GAP + flavorWidth(font, fl.subList(0, k)) <= font.width(line())) return k;
        return 0;
    }
    private boolean ownRow(Font font) { return !flavors().isEmpty() && inline(font) == 0; }

    // the card's height is asked without a font: use the current one
    @Override public int getHeight() { return ((ownRow(net.minecraft.client.Minecraft.getInstance().font) ? 3 : 2) + (card.known() ? 0 : 1)) * 11; }

    @Override public int getWidth(Font font) {
        int w = Math.max(font.width(line()), statsWidth(font));
        if (inline(font) > 0) w = Math.max(w, statsWidth(font) + FLAVOR_GAP + flavorWidth(font, flavors().subList(0, inline(font))));
        else if (ownRow(font)) w = Math.max(w, flavorWidth(font, flavors()));
        return w;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        if (!card.known()) { g.drawString(font, Component.literal("Unidentified").withStyle(net.minecraft.ChatFormatting.ITALIC, net.minecraft.ChatFormatting.LIGHT_PURPLE), x, y, 0xFFFFFF, true); y += 11; }
        g.drawString(font, line(), x, y, 0xFFFFFF, true);
        y += 11; int cx = x;
        for (String[] st : stats()) {
            g.blitSprite(icon(st[0]), cx, y, 9, 9); cx += 9;
            if (!st[1].isEmpty()) { g.drawString(font, st[1], cx + 1, y + 1, 0xAAAAAA, true); cx += 1 + font.width(st[1]); }
            cx += GAP;
        }
        var fl = flavors(); int k = inline(font);
        if (k > 0) cx += FLAVOR_GAP - GAP; else if (ownRow(font)) { y += 11; cx = x; k = fl.size(); }
        for (int i = 0; i < k; i++) {
            int[] f = fl.get(i);
            g.blitSprite(icon(FLAVORS[f[0]]), cx, y, 9, 9);
            g.drawString(font, String.valueOf(f[1]), cx + 10, y + 1, FLAVOR_COLOURS[f[0]], true);
            cx += 10 + font.width(String.valueOf(f[1])) + GAP;
        }
    }
}
