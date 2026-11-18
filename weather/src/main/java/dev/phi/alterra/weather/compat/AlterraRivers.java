package dev.phi.alterra.weather.compat;

import net.minecraft.server.level.ServerLevel;

/**
 * Alterra's own rivers (in the Alterra core, when it is installed): the water surface of the river over a column. The water simulation refills river water towards it
 * at a limited rate (the river is fed from upstream), so a river is finite but keeps flowing. Without the core this is always NaN.
 */
public final class AlterraRivers {
    private AlterraRivers() {}

    public static float surface(ServerLevel l, int x, int z) {
        if (!net.neoforged.fml.ModList.get().isLoaded("alterra")) return Float.NaN;
        return dev.phi.alterra.api.AlterraApi.riverSurface(l, x, z);
    }
}
