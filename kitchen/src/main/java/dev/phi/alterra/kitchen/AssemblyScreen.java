package dev.phi.alterra.kitchen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** The kitchen station's screen: a plate with the food on it as it is built (a live picture), the things on the table in a row beneath it, and a Serve button. Drop a carried item onto the plate to add it; click one in the row to take it back. */
public final class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {
    private static final int PLATE_X = 28, PLATE_Y = 16, PLATE_W = 120, PLATE_H = 62, CHIP_Y = 84, CHIP = 16;
    private final int panel, edge, well, dark, light;
    private Button serve;
    private net.minecraft.client.gui.components.EditBox name;
    private ItemStack lastShown = ItemStack.EMPTY;
    public AssemblyScreen(AssemblyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = AssemblyMenu.WIDTH; imageHeight = AssemblyMenu.HEIGHT; inventoryLabelY = -100;
        switch (menu.layout()) {
            case PREP -> { panel = 0xDEC492; edge = 0x966C3E; well = 0xB09264; dark = 0x785834; light = 0xF0DEB2; }
            case COUNTER -> { panel = 0xD6DAD6; edge = 0x825E28; well = 0xA8ACA8; dark = 0x686C6A; light = 0xF4F6F4; }
            default -> { panel = 0xF6ECE2; edge = 0xBE4650; well = 0xE2CEC8; dark = 0xA87878; light = 0xFFFAF6; }
        }
    }
    @Override protected void init() {
        super.init(); titleLabelX = 8; titleLabelY = -100;
        name = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font, leftPos + 8, topPos + 3, imageWidth - 16, 12, Component.literal("Dish name")));
        name.setMaxLength(32); name.setBordered(true);
        serve = addRenderableWidget(Button.builder(Component.literal("Serve"), b -> { sendName(); press(1); }).bounds(leftPos + 34, topPos + 104, 56, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Take back"), b -> press(2)).bounds(leftPos + 94, topPos + 104, 56, 14).build());
    }
    private void press(int id) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    @Override protected void containerTick() {
        super.containerTick(); serve.active = !menu.preview().isEmpty();
        ItemStack shown = menu.preview();
        if (!ItemStack.isSameItemSameComponents(shown, lastShown)) {   // a different dish on the plate: show its saved name, or the made-up one as a hint
            lastShown = shown.copy();
            if (!name.isFocused()) name.setValue(shown.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? shown.getHoverName().getString() : "");
            name.setHint(Component.literal(shown.isEmpty() ? menu.layout().title : shown.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? "" : shown.getHoverName().getString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        name.active = !shown.isEmpty();
    }
    private void sendName() {
        String typed = name.getValue().strip(), current = menu.preview().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? menu.preview().getHoverName().getString() : "";
        if (!typed.equals(current)) net.neoforged.neoforge.network.PacketDistributor.sendToServer(new NameDish(typed));
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (name.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { sendName(); name.setFocused(false); return true; }
            if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { name.keyPressed(key, scan, mods); return true; }   // typing does not close the screen or press hotbar keys
        }
        return super.keyPressed(key, scan, mods);
    }
    @Override public void removed() { if (name != null && name.isFocused()) sendName(); super.removed(); }

    /** Places on the table that hold something, as (slot number, item). */
    private List<int[]> chips() {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < menu.places(); i++) if (!menu.table(i).isEmpty()) l.add(new int[] {i});
        return l;
    }
    private int chipAt(double mx, double my) {
        List<int[]> chips = chips();
        for (int k = 0; k < chips.size(); k++) {
            int x = leftPos + 8 + k * CHIP, y = topPos + CHIP_Y;
            if (mx >= x && mx < x + CHIP && my >= y && my < y + CHIP) return chips.get(k)[0];
        }
        return -1;
    }
    private boolean onPlate(double mx, double my) { return mx >= leftPos + PLATE_X && mx < leftPos + PLATE_X + PLATE_W && my >= topPos + PLATE_Y && my < topPos + PLATE_Y + PLATE_H; }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (!menu.getCarried().isEmpty() && onPlate(mx, my)) { press(button == 1 ? 11 : 10); return true; }
        int chip = menu.getCarried().isEmpty() ? chipAt(mx, my) : -1;
        if (chip >= 0) { press(100 + chip); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int chip = chipAt(mouseX, mouseY);
        if (chip >= 0 && menu.getCarried().isEmpty()) g.renderTooltip(font, menu.table(chip), mouseX, mouseY);
        else renderTooltip(g, mouseX, mouseY);
    }

    private void rect(GuiGraphics g, int x, int y, int w, int h, int col) { g.fill(x, y, x + w, y + h, 0xFF000000 | col); }
    private void well(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, well); rect(g, x, y, w, 1, dark); rect(g, x, y, 1, h, dark); rect(g, x, y + h - 1, w, 1, light); rect(g, x + w - 1, y, 1, h, light);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        rect(g, x, y, imageWidth, imageHeight, edge); rect(g, x + 3, y + 3, imageWidth - 6, imageHeight - 6, panel);
        // the plate: a rim, a lighter dish, and the food on it
        int px = x + PLATE_X, py = y + PLATE_Y;
        rect(g, px, py, PLATE_W, PLATE_H, dark); rect(g, px + 2, py + 2, PLATE_W - 4, PLATE_H - 4, light); rect(g, px + 6, py + 6, PLATE_W - 12, PLATE_H - 12, 0xFFFFFF & (panel | 0x101010));
        ItemStack shown = menu.preview();
        if (shown.isEmpty()) for (int i = 0; i < menu.places() && shown.isEmpty(); i++) if (!menu.table(i).isEmpty()) { shown = menu.table(i); break; }   // just the base so far
        if (!shown.isEmpty()) {
            var pose = g.pose(); pose.pushPose(); pose.translate(px + PLATE_W / 2f - 28, py + PLATE_H / 2f - 28, 0); pose.scale(3.5f, 3.5f, 1);
            g.renderItem(shown, 0, 0);
            pose.popPose();
        } else {
            String hint = menu.layout() == AssemblyMenu.Layout.STAND ? "Drop an ice cream here" : "Drop a base here";
            g.drawCenteredString(font, hint, px + PLATE_W / 2, py + PLATE_H / 2 - 4, 0xFF000000 | dark);
        }
        // everything on the table, in order
        List<int[]> chips = chips();
        for (int k = 0; k < chips.size(); k++) { well(g, x + 8 + k * CHIP - 0, y + CHIP_Y - 1, CHIP, CHIP + 2); g.renderItem(menu.table(chips.get(k)[0]), x + 8 + k * CHIP, y + CHIP_Y); }
        rect(g, x + 8, y + 121, imageWidth - 16, 1, dark);
        for (var s : menu.slots) if (s.isActive()) well(g, x + s.x - 1, y + s.y - 1, 18, 18);
    }
}
