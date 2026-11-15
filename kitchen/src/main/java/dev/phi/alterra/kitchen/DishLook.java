package dev.phi.alterra.kitchen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paints the picture of a made-up dish when it is first shown (and keeps it): the parts of the dish (cut out of Farmer's Delight style sprites) are filled with
 * the real colours of the ingredient that belongs to them, so beef looks like beef and salmon like salmon, with a pattern for the kind of food (fibres, flakes,
 * segments, speckles) and a little noise that is different for every dish. The seasoning is scattered on top as flecks.
 */
public final class DishLook {
    private DishLook() {}

    private static final Map<String, ResourceLocation> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() <= 300) return false;
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            return true;
        }
    };
    private static final Map<String, NativeImage> LAYERS = new HashMap<>();
    private static final Map<String, int[]> RAMPS = new HashMap<>();   // ingredients -> {dark, mid, light} as 0xRRGGBB

    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(tm::release); CACHE.clear();
        LAYERS.values().forEach(NativeImage::close); LAYERS.clear(); RAMPS.clear();
    }

    public static ResourceLocation textureFor(ItemStack stack) {
        String kind = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        Dish dish = stack.get(AlterraKitchen.DISH.get());
        String key = kind + "/" + (dish == null ? "plain" : Integer.toHexString(dish.hashCode()));
        ResourceLocation have = CACHE.get(key);
        if (have != null) return have;
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "dish/" + key);
        NativeImage img = paint(kind, dish);
        Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(img));
        CACHE.put(key, tex);
        return tex;
    }

    // ------------------------------------------------------------ painting
    private static NativeImage layer(String name) {
        return LAYERS.computeIfAbsent(name, n -> {
            try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "textures/item/" + n + ".png")).open()) { return NativeImage.read(in); }
            catch (Exception e) { return new NativeImage(16, 16, true); }
        });
    }
    private static int abgr(int rgb) { return 0xFF000000 | (rgb & 255) << 16 | (rgb >> 8 & 255) << 8 | (rgb >> 16 & 255); }
    private static int mul(int grey, int rgb) {
        return abgr(((rgb >> 16 & 255) * grey / 255) << 16 | ((rgb >> 8 & 255) * grey / 255) << 8 | ((rgb & 255) * grey / 255));
    }
    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177; h = (h ^ (h >>> 13)) * 1274126177; return h ^ (h >>> 16);
    }

    private static NativeImage paint(String kind, Dish dish) {
        NativeImage out = new NativeImage(16, 16, true);
        var meta = AlterraKitchen.DISH_META.get(kind);
        @SuppressWarnings("unchecked") List<String> names = meta == null ? List.of() : (List<String>) meta.get("zoneNames");
        @SuppressWarnings("unchecked") List<Number> defaults = meta == null ? List.of() : (List<Number>) meta.get("defaults");
        NativeImage base = layer(kind);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if ((base.getPixelRGBA(x, y) >>> 24) > 0) out.setPixelRGBA(x, y, base.getPixelRGBA(x, y));
        List<List<ResourceLocation>> sources = dish == null ? null : dish.zoneSources(kind, names.size());
        int seed = dish == null ? 1 : dish.hashCode();
        for (int z = 0; z < names.size(); z++) {
            NativeImage mask = layer(kind + "_" + names.get(z));
            List<ResourceLocation> src = sources == null ? List.of() : sources.get(z);
            int[] ramp = src.isEmpty() ? null : ramp(src);
            String pattern = src.isEmpty() ? "smooth" : Dish.pattern(src.get(0));
            int zseed = seed * 31 + z * 7919;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int p = mask.getPixelRGBA(x, y);
                if ((p >>> 24) == 0) continue;
                int g = p & 255;
                if (ramp == null) out.setPixelRGBA(x, y, mul(g, defaults.get(z).intValue()));
                else out.setPixelRGBA(x, y, abgr(sample(ramp, Math.max(0f, Math.min(1f, g / 255f + modulation(pattern, x, y, zseed))))));
            }
        }
        if (dish != null && !dish.seasoning().equals(Dish.NONE)) {   // flecks of the seasoning, a different handful on every dish
            NativeImage fl = layer(kind + "_flecks"); int col = dish.seasoningColor();
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                if ((fl.getPixelRGBA(x, y) >>> 24) > 0 && (hash(x, y, seed) & 255) < 190) out.setPixelRGBA(x, y, abgr(col));
        }
        return out;
    }

    /** How much the pattern of a kind of food moves the brightness of a pixel (-0.2 to 0.2), plus noise that differs per dish. */
    private static float modulation(String pattern, int x, int y, int seed) {
        float noise = ((hash(x, y, seed) & 255) / 255f - 0.5f) * 2f;
        return switch (pattern) {
            case "fibers" -> (y % 2 == 0 ? 0f : -0.10f) + noise * 0.06f;
            case "flakes" -> ((x + y) % 4 == 0 ? 0.12f : 0f) + (Math.floorMod(x - y, 5) == 0 ? -0.08f : 0f) + noise * 0.05f;
            case "segments" -> (x % 3 == 1 && y % 3 == 1 ? 0.18f : -0.03f) + noise * 0.04f;
            case "speckle" -> noise * 0.18f;
            default -> noise * 0.05f + ((x + y) % 7 == 0 ? 0.06f : 0f);
        };
    }

    // ------------------------------------------------------------ colours of an ingredient
    private static int[] ramp(List<ResourceLocation> items) {
        String key = items.toString();
        return RAMPS.computeIfAbsent(key, k -> {
            List<int[]> px = new ArrayList<>();   // {luma, rgb}
            for (ResourceLocation id : items) collect(id, px);
            if (px.isEmpty()) { int c = Dish.fillingColor(items.get(0)); return new int[] {mulRgb(c, 0.55f), c, mulRgb(c, 1.25f)}; }
            px.sort((a, b) -> Integer.compare(a[0], b[0]));
            int n = px.size();
            return new int[] {average(px, 0, Math.max(1, n / 10)), average(px, n * 2 / 5, Math.max(n * 3 / 5, n * 2 / 5 + 1)), average(px, n - Math.max(1, n / 10), n)};
        });
    }
    private static void collect(ResourceLocation id, List<int[]> out) {
        try {
            ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(id));
            TextureAtlasSprite sprite = Minecraft.getInstance().getItemRenderer().getModel(st, null, null, 0).getParticleIcon(ModelData.EMPTY);
            NativeImage img = sprite.contents().getOriginalImage();
            int w = sprite.contents().width(), h = sprite.contents().height();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);
                if ((p >>> 24) < 200) continue;
                int r = p & 255, g = p >> 8 & 255, b = p >> 16 & 255, luma = (r * 299 + g * 587 + b * 114) / 1000;
                if (luma < 30) continue;   // the dark edge of an icon is not the food
                out.add(new int[] {luma, r << 16 | g << 8 | b});
            }
        } catch (Throwable ignored) { }
    }
    private static int average(List<int[]> px, int from, int to) {
        long r = 0, g = 0, b = 0; int n = Math.max(1, to - from);
        for (int i = from; i < Math.min(to, px.size()); i++) { int c = px.get(i)[1]; r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; }
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }
    private static int mulRgb(int c, float f) { return Math.min(255, (int) ((c >> 16 & 255) * f)) << 16 | Math.min(255, (int) ((c >> 8 & 255) * f)) << 8 | Math.min(255, (int) ((c & 255) * f)); }
    /** u from 0 (darkest) to 1 (lightest) along the ramp: dark at 0, the middle colour at 0.55, light at 1. */
    private static int sample(int[] ramp, float u) {
        int a, b; float t;
        if (u < 0.55f) { a = ramp[0]; b = ramp[1]; t = u / 0.55f; } else { a = ramp[1]; b = ramp[2]; t = (u - 0.55f) / 0.45f; }
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
