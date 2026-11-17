package dev.phi.alterra.illuminate.compat;

import dev.phi.prismlib.settings.Settings;
import dev.phi.alterra.illuminate.IlluminateConfig;

import java.util.List;

/** The options as pages of Prism Lib's settings screen (behind the Config button in the mod list), whether or not Alterra is installed. */
public final class LibrarySettings {
    private LibrarySettings() {}
    public static void register() {
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Quality", "How much work the look does.", List.of(
                Settings.Option.number("Quality", "How much work the look does: 0 low (fastest), 1 medium, 2 high.", IlluminateConfig.QUALITY, 0, 2, 1, "")), "minecraft:comparator", "General");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Colour and glow", "The overall picture.", List.of(
                Settings.Option.bool("Scene look", "The scene pass: colour grade, glow, light shafts, haze, clouds, underwater fog. Off: none of them.", IlluminateConfig.SCENE_LOOK),
                Settings.Option.bool("Colour grade", "A filmic curve, warm light, slightly cool shadows.", IlluminateConfig.GRADE),
                Settings.Option.number("Grade strength", "How much of the colour grade is applied.", IlluminateConfig.GRADE_STRENGTH, 0.0, 1.5, 0.1, "x"),
                Settings.Option.bool("Glow", "A soft glow around very bright things.", IlluminateConfig.GLOW),
                Settings.Option.number("Glow strength", "How strong the glow is.", IlluminateConfig.GLOW_STRENGTH, 0.0, 3.0, 0.1, "x")), "minecraft:sea_lantern", "General");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Surface", "How water looks and moves.", List.of(
                Settings.Option.bool("Water look", "Clear water coloured by depth and biome, mirroring the sky, with the sun's glint, ripples and rain rings.", IlluminateConfig.WATER_LOOK),
                Settings.Option.bool("Reflections", "The water, wet ground and metal mirror the world around them, not only the sky.", IlluminateConfig.REFLECTIONS),
                Settings.Option.bool("Waves", "The surface rises and falls in slow swells; boats ride them.", IlluminateConfig.WATER_WAVES),
                Settings.Option.bool("Smooth water", "Filling and draining water moves smoothly instead of a step at a time.", IlluminateConfig.SMOOTH_WATER),
                Settings.Option.bool("Shore foam", "A band of foam along the water's edge.", IlluminateConfig.FOAM)), "minecraft:water_bucket", "Water");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Underwater", "Seen from below.", List.of(
                Settings.Option.bool("Caustics", "Rippling light on the bottom of shallow water and under water.", IlluminateConfig.CAUSTICS),
                Settings.Option.number("Caustic strength", "How bright the caustics and the lava glow are.", IlluminateConfig.CAUSTIC_STRENGTH, 0.1, 2.0, 0.1, "x"),
                Settings.Option.bool("Underwater fog", "Under water the water's colour closes in with distance, with light rippling over everything.", IlluminateConfig.UNDERWATER_FOG),
                Settings.Option.number("Underwater strength", "How strong caustics and shafts are under water.", IlluminateConfig.UNDERWATER_STRENGTH, 0.0, 2.0, 0.1, "x")), "minecraft:prismarine_crystals", "Water");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Clouds and haze", "The sky overhead and far away.", List.of(
                Settings.Option.bool("Clouds", "Real clouds instead of vanilla's flat ones, and their shadows (the heaviest part).", IlluminateConfig.CLOUDS),
                Settings.Option.number("Cloud amount", "How much of the sky the clouds cover.", IlluminateConfig.CLOUD_AMOUNT, 0.5, 1.5, 0.05, "x"),
                Settings.Option.bool("Haze", "Haze in the far distance, warm towards the sun.", IlluminateConfig.HAZE),
                Settings.Option.number("Haze strength", "How thick the distance haze is.", IlluminateConfig.HAZE_STRENGTH, 0.0, 3.0, 0.1, "x")), "minecraft:white_wool", "Sky");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Sun", "Shafts of light and shadows.", List.of(
                Settings.Option.bool("Light shafts", "Light shafts from the sun through gaps in trees and terrain.", IlluminateConfig.SHAFTS),
                Settings.Option.number("Shaft strength", "How strong the light shafts are.", IlluminateConfig.SHAFT_STRENGTH, 0.0, 2.0, 0.1, "x"),
                Settings.Option.bool("Sun shadows", "Soft shadows of trees and overhangs from the sun, and light shafts through leaves.", IlluminateConfig.SHADOWS),
                Settings.Option.number("Shadow strength", "How dark the sun shadows are.", IlluminateConfig.SHADOW_STRENGTH, 0.0, 2.0, 0.1, "x")), "minecraft:glowstone", "Sky");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Sunset and storms", "Sunrise, sunset and lightning.", List.of(
                Settings.Option.number("Sunset strength", "How red clouds, haze and water get at sunrise and sunset.", IlluminateConfig.SUNSET_STRENGTH, 0.0, 1.5, 0.1, "x"),
                Settings.Option.number("Flash strength", "How bright lightning flashes are.", IlluminateConfig.FLASH_STRENGTH, 0.0, 2.0, 0.1, "x")), "minecraft:fire_charge", "Sky");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Snow", "How snow looks.", List.of(
                Settings.Option.bool("Custom snow", "Snow is drawn as its own smooth, grainy surface instead of the blocks' layers.", IlluminateConfig.CUSTOM_SNOW),
                Settings.Option.bool("Smooth snow", "Snow lies in smooth drifts and slopes instead of steps of an eighth of a block.", IlluminateConfig.SMOOTH_SNOW)), "minecraft:snowball", "World");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Foliage", "Leaves and plants in the wind.", List.of(
                Settings.Option.bool("Swaying leaves", "Leaves sway in the wind. Needs a restart.", IlluminateConfig.SWAY),
                Settings.Option.bool("Swaying plants", "Grass and flowers sway too.", IlluminateConfig.SWAY_PLANTS),
                Settings.Option.number("Sway amount", "How far things sway.", IlluminateConfig.SWAY_AMOUNT, 0.0, 2.5, 0.1, "x")), "minecraft:oak_leaves", "World");
        Settings.category(dev.phi.alterra.illuminate.Illuminate.ID, "Surfaces", "Wet ground, metal and lava.", List.of(
                Settings.Option.bool("Wet surfaces", "Rain darkens what it falls on, and puddles gather in low spots.", IlluminateConfig.WET),
                Settings.Option.bool("Shining metal", "Metal blocks shine and mirror the sky.", IlluminateConfig.METALS),
                Settings.Option.bool("Lava glow", "Flickering glow on lava and the ground next to it.", IlluminateConfig.LAVA_GLOW)), "minecraft:iron_ingot", "World");
    }
}
