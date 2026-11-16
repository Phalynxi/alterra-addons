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
        bus.addListener(dev.phi.alterra.illuminate.water.GridWater::registerShader);   // grid-simulated water's own shader
        bus.addListener(dev.phi.alterra.illuminate.snow.SnowSurface::registerShader);   // the snow surface's own shader
        dev.phi.prismlib.service.Services.register(dev.phi.prismlib.api.SnowSurfaceProvider.class, (lv, x, z) -> lv instanceof net.minecraft.client.multiplayer.ClientLevel cl ? dev.phi.alterra.illuminate.snow.SnowSurface.heightAt(cl, x, z) : Float.NaN, 100, ID);   // what sits on the snow asks where it is
        dev.phi.prismlib.service.Services.register(dev.phi.prismlib.api.DisturbanceListener.class, (level, d) -> {   // a wheel on the snow, seen on this client: it presses a dent as well (the weather mod presses the layers on the server)
            if (!level.isClientSide || d.kind() != dev.phi.prismlib.api.Disturbance.Kind.WHEEL || !(level instanceof net.minecraft.client.multiplayer.ClientLevel cl)) return;
            float h = dev.phi.alterra.illuminate.snow.SnowSurface.heightAt(cl, d.x(), d.z());
            if (Float.isNaN(h) || Math.abs(d.y() - h) > 1.5) return;
            dev.phi.alterra.illuminate.snow.SnowDents.stamp(d.x(), d.z(), d.radius() * 0.7f + 0.12f, 0.07f + 0.2f * Math.min(1f, d.energy()));
        }, 100, ID);
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
