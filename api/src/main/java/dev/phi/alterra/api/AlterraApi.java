package dev.phi.alterra.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * What addons can hook into. Call these from your mod constructor; Alterra uses them once every mod has loaded.
 * Creative tab sections, settings pages. Guide pages need no call: put a file named {@code <modid>_guide.json} in your jar (docs/field-guide.md).
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

    // ---------------------------------------------------------------- compat switches
    private static final java.util.Map<String, java.util.function.BooleanSupplier> COMPAT = new java.util.concurrent.ConcurrentHashMap<>();
    /** Alterra registers its compat switches here (config values). */
    public static void registerCompat(String key, java.util.function.BooleanSupplier value) { COMPAT.put(key, value); }
    /** Whether a compat feature is switched on in Alterra's settings (true when the key is not known). Addons use this to follow the same switches. */
    public static boolean compat(String key) { var v = COMPAT.get(key); return v == null || v.getAsBoolean(); }

    private static final java.util.Map<String, java.util.function.IntSupplier> NUMBERS = new java.util.concurrent.ConcurrentHashMap<>();
    public static void registerNumber(String key, java.util.function.IntSupplier value) { NUMBERS.put(key, value); }
    /** A number from Alterra's settings (the given default when the key is not known). */
    public static int number(String key, int fallback) { var v = NUMBERS.get(key); return v == null ? fallback : v.getAsInt(); }

    // ---------------------------------------------------------------- wind
    /** Where the wind comes from (the Weather addon sets it). */
    public interface WindProvider { net.minecraft.world.phys.Vec3 wind(net.minecraft.world.level.Level level, double x, double z); }
    private static volatile WindProvider WIND;
    public static void setWindProvider(WindProvider provider) { WIND = provider; }
    /** The wind at a place: a horizontal vector pointing the way it blows, its length the speed (0 calm, 1 a hard storm wind, up to 1.5). Zero when no weather addon is installed. */
    public static net.minecraft.world.phys.Vec3 wind(net.minecraft.world.level.Level level, double x, double z) { var p = WIND; return p == null ? net.minecraft.world.phys.Vec3.ZERO : p.wind(level, x, z); }

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

    // ---------------------------------------------------------------- rivers
    /** Answers where Alterra's rivers run (the core sets it). */
    public interface RiverProvider { float surface(net.minecraft.server.level.ServerLevel level, int x, int z); }
    private static volatile RiverProvider RIVERS;
    public static void setRiverProvider(RiverProvider provider) { RIVERS = provider; }
    /** The water surface (a height) of an Alterra river over a column, or NaN when there is no river there or no Alterra. */
    public static float riverSurface(net.minecraft.server.level.ServerLevel level, int x, int z) { var p = RIVERS; return p == null ? Float.NaN : p.surface(level, x, z); }
    /** Whether an Alterra river runs over the column. */
    public static boolean isRiver(net.minecraft.server.level.ServerLevel level, int x, int z) { return !Float.isNaN(riverSurface(level, x, z)); }

    // ---------------------------------------------------------------- biomes
    /** One of Alterra's biomes: its id (without the namespace), display name, dimension (overworld, nether, end) and whether it is switched on in the settings. */
    public record BiomeInfo(String id, String name, String dimension, boolean enabled) {}
    public interface BiomeProvider { List<BiomeInfo> biomes(); }
    private static volatile BiomeProvider BIOMES;
    public static void setBiomeProvider(BiomeProvider provider) { BIOMES = provider; }
    /** Every biome Alterra adds (empty without Alterra). */
    public static List<BiomeInfo> biomes() { var p = BIOMES; return p == null ? List.of() : p.biomes(); }
    /** Whether the Alterra biome with this id is switched on (true when it is not known). */
    public static boolean biomeEnabled(String id) { for (var b : biomes()) if (b.id().equals(id)) return b.enabled(); return true; }

    // ---------------------------------------------------------------- loading screen
    private static final List<String> TIPS = new CopyOnWriteArrayList<>();
    /** A tip for the loading screen, shown among Alterra's own. */
    public static void addLoadingTip(String tip) { TIPS.add(tip); }
    public static List<String> loadingTips() { return List.copyOf(TIPS); }

    // ---------------------------------------------------------------- version
    /** The version of this API (bumped when something is added): an addon can check it before using newer calls. 2: rivers, biomes, loading tips. */
    public static int apiVersion() { return 2; }
}
