package dev.phi.alterra.illuminate.compat;

import dev.phi.alterra.api.AlterraApi;
import dev.phi.alterra.illuminate.IlluminateConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Illuminate's page in Alterra's settings screen. Only loaded when Alterra is installed; without it the same options are in the config file. */
public final class AlterraSettings {
    private AlterraSettings() {}
    public static void register() {
        AlterraApi.addSettingsCategory("Illuminate", "The look: water, light, clouds and the rest. Only on your computer.", () -> new ItemStack(Items.GLOWSTONE), List.of(
                AlterraApi.Option.number("Quality", "How much work the look does: 0 low (fastest), 1 medium, 2 high.", IlluminateConfig.QUALITY, 0, 2, 1, ""),
                AlterraApi.Option.bool("Water look", "Clear water coloured by depth and biome, mirroring the sky, with the sun's glint, ripples and rain rings.", IlluminateConfig.WATER_LOOK),
                AlterraApi.Option.bool("Reflections", "The water, wet ground and metal mirror the world around them, not only the sky.", IlluminateConfig.REFLECTIONS),
                AlterraApi.Option.bool("Caustics", "Rippling light on the bottom of shallow water and under water.", IlluminateConfig.CAUSTICS),
                AlterraApi.Option.number("Caustic strength", "How bright the caustics and the lava glow are.", IlluminateConfig.CAUSTIC_STRENGTH, 0.1, 2.0, 0.1, "x"),
                AlterraApi.Option.bool("Waves", "The surface rises and falls in slow swells; boats ride them.", IlluminateConfig.WATER_WAVES),
                AlterraApi.Option.bool("Custom snow", "Snow is drawn as its own smooth, grainy surface instead of the blocks' layers.", IlluminateConfig.CUSTOM_SNOW),
                AlterraApi.Option.bool("Smooth snow", "Snow lies in smooth drifts and slopes instead of steps of an eighth of a block.", IlluminateConfig.SMOOTH_SNOW),
                AlterraApi.Option.bool("Smooth water", "Filling and draining water moves smoothly instead of a step at a time.", IlluminateConfig.SMOOTH_WATER),
                AlterraApi.Option.bool("Shore foam", "A band of foam along the water's edge.", IlluminateConfig.FOAM),
                AlterraApi.Option.bool("Lava glow", "Flickering glow on lava and the ground next to it.", IlluminateConfig.LAVA_GLOW),
                AlterraApi.Option.bool("Wet surfaces", "Rain darkens what it falls on, and puddles gather in low spots.", IlluminateConfig.WET),
                AlterraApi.Option.bool("Shining metal", "Metal blocks shine and mirror the sky.", IlluminateConfig.METALS),
                AlterraApi.Option.bool("Swaying leaves", "Leaves sway in the wind. Needs a restart.", IlluminateConfig.SWAY),
                AlterraApi.Option.bool("Swaying plants", "Grass and flowers sway too.", IlluminateConfig.SWAY_PLANTS),
                AlterraApi.Option.number("Sway amount", "How far things sway.", IlluminateConfig.SWAY_AMOUNT, 0.0, 2.5, 0.1, "x"),
                AlterraApi.Option.bool("Scene look", "The scene pass: colour grade, glow, light shafts, haze, clouds, underwater fog. Off: none of them.", IlluminateConfig.SCENE_LOOK),
                AlterraApi.Option.bool("Colour grade", "A filmic curve, warm light, slightly cool shadows.", IlluminateConfig.GRADE),
                AlterraApi.Option.bool("Glow", "A soft glow around very bright things.", IlluminateConfig.GLOW),
                AlterraApi.Option.bool("Light shafts", "Light shafts from the sun through gaps in trees and terrain.", IlluminateConfig.SHAFTS),
                AlterraApi.Option.bool("Haze", "Haze in the far distance, warm towards the sun.", IlluminateConfig.HAZE),
                AlterraApi.Option.bool("Clouds", "Real clouds instead of vanilla's flat ones, and their shadows (the heaviest part).", IlluminateConfig.CLOUDS),
                AlterraApi.Option.bool("Sun shadows", "Soft shadows of trees and overhangs from the sun, and light shafts through leaves.", IlluminateConfig.SHADOWS),
                AlterraApi.Option.bool("Underwater fog", "Under water the water's colour closes in with distance, with light rippling over everything.", IlluminateConfig.UNDERWATER_FOG)));
    }
}
