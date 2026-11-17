package dev.phi.alterra.illuminate.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Create: Deep Seas ships its own lighting engine (the Vortex Engine: coloured light, block shadows, bloom, shafts). It draws the same things this mod does, so with both the light and the
 * shadows are drawn twice over each other. When both are installed this switches the Vortex Engine off in its config (it reads its master switch at start-up, so it takes effect from
 * the next launch) and says so in the log. Start the game with -Dalterra.keepvortex=true to leave it alone.
 */
public final class VortexEngineGuard {
    private static final Logger LOG = LoggerFactory.getLogger("Alterra Illuminate");
    private static final Pattern ON = Pattern.compile("(?m)^(\\s*)enabled(\\s*)=(\\s*)true");
    private VortexEngineGuard() {}

    public static void apply() {
        if (!ModList.get().isLoaded("create_submarine") || Boolean.getBoolean("alterra.keepvortex")) return;
        Path file = FMLPaths.CONFIGDIR.get().resolve("create_submarine-vortex.toml");
        if (!Files.exists(file)) return;   // not written yet: its own default applies (this runs again on the next launch)
        try {
            String text = Files.readString(file);
            var m = ON.matcher(text);
            if (!m.find()) return;
            Files.writeString(file, m.replaceFirst("$1enabled$2=$3false"));
            LOG.warn("Create: Deep Seas' Vortex Engine (its own coloured light and shadows) is switched off in {}: it would draw over this mod's light and shadows. It stays off from the next launch (start with -Dalterra.keepvortex=true to keep it).", file.getFileName());
        } catch (IOException e) {
            LOG.warn("Could not switch off the Vortex Engine in {}: {}", file.getFileName(), e.toString());
        }
    }
}
