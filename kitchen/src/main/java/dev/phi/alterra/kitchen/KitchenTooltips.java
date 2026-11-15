package dev.phi.alterra.kitchen;

import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.List;
import java.util.Map;

/**
 * The tooltip of our foods and drinks: an ornate pixel frame (a double border, a gem on each corner, a crest on top and a pendant below, a line under the title)
 * in the colours of how good the food tastes. The pieces and colours come from alterra_kitchen_tooltip.json (made by tools/tooltip_art.py, which also draws a mock-up of it).
 * The colour event gives the background and the position of the tooltip; the frame is drawn right after the screen, over it.
 */
@EventBusSubscriber(modid = AlterraKitchen.ID, value = Dist.CLIENT)
public final class KitchenTooltips {
    private KitchenTooltips() {}
    private record Tier(int min, int main, int dark, int light, int gem, int bg, int bg2) {}
    private static List<String> top, topHigh, bottom;
    private static Tier unknown;
    private static List<Tier> tiers;
    private static int[] box;   // x, y, width, height of the text, and the taste

    @SuppressWarnings("unchecked")
    private static void load() {
        if (tiers != null) return;
        Map<String, Object> d = AlterraKitchen.read("alterra_kitchen_tooltip.json", new TypeToken<Map<String, Object>>() {});
        top = (List<String>) d.get("top"); topHigh = (List<String>) d.get("topHigh"); bottom = (List<String>) d.get("bottom");
        Map<String, Object> u = (Map<String, Object>) d.get("unknown");
        unknown = new Tier(-1, hex(u, "main"), hex(u, "dark"), hex(u, "light"), hex(u, "gem"), hex(u, "bg"), hex(u, "bg2"));
        tiers = ((List<Map<String, Object>>) d.get("tiers")).stream().map(t -> new Tier(((Number) t.get("min")).intValue(), hex(t, "main"), hex(t, "dark"), hex(t, "light"), hex(t, "gem"), hex(t, "bg"), hex(t, "bg2"))).toList();
    }
    private static int hex(Map<String, Object> t, String k) { return Integer.parseInt((String) t.get(k), 16); }
    private static Tier tier(int taste) {
        if (taste < 0) return unknown;   // not tasted yet: a plain muted frame that gives nothing away
        Tier r = tiers.get(0);
        for (Tier t : tiers) if (t.min <= taste) r = t;
        return r;
    }

    /** The journal tells how many foods it has notes on. */
    @SubscribeEvent
    public static void journalNotes(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) {
        int n = TasteKnowledge.notes(e.getItemStack());
        if (n > 0) e.getToolTip().add(net.minecraft.network.chat.Component.literal("Tasted: " + n).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void colors(RenderTooltipEvent.Color e) {
        ItemStack st = e.getItemStack();
        int taste = st.isEmpty() ? -1 : AlterraKitchen.tasteOf(st);
        if (taste < 0) return;
        if (!ClientKnowledge.known(st)) taste = -2;   // the frame must not give the taste away either
        load();
        Tier t = tier(taste);
        e.setBackgroundStart(0xF0000000 | t.bg); e.setBackgroundEnd(0xF0000000 | t.bg2);
        e.setBorderStart(0x00000000); e.setBorderEnd(0x00000000);   // the frame is drawn afterwards
        int w = 0, h = 0;
        for (ClientTooltipComponent comp : e.getComponents()) { w = Math.max(w, comp.getWidth(e.getFont())); h += comp.getHeight(); }
        if (e.getComponents().size() > 1) h += 2;
        box = new int[] {e.getX(), e.getY(), w, h, taste};
    }

    private static int rgb(int c) { return 0xFF000000 | c; }
    private static int shade(int c, float f) { return (int) ((c >> 16 & 255) * f) << 16 | (int) ((c >> 8 & 255) * f) << 8 | (int) ((c & 255) * f); }
    private static int hue(float h, float s) { return java.awt.Color.HSBtoRGB(h - (float) Math.floor(h), s, 1f) & 0xFFFFFF; }
    /** The point at distance p along the frame's outline, going clockwise from the top left corner. */
    private static int[] along(int p, int left, int top, int right, int bottom) {
        int w = right - left, h = bottom - top;
        if (p < w) return new int[] {left + p, top};
        p -= w; if (p < h) return new int[] {right, top + p};
        p -= h; if (p < w) return new int[] {right - p, bottom};
        p -= w; return new int[] {left, bottom - p};
    }

    @SubscribeEvent
    public static void frame(ScreenEvent.Render.Post e) {
        int[] b = box; box = null;
        if (b == null || tiers == null) return;
        Tier t = tier(b[4]);
        boolean divine = t.min >= 120, high = t.min >= 80 && t.min < 1000;
        long ms = net.minecraft.Util.getMillis();
        int tMain = t.main, tLight = t.light, tGem = t.gem, tDark = t.dark;
        if (divine) {   // the colours of the frame move through the rainbow
            float h = (ms % 6000) / 6000f;
            tMain = hue(h, 0.5f); tLight = hue(h + 0.03f, 0.15f); tGem = hue(h + 0.015f, 0.32f); tDark = shade(tMain, 0.35f);   // the gems stay in the frame's own colour, only paler
        }
        GuiGraphics g = e.getGuiGraphics();
        int main = rgb(tMain), dark = rgb(tDark), light = rgb(tLight), gem = rgb(tGem), gemDark = rgb(shade(tGem, 0.7f));
        int pulse = 0x40 + (int) (0x30 * Math.sin(ms / (divine ? 250.0 : 600.0)));   // the inner line breathes
        int left = b[0] - 4, topY = b[1] - 4, right = b[0] + b[2] + 3, bottomY = b[1] + b[3] + 3;
        g.pose().pushPose(); g.pose().translate(0, 0, 450);
        int shaded = rgb(shade(tMain, 0.72f));
        g.fill(left, topY, right + 1, topY + 1, main); g.fill(left, topY + 1, left + 1, bottomY + 1, main);   // a lit top and left edge, a shaded bottom and right
        g.fill(left + 1, bottomY, right + 1, bottomY + 1, shaded); g.fill(right, topY + 1, right + 1, bottomY, shaded);
        outline(g, left + 1, topY + 1, right - 1, bottomY - 1, dark);
        g.fill(b[0], b[1] + 10, b[0] + b[2], b[1] + 11, 0x6E000000 | tMain);   // the line under the title
        // a light runs round the frame, with a fading tail (faster and longer for the better foods)
        int perimeter = 2 * (right - left) + 2 * (bottomY - topY), tail = high ? 14 : 8, head = (int) ((ms / (divine ? 14 : high ? 22 : 34)) % perimeter);
        for (int i = 0; i < tail; i++) {
            int[] pt = along(Math.floorMod(head - i, perimeter), left, topY, right, bottomY);
            g.fill(pt[0], pt[1], pt[0] + 1, pt[1] + 1, ((int) (255 * (1f - i / (float) tail)) << 24) | (tLight & 0xFFFFFF));
        }
        int[][] corners = {{left, topY, 1, 1}, {right, topY, -1, 1}, {left, bottomY, 1, -1}, {right, bottomY, -1, -1}};
        for (int k = 0; k < 4; k++) {
            int[] c = corners[k];
            g.fill(c[0] - 1, c[1] - 1, c[0] + 2, c[1] + 2, gem);
            g.fill(c[0] - 1, c[1] - 1, c[0], c[1], light);   // the gem catches light at its top left and is darker at the bottom right
            g.fill(c[0] + 1, c[1] + 1, c[0] + 2, c[1] + 2, gemDark);
            for (int q = 2; q < 6; q++) { g.fill(c[0] + c[2] * q, c[1], c[0] + c[2] * q + 1, c[1] + 1, light); g.fill(c[0], c[1] + c[3] * q, c[0] + 1, c[1] + c[3] * q + 1, light); }
            double phase = ((ms / (divine ? 500.0 : 1100.0)) + k * 0.27) % 1.0;
            if (phase < 0.14) {   // a sparkle: a white cross over the gem
                g.fill(c[0] - 2, c[1], c[0] + 3, c[1] + 1, 0xFFFFFFFF); g.fill(c[0], c[1] - 2, c[0] + 1, c[1] + 3, 0xFFFFFFFF);
            }
        }
        List<String> crest = high ? topHigh : top;
        piece(g, crest, (left + right) / 2 - crest.get(0).length() / 2, topY - (crest.size() - 1), tMain, tLight, tDark, tGem);
        piece(g, bottom, (left + right) / 2 - bottom.get(0).length() / 2, bottomY, tMain, tLight, tDark, tGem);
        if (high && (ms / 700) % 3 == 0) {   // the crest's gem twinkles now and then
            int cx = (left + right) / 2, cy = topY - (crest.size() - 1);
            g.fill(cx - 1, cy, cx + 2, cy + 1, 0xFFFFFFFF); g.fill(cx, cy - 1, cx + 1, cy + 2, 0xFFFFFFFF);
        }
        g.pose().popPose();
    }

    private static void outline(GuiGraphics g, int l, int t, int r, int b, int c) {
        g.fill(l, t, r + 1, t + 1, c); g.fill(l, b, r + 1, b + 1, c); g.fill(l, t + 1, l + 1, b, c); g.fill(r, t + 1, r + 1, b, c);
    }
    private static void piece(GuiGraphics g, List<String> rows, int ox, int oy, int main, int light, int dark, int gem) {
        for (int r = 0; r < rows.size(); r++) for (int c = 0; c < rows.get(r).length(); c++) {
            int col = switch (rows.get(r).charAt(c)) { case 'M' -> main; case 'L' -> light; case 'D' -> dark; case 'G' -> gem; default -> -1; };
            if (col != -1) g.fill(ox + c, oy + r, ox + c + 1, oy + r + 1, 0xFF000000 | col);
        }
    }
}
