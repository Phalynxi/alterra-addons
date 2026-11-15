package dev.phi.alterra.kitchen;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** How much thirst a drink of ours quenches and whether it cools or warms (Legendary Survival Overhaul's data of our built-in packs, read from our own files so the client can show it). */
public final class LsoStats {
    private LsoStats() {}
    private static final Map<String, Integer> HYDRATION = new HashMap<>(), TEMPERATURE = new HashMap<>();

    private static int read(String path, String field, Map<String, Integer> cache, String name) {
        return cache.computeIfAbsent(name, n -> {
            try (var in = LsoStats.class.getResourceAsStream(path.formatted(n))) {
                if (in == null) return 0;
                JsonArray a = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonArray();
                return a.size() == 0 || !a.get(0).getAsJsonObject().has(field) ? 0 : a.get(0).getAsJsonObject().get(field).getAsInt();
            } catch (Exception e) { return 0; }
        });
    }
    public static int hydration(String name) { return read("/resourcepacks/lso_drinks/data/alterra_kitchen/legendarysurvivaloverhaul/thirst/consumables/%s.json", "hydration", HYDRATION, name); }
    public static int temperature(String name) { return read("/resourcepacks/lso_temp/data/alterra_kitchen/legendarysurvivaloverhaul/temperature/consumables/%s.json", "temperature_level", TEMPERATURE, name); }
}
