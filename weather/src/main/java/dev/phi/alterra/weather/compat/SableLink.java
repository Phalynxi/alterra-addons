package dev.phi.alterra.weather.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the vehicle mods (Sable, Create Aeronautics, Offroad) need from us, through reflection so none of them is needed to build or run: positions inside a vehicle turned into world
 * positions, and reading the private fields and methods of their block entities. Anything that does not work is logged once, clearly, and the vehicle effect is simply off:
 * a changed field name in another mod must never be able to crash the game.
 */
public final class SableLink {
    private SableLink() {}
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, Boolean> WARNED = new ConcurrentHashMap<>();
    private static Object companion; private static Method project; private static boolean projectFailed;

    /** A position in the world: a position inside a vehicle is projected out of it, anything else stays as it is. */
    public static Vec3 toWorld(Level level, Vec3 p) {
        if (projectFailed) return p;
        try {
            if (project == null) {
                Class<?> c = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                companion = c.getField("INSTANCE").get(null);
                project = c.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            }
            Object r = project.invoke(companion, level, p);
            return r instanceof Vec3 v ? v : p;
        } catch (ClassNotFoundException e) { projectFailed = true; return p; }   // no Sable: nothing to project
        catch (ReflectiveOperationException | RuntimeException e) { projectFailed = true; warn("sable", "Sable's position projection did not work, vehicle effects use the plain position", e); return p; }
    }

    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    /** A private or public field of another mod's object, or null (logged once). */
    public static Object field(Object o, String name) {
        try {
            Field f = FIELDS.computeIfAbsent(o.getClass().getName() + "#" + name, k -> {
                for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
                    try { Field x = c.getDeclaredField(name); x.setAccessible(true); return x; } catch (NoSuchFieldException ignored) { }
                }
                return null;
            });
            if (f == null) { warn(o.getClass().getName() + "#" + name, "field " + name + " not found in " + o.getClass().getName() + ": that vehicle effect is off", null); return null; }
            return f.get(o);
        } catch (ReflectiveOperationException | RuntimeException e) { warn(o.getClass().getName() + "#" + name, "could not read " + name, e); return null; }
    }

    /** A method without arguments of another mod's object, or null (logged once). */
    public static Object call(Object o, String name) {
        try {
            Method m = METHODS.computeIfAbsent(o.getClass().getName() + "#" + name, k -> {
                for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
                    try { Method x = c.getDeclaredMethod(name); x.setAccessible(true); return x; } catch (NoSuchMethodException ignored) { }
                }
                return null;
            });
            if (m == null) { warn(o.getClass().getName() + "#" + name + "()", "method " + name + " not found in " + o.getClass().getName() + ": that vehicle effect is off", null); return null; }
            return m.invoke(o);
        } catch (ReflectiveOperationException | RuntimeException e) { warn(o.getClass().getName() + "#" + name + "()", "could not call " + name, e); return null; }
    }

    private static void warn(String key, String message, Throwable t) {
        if (WARNED.putIfAbsent(key, Boolean.TRUE) == null) { if (t != null) LOG.warn("Alterra Weather: " + message, t); else LOG.warn("Alterra Weather: " + message); }
    }
}
