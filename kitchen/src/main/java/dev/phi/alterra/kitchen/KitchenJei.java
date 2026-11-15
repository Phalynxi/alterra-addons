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
        boolean secrets = dev.phi.alterra.api.AlterraApi.compat("jeiHideSecrets"), mixes = dev.phi.alterra.api.AlterraApi.compat("jeiHideMixes"), create = dev.phi.alterra.api.AlterraApi.compat("jeiHideCreate");
        Set<String> hidden = new HashSet<>(), all = new HashSet<>();
        try (var in = KitchenJei.class.getResourceAsStream("/alterra_kitchen_juices.json")) {
            List<Map<String, Object>> list = new Gson().fromJson(new InputStreamReader(in), new TypeToken<List<Map<String, Object>>>() {}.getType());
            for (Map<String, Object> j : list) {
                String id = (String) j.get("id"); all.add(id);
                boolean secret = Boolean.TRUE.equals(j.get("secret")), hid = Boolean.TRUE.equals(j.get("hidden"));
                if (secret ? secrets : hid && mixes) hidden.add(id);
            }
        } catch (Exception e) { return; }
        IRecipeManager rm = runtime.getRecipeManager();
        rm.createRecipeCategoryLookup().includeHidden().get().forEach(cat -> hideIn(rm, cat, hidden, all, create));
    }

    private static <T> void hideIn(IRecipeManager rm, IRecipeCategory<T> cat, Set<String> hidden, Set<String> all, boolean createToo) {
        RecipeType<T> type = cat.getRecipeType();
        List<T> found = rm.createRecipeLookup(type).includeHidden().get().filter(r -> {
            if (!(r instanceof RecipeHolder<?> h)) return false;
            ResourceLocation id = h.id();
            if (!id.getNamespace().equals(AlterraKitchen.ID) && !id.getNamespace().equals("alterra")) return false;   // the Create recipes are in Alterra's own namespace
            String path = id.getPath(), name = path.substring(path.lastIndexOf('/') + 1);
            return hidden.contains(name) || (createToo && path.startsWith("create/") && all.contains(name));
        }).toList();
        if (!found.isEmpty()) rm.hideRecipes(type, found);
    }
}
