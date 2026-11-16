package dev.phi.alterra.illuminate;

import dev.phi.alterra.illuminate.look.Foliage;
import dev.phi.alterra.illuminate.look.PostFx;
import dev.phi.alterra.illuminate.maps.FlowMap;
import dev.phi.alterra.illuminate.maps.RainMap;

import dev.phi.prismlib.api.Look;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/** Alterra Illuminate: the look (with Sodium, no shader pack). It draws what Prism Lib's providers say (Alterra Weather is one). */
@Mod(value = Illuminate.ID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class Illuminate {
    public static final String ID = "alterra_illuminate";

    public Illuminate(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, IlluminateConfig.SPEC);
        Look.shaderWater = Foliage::shaderWater;
        Look.ownClouds = PostFx::drawsClouds;
        Look.waterAmounts = FlowMap::received;
        Look.puddleAt = RainMap::puddle;
        Look.splashOnLand = a -> RainMap.splash(a[0], a[1], a[2], a[3]);
        dev.phi.alterra.illuminate.compat.LibrarySettings.register();   // the options, as pages of Prism Lib's settings screen
        dev.phi.prismlib.settings.SettingsClient.install(container, ID, "Alterra Illuminate");
        if (ModList.get().isLoaded("alterra")) dev.phi.alterra.illuminate.compat.AlterraSettings.register();   // its page in Alterra's settings screen
    }
}
