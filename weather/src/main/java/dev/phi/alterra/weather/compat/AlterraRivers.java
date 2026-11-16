package dev.phi.alterra.weather.compat;

import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Alterra's own rivers (in the Alterra core, when it is installed): the water surface of the river over a column. The water simulation refills river water towards it
 * at a limited rate (the river is fed from upstream), so a river is finite but keeps flowing. Looked up once by name; without the core this is always NaN.
 */
public final class AlterraRivers {
    private AlterraRivers() {}
    private static final Logger LOG = LoggerFactory.getLogger("AlterraWeather");
    private static MethodHandle surface; private static boolean looked;

    public static float surface(ServerLevel l, int x, int z) {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("dev.phi.alterra.world.rivers.RiverWaterFeature");
                surface = MethodHandles.publicLookup().findStatic(c, "riverSurface", MethodType.methodType(float.class, ServerLevel.class, int.class, int.class));
            } catch (ClassNotFoundException e) { surface = null; }   // no Alterra core rivers
            catch (ReflectiveOperationException e) { LOG.warn("Alterra Weather: the core's rivers could not be linked", e); surface = null; }
        }
        if (surface == null) return Float.NaN;
        try { return (float) surface.invokeExact(l, x, z); }
        catch (Throwable t) { LOG.warn("Alterra Weather: the river check failed, rivers are treated as ordinary water from now on", t); surface = null; return Float.NaN; }
    }
}
