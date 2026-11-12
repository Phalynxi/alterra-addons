package dev.phi.alterra.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * What an Alterra addon can hook into. Call these from the addon's mod constructor (they only collect; Alterra applies them later, so
 * the order mods are constructed in does not matter).
 *
 * <ul>
 * <li>Creative tab: a section of its own in the Alterra tab, or items added to the "Boats &amp; More" section.</li>
 * <li>Settings: a page of options in Alterra's settings screen.</li>
 * <li>Field guide: no call needed - put a file named {@code <modid>_guide.json} in the root of the addon's jar (chapters, entries and
 *     items to watch; see docs/field-guide.md). Alterra merges every such file into the book.</li>
 * </ul>
 */
public final class AlterraApi {
    private AlterraApi() {}
    public static final String MOD_ID = "alterra";

    // ---------------------------------------------------------------- creative tab
    /** A section of the Alterra creative tab. Its banner is the texture {@code <namespace>:textures/gui/fancy_tab_section/<path>.png}
     *  (162x288: 16 frames of 162x18) and its title the translation key {@code section.<namespace>.<path>}; ship both in the addon. */
    public record TabSection(ResourceLocation id, int textColor, Supplier<List<ItemStack>> items) {}
    private static final List<TabSection> SECTIONS = new CopyOnWriteArrayList<>();
    private static final List<Supplier<List<ItemStack>>> EXTRA_ITEMS = new CopyOnWriteArrayList<>();

    public static void addTabSection(ResourceLocation id, int textColor, Supplier<List<ItemStack>> items) { SECTIONS.add(new TabSection(id, textColor, items)); }
    /** Items for the tab's last section ("Boats &amp; More"), for addons too small for a section of their own. */
    public static void addTabItems(Supplier<List<ItemStack>> items) { EXTRA_ITEMS.add(items); }
    public static List<TabSection> tabSections() { return List.copyOf(SECTIONS); }
    public static List<Supplier<List<ItemStack>>> extraTabItems() { return List.copyOf(EXTRA_ITEMS); }

    // ---------------------------------------------------------------- settings
    public enum Kind { BOOL, DOUBLE, INT }
    /** One option on a settings page. {@code newChunksOnly}: shows a "New chunks" tag (the option only affects land generated later). */
    public record Option(String name, String description, boolean newChunksOnly, Kind kind, ModConfigSpec.ConfigValue<?> value, double min, double max, double step, String unit) {
        public static Option bool(String name, String description, ModConfigSpec.BooleanValue value) { return new Option(name, description, false, Kind.BOOL, value, 0, 1, 1, ""); }
        public static Option number(String name, String description, ModConfigSpec.DoubleValue value, double min, double max, double step, String unit) {
            return new Option(name, description, false, Kind.DOUBLE, value, min, max, step, unit);
        }
        public static Option integer(String name, String description, ModConfigSpec.IntValue value, int min, int max, String prefix) {
            return new Option(name, description, false, Kind.INT, value, min, max, Math.max(1, (max - min) / 40), prefix);
        }
    }
    /** A page in Alterra's settings screen. The values belong to the addon's own config spec; Alterra saves them when the screen closes. */
    public record Category(String name, String description, Supplier<ItemStack> icon, List<Option> options) {}
    private static final List<Category> CATEGORIES = new CopyOnWriteArrayList<>();

    public static void addSettingsCategory(String name, String description, Supplier<ItemStack> icon, List<Option> options) {
        CATEGORIES.add(new Category(name, description, icon, List.copyOf(options)));
    }
    public static List<Category> settingsCategories() { return List.copyOf(CATEGORIES); }
}
