package dev.phi.alterra.illuminate;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Every part of the look, each on its own switch (only on your computer). */
public final class IlluminateConfig {
    private IlluminateConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue WATER_LOOK, REFLECTIONS, CAUSTICS, WATER_WAVES, SMOOTH_WATER, SMOOTH_SNOW, FOAM, WET, METALS, LAVA_GLOW, SWAY, SWAY_PLANTS,
            SCENE_LOOK, GRADE, GLOW, SHAFTS, HAZE, CLOUDS, UNDERWATER_FOG, SHADOWS;
    public static final ModConfigSpec.DoubleValue CAUSTIC_STRENGTH, SWAY_AMOUNT, QUALITY;
    static {
        var b = new ModConfigSpec.Builder();
        QUALITY = b.comment("How much work the look does: 0 low (fastest), 1 medium, 2 high. Fewer cloud, shadow and shaft samples on low.").defineInRange("quality", 1.0, 0.0, 2.0);
        b.push("water");
        WATER_LOOK = b.comment("The water's own look: clear over shallows and coloured by depth and by biome, mirroring the sky, the sun's glint, ripples and rain rings.").define("waterLook", true);
        REFLECTIONS = b.comment("The water, wet ground and metal mirror the world around them (trees, the shore), not only the sky.").define("reflections", true);
        CAUSTICS = b.comment("Rippling light on the bottom of shallow water and on things under water.").define("caustics", true);
        CAUSTIC_STRENGTH = b.comment("How bright the caustics and the lava glow are.").defineInRange("causticStrength", 1.0, 0.1, 2.0);
        WATER_WAVES = b.comment("The surface rises and falls in slow swells; boats ride them.").define("waves", true);
        SMOOTH_SNOW = b.comment("Snow lies in smooth drifts and slopes (the corners of its layers follow the snow around them) instead of steps of an eighth of a block.").define("smoothSnow", true);
        SMOOTH_WATER = b.comment("Water rising and falling (filling, draining) moves smoothly instead of a step at a time.").define("smoothWater", true);
        FOAM = b.comment("A band of foam along the water's edge.").define("shoreFoam", true);
        LAVA_GLOW = b.comment("Flickering glow on lava and on the ground next to it.").define("lavaGlow", true);
        b.pop().push("surfaces");
        WET = b.comment("Rain darkens what it falls on, and puddles gather in low spots.").define("wetSurfaces", true);
        METALS = b.comment("Metal blocks shine and mirror the sky.").define("metals", true);
        SWAY = b.comment("Leaves sway in the wind. Needs a restart to switch on or off.").define("swayingLeaves", true);
        SWAY_PLANTS = b.comment("Grass and flowers sway too.").define("swayingPlants", true);
        SWAY_AMOUNT = b.comment("How far things sway. 1 is normal.").defineInRange("swayAmount", 1.0, 0.0, 2.5);
        b.pop().push("scene");
        SCENE_LOOK = b.comment("The scene pass (everything below). Off: none of them.").define("sceneLook", true);
        GRADE = b.comment("A filmic colour grade: a gentle curve, warm light, slightly cool shadows.").define("colourGrade", true);
        GLOW = b.comment("A soft glow around very bright things.").define("glow", true);
        SHAFTS = b.comment("Light shafts from the sun through gaps in trees and terrain.").define("lightShafts", true);
        HAZE = b.comment("Haze in the far distance, warm towards the sun.").define("haze", true);
        SHADOWS = b.comment("Soft sun shadows of trees and overhangs (from the height of everything around), and light shafts through gaps in leaves.").define("sunShadows", false);
        CLOUDS = b.comment("Real clouds (instead of vanilla's flat ones): the same for everyone, dark when it rains. The heaviest part of the look.").define("clouds", true);
        UNDERWATER_FOG = b.comment("Under water the water's own colour closes in with distance, and light ripples over everything.").define("underwaterFog", true);
        b.pop();
        SPEC = b.build();
    }
    public static <T> T get(ModConfigSpec.ConfigValue<T> v) { try { return v.get(); } catch (Exception e) { return v.getDefault(); } }
}
