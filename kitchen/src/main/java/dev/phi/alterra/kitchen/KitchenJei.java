package dev.phi.alterra.kitchen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps the secret drink recipes out of JEI. Only loaded when JEI is installed. */
@JeiPlugin
public final class KitchenJei implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath(AlterraKitchen.ID, "jei"); }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        Set<String> hidden = new HashSet<>();
        try (var in = KitchenJei.class.getResourceAsStream("/alterra_kitchen_juices.json")) {
            List<Map<String, Object>> list = new Gson().fromJson(new InputStreamReader(in), new TypeToken<List<Map<String, Object>>>() {}.getType());
            for (Map<String, Object> j : list) if (Boolean.TRUE.equals(j.get("hidden"))) hidden.add((String) j.get("id"));
        } catch (Exception e) { return; }
        IRecipeManager rm = runtime.getRecipeManager();
        rm.createRecipeCategoryLookup().includeHidden().get().forEach(cat -> hideIn(rm, cat, hidden));
    }

    private static <T> void hideIn(IRecipeManager rm, IRecipeCategory<T> cat, Set<String> hidden) {
        RecipeType<T> type = cat.getRecipeType();
        List<T> found = rm.createRecipeLookup(type).includeHidden().get().filter(r -> {
            if (!(r instanceof RecipeHolder<?> h)) return false;
            ResourceLocation id = h.id();
            if (!id.getNamespace().equals(AlterraKitchen.ID)) return false;
            String path = id.getPath();
            return hidden.contains(path.substring(path.lastIndexOf('/') + 1));
        }).toList();
        if (!found.isEmpty()) rm.hideRecipes(type, found);
    }
}
