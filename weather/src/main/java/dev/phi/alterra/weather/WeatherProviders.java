package dev.phi.alterra.weather;

import dev.phi.prismlib.api.SeaProvider;
import dev.phi.prismlib.api.StormProvider;
import dev.phi.prismlib.api.WaterEvents;
import dev.phi.prismlib.api.WindProvider;
import dev.phi.prismlib.service.Services;
import dev.phi.alterra.weather.storm.HeavyStorm;
import dev.phi.alterra.weather.storm.StormCells;
import dev.phi.alterra.weather.storm.Wetness;
import dev.phi.alterra.weather.water.Foam;
import dev.phi.alterra.weather.water.Hulls;
import dev.phi.alterra.weather.water.Ripples;
import dev.phi.alterra.weather.water.SeaOpen;
import dev.phi.alterra.weather.water.SeaSurge;
import dev.phi.alterra.weather.water.SubLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** What this mod knows, offered to every other mod through Prism Lib's services: the wind, the storms, the sea and what is happening on the water. */
public final class WeatherProviders {
    private WeatherProviders() {}

    public static void register() {
        Services.register(WindProvider.class, (level, x, z) -> Wind.at(level.getGameTime(), x, z, level.getRainLevel(1f), level.getThunderLevel(1f), HeavyStorm.at(level, x, z)), 100, AlterraWeather.ID);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) Services.register(dev.phi.prismlib.api.WaveField.class, dev.phi.alterra.weather.water.WaveFieldClient.INSTANCE, 100, AlterraWeather.ID);
        Services.register(dev.phi.prismlib.api.WaterSurfaces.class, dev.phi.alterra.weather.disaster.DisasterClient::surfaces, 100, AlterraWeather.ID);
        Services.register(dev.phi.prismlib.api.DisasterView.class, dev.phi.alterra.weather.disaster.DisasterClient::funnels, 100, AlterraWeather.ID);
        Services.register(StormProvider.class, new StormProvider() {
            public float heavy(Level level) { var c = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(); return level.isClientSide ? HeavyStorm.at(level, c.x, c.z) : HeavyStorm.level(level); }   // on the client: over the camera
            public java.util.List<dev.phi.prismlib.api.StormCell> cells(Level level, double x, double z, double range) {
                var list = new java.util.ArrayList<StormCells.Cell>(); StormCells.near(level.getGameTime(), x, z, range, list);
                var out = new java.util.ArrayList<dev.phi.prismlib.api.StormCell>(list.size());
                for (var c : list) out.add(new dev.phi.prismlib.api.StormCell(c.x(), c.z(), c.radius(), c.level(), c.vx(), c.vz()));
                return out;
            }
            public float fog() { return WeatherHooks.stormFog; }
            public float drying(Level level, BlockPos pos) { return Drying.factor(level, pos); }
            public float serverWet() { return Wetness.serverAt != 0 && System.nanoTime() - Wetness.serverAt < 6_000_000_000L ? Wetness.serverWet : Float.NaN; }
        }, 100, AlterraWeather.ID);
        Services.register(SeaProvider.class, new SeaProvider() {
            public float open(Level level, int x, int z, BlockPos.MutableBlockPos scratch) { return SeaOpen.at(level, x, z, scratch); }
            public boolean surging() { return SeaSurge.active(); }
            public float surge(double x, double z) { return SeaSurge.height(x, z); }
        }, 100, AlterraWeather.ID);
        Services.register(WaterEvents.class, new WaterEvents() {
            public int foam(float[] out, Vec3 camera) { return Foam.snapshot(out, camera); }
            public int ripples(float[] out, Vec3 camera) { return Ripples.snapshot(out, camera); }
            public int shipBoxes(float[] out, Vec3 camera) { return SubLevels.snapshot(out, camera); }
            public int hulls(float[] out, Vec3 camera) { return SubLevels.snapshotHulls(out, camera); }
            public boolean shipsHandledElsewhere() { return Hulls.deepSeas(); }
        }, 100, AlterraWeather.ID);
    }
}
