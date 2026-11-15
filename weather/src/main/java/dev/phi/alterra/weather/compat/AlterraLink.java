package dev.phi.alterra.weather.compat;

import dev.phi.alterra.api.AlterraApi;
import dev.phi.alterra.weather.Wind;
import dev.phi.alterra.weather.config.DisasterConfig;
import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.alterra.weather.config.WeatherConfig;
import dev.phi.alterra.weather.storm.HeavyStorm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** What this mod does for Alterra when Alterra is installed: tells it the wind and adds its pages to Alterra's settings screen. Only loaded then; without Alterra the options are in the config files. */
public final class AlterraLink {
    private AlterraLink() {}
    public static void register() {
        AlterraApi.setWindProvider((level, x, z) -> Wind.at(level.getGameTime(), x, z, level.getRainLevel(1f), level.getThunderLevel(1f), HeavyStorm.level(level)));   // other addons can ask for the wind
        AlterraApi.addSettingsCategory("Weather", "Weather effects. Only on your computer.", () -> new ItemStack(Items.SAND), List.of(
                AlterraApi.Option.bool("Sandstorms", "Dust and sandy fog in deserts.", WeatherConfig.SANDSTORMS),
                AlterraApi.Option.bool("Fog banks", "Thick fog in misty biomes.", WeatherConfig.FOG_BANKS),
                AlterraApi.Option.bool("Petal storms", "Petals blowing around in blossom woods.", WeatherConfig.PETAL_STORMS),
                AlterraApi.Option.bool("Aurora", "Northern lights over snowy biomes at night.", WeatherConfig.AURORA),
                AlterraApi.Option.bool("Blizzards", "Driven snow and bad visibility in snowy biomes.", WeatherConfig.BLIZZARDS),
                AlterraApi.Option.bool("Pollen", "Pollen floating around flower biomes on sunny days.", WeatherConfig.POLLEN),
                AlterraApi.Option.bool("Ashfall", "Ash falling in burnt biomes.", WeatherConfig.ASHFALL),
                AlterraApi.Option.bool("Follow vanilla weather", "Rain and thunder bring each biome's own storm: sandstorm in deserts, blizzard in snow, petals, ash, mist.", WeatherConfig.FOLLOW_VANILLA),
                AlterraApi.Option.number("Storm share", "How often rain turns into the biome storm instead of plain rain or snow.", WeatherConfig.STORM_SHARE, 0.0, 2.0, 0.1, "x"),
                AlterraApi.Option.bool("Rain ripples", "Raindrops that land on water make rings.", WeatherConfig.RIPPLES),
                AlterraApi.Option.bool("Wind-blown rain", "Rain and snow fall slanted by the wind and are drawn by Alterra, not vanilla.", WeatherConfig.REPLACE_RAIN),
                AlterraApi.Option.number("Rain slant", "How far the wind pushes the rain sideways.", WeatherConfig.RAIN_SLANT, 0.0, 1.5, 0.1, "x"),
                AlterraApi.Option.bool("Heavy thunderstorms", "Some thunderstorms are heavy: a gale, pouring rain, a dark sky.", HeavyConfig.HEAVY),
                AlterraApi.Option.number("Heavy storm share", "How many thunderstorms are heavy ones.", HeavyConfig.SHARE, 0.0, 1.0, 0.1, "x"),
                AlterraApi.Option.bool("Lightning", "Heavy thunderstorms strike lightning around you.", HeavyConfig.LIGHTNING),
                AlterraApi.Option.number("Lightning rate", "How often it strikes.", HeavyConfig.LIGHTNING_RATE, 0.1, 4.0, 0.1, "x"),
                AlterraApi.Option.number("Real strikes", "What share of the strikes are real (fire, damage). The rest are for show.", HeavyConfig.REAL_BOLTS, 0.0, 1.0, 0.05, "x"),
                AlterraApi.Option.bool("Footprints", "Prints and puffs where you and other things walk on snow, sand and mud.", WeatherConfig.FOOTPRINTS),
                AlterraApi.Option.bool("Shore splashes", "Waves break on the shore and throw spray inland that wets the land.", WeatherConfig.SHORE_SPLASH),
                AlterraApi.Option.bool("Snow tracks", "Walking on layers of snow presses them down: real tracks.", HeavyConfig.SNOW_TRACKS),
                AlterraApi.Option.bool("Snow settles", "Snow on slopes creeps downhill by itself (the start of avalanches).", HeavyConfig.SNOW_SETTLES),
                AlterraApi.Option.bool("Wind moves sand and snow", "Sandstorms carry sand into dunes and blizzards carry snow into drifts, for real (blocks move).", HeavyConfig.WIND_MOVES_LOOSE),
                AlterraApi.Option.bool("Avalanches", "Snow on steep slopes slides down in heavy snowfall, when something explodes, lightning strikes or you run across it.", HeavyConfig.AVALANCHES),
                AlterraApi.Option.bool("Distant storms", "See a storm in the next biome on the horizon before you get there.", WeatherConfig.DISTANT),
                AlterraApi.Option.bool("Storms on their own", "Storms also come without rain, on their own timer.", WeatherConfig.OWN_STORMS),
                AlterraApi.Option.bool("Adapt to frame rate", "Use fewer particles when the game slows down.", WeatherConfig.ADAPT),
                AlterraApi.Option.number("Intensity", "How many particles. Lower it if the game runs badly.", WeatherConfig.INTENSITY, 0.1, 2.0, 0.1, "x"),
                AlterraApi.Option.bool("Step aside for other mods", "Turn off effects that another weather or fog mod already does.", WeatherConfig.STEP_ASIDE)));
        AlterraApi.addSettingsCategory("Disasters", "Tornadoes, hail, whirlpools and dust devils. They need Alterra Illuminate, which draws them, and happen on the server.", () -> new ItemStack(Items.LIGHTNING_ROD), List.of(
                AlterraApi.Option.bool("Tornadoes", "Now and then in heavy thunderstorms: a funnel that travels with the wind and throws things around.", DisasterConfig.TORNADOES),
                AlterraApi.Option.bool("Tornadoes tear up blocks", "Tornadoes lift soft blocks (leaves, plants, glass, snow, carpet, wool). Off: they only move free things.", DisasterConfig.TORNADO_BLOCKS),
                AlterraApi.Option.bool("Hail", "Some heavy thunderstorms bring hail that hurts.", DisasterConfig.HAIL),
                AlterraApi.Option.bool("Hail breaks glass", "Hail can break glass that is open to the sky.", DisasterConfig.HAIL_GLASS),
                AlterraApi.Option.bool("Whirlpools", "Whirlpools in the open sea that pull in boats and swimmers.", DisasterConfig.VORTEXES),
                AlterraApi.Option.bool("Dust devils", "Small harmless whirls of sand in the desert on hot days.", DisasterConfig.DUST_DEVILS),
                AlterraApi.Option.bool("Pull things in", "Disasters push and pull players, mobs, items and boats. Off: only for show.", DisasterConfig.PULL_THINGS),
                AlterraApi.Option.number("How often", "How often disasters start. 1 is normal.", DisasterConfig.RATE, 0.0, 3.0, 0.1, "x")));
    }
}
