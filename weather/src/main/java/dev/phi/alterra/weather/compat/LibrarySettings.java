package dev.phi.alterra.weather.compat;

import dev.phi.prismlib.settings.Settings;
import dev.phi.alterra.weather.config.DisasterConfig;
import dev.phi.alterra.weather.config.HeavyConfig;
import dev.phi.alterra.weather.config.WeatherConfig;

import java.util.List;

/** The options as pages of Prism Lib's settings screen (behind the Config button in the mod list), whether or not Alterra is installed. */
public final class LibrarySettings {
    private LibrarySettings() {}
    public static void register() {
        Settings.category(dev.phi.alterra.weather.AlterraWeather.ID, "Weather", "Weather effects. Only on your computer.", List.of(
                Settings.Option.bool("Sandstorms", "Dust and sandy fog in deserts.", WeatherConfig.SANDSTORMS),
                Settings.Option.bool("Fog banks", "Thick fog in misty biomes.", WeatherConfig.FOG_BANKS),
                Settings.Option.bool("Petal storms", "Petals blowing around in blossom woods.", WeatherConfig.PETAL_STORMS),
                Settings.Option.bool("Aurora", "Northern lights over snowy biomes at night.", WeatherConfig.AURORA),
                Settings.Option.bool("Blizzards", "Driven snow and bad visibility in snowy biomes.", WeatherConfig.BLIZZARDS),
                Settings.Option.bool("Pollen", "Pollen floating around flower biomes on sunny days.", WeatherConfig.POLLEN),
                Settings.Option.bool("Ashfall", "Ash falling in burnt biomes.", WeatherConfig.ASHFALL),
                Settings.Option.bool("Follow vanilla weather", "Rain and thunder bring each biome's own storm: sandstorm in deserts, blizzard in snow, petals, ash, mist.", WeatherConfig.FOLLOW_VANILLA),
                Settings.Option.number("Storm share", "How often rain turns into the biome storm instead of plain rain or snow.", WeatherConfig.STORM_SHARE, 0.0, 2.0, 0.1, "x"),
                Settings.Option.bool("Rain ripples", "Raindrops that land on water make rings.", WeatherConfig.RIPPLES),
                Settings.Option.bool("Wind-blown rain", "Rain and snow fall slanted by the wind and are drawn by Alterra, not vanilla.", WeatherConfig.REPLACE_RAIN),
                Settings.Option.number("Rain slant", "How far the wind pushes the rain sideways.", WeatherConfig.RAIN_SLANT, 0.0, 1.5, 0.1, "x"),
                Settings.Option.bool("Heavy thunderstorms", "Some thunderstorms are heavy: a gale, pouring rain, a dark sky.", HeavyConfig.HEAVY),
                Settings.Option.number("Heavy storm share", "How many thunderstorms are heavy ones.", HeavyConfig.SHARE, 0.0, 1.0, 0.1, "x"),
                Settings.Option.bool("Lightning", "Heavy thunderstorms strike lightning around you.", HeavyConfig.LIGHTNING),
                Settings.Option.number("Lightning rate", "How often it strikes.", HeavyConfig.LIGHTNING_RATE, 0.1, 4.0, 0.1, "x"),
                Settings.Option.number("Real strikes", "What share of the strikes are real (fire, damage). The rest are for show.", HeavyConfig.REAL_BOLTS, 0.0, 1.0, 0.05, "x"),
                Settings.Option.bool("Footprints", "Prints and puffs where you and other things walk on snow, sand and mud.", WeatherConfig.FOOTPRINTS),
                Settings.Option.bool("Shore splashes", "Waves break on the shore and throw spray inland that wets the land.", WeatherConfig.SHORE_SPLASH),
                Settings.Option.bool("Snow tracks", "Walking on layers of snow presses them down: real tracks.", HeavyConfig.SNOW_TRACKS),
                Settings.Option.bool("Snow settles", "Snow on slopes creeps downhill by itself (the start of avalanches).", HeavyConfig.SNOW_SETTLES),
                Settings.Option.bool("Wind moves sand and snow", "Sandstorms carry sand into dunes and blizzards carry snow into drifts, for real (blocks move).", HeavyConfig.WIND_MOVES_LOOSE),
                Settings.Option.bool("Avalanches", "Snow on steep slopes slides down in heavy snowfall, when something explodes, lightning strikes or you run across it.", HeavyConfig.AVALANCHES),
                Settings.Option.bool("Distant storms", "See a storm in the next biome on the horizon before you get there.", WeatherConfig.DISTANT),
                Settings.Option.bool("Storms on their own", "Storms also come without rain, on their own timer.", WeatherConfig.OWN_STORMS),
                Settings.Option.bool("Adapt to frame rate", "Use fewer particles when the game slows down.", WeatherConfig.ADAPT),
                Settings.Option.number("Intensity", "How many particles. Lower it if the game runs badly.", WeatherConfig.INTENSITY, 0.1, 2.0, 0.1, "x"),
                Settings.Option.bool("Step aside for other mods", "Turn off effects that another weather or fog mod already does.", WeatherConfig.STEP_ASIDE)));
        Settings.category(dev.phi.alterra.weather.AlterraWeather.ID, "Disasters", "Tornadoes, hail, whirlpools and dust devils. They need Alterra Illuminate, which draws them, and happen on the server.", List.of(
                Settings.Option.bool("Tornadoes", "Now and then in heavy thunderstorms: a funnel that travels with the wind and throws things around.", DisasterConfig.TORNADOES),
                Settings.Option.bool("Tornadoes tear up blocks", "Tornadoes lift soft blocks (leaves, plants, glass, snow, carpet, wool). Off: they only move free things.", DisasterConfig.TORNADO_BLOCKS),
                Settings.Option.bool("Hail", "Some heavy thunderstorms bring hail that hurts.", DisasterConfig.HAIL),
                Settings.Option.bool("Hail breaks glass", "Hail can break glass that is open to the sky.", DisasterConfig.HAIL_GLASS),
                Settings.Option.bool("Whirlpools", "Whirlpools in the open sea that pull in boats and swimmers.", DisasterConfig.VORTEXES),
                Settings.Option.bool("Dust devils", "Small harmless whirls of sand in the desert on hot days.", DisasterConfig.DUST_DEVILS),
                Settings.Option.bool("Pull things in", "Disasters push and pull players, mobs, items and boats. Off: only for show.", DisasterConfig.PULL_THINGS),
                Settings.Option.number("How often", "How often disasters start. 1 is normal.", DisasterConfig.RATE, 0.0, 3.0, 0.1, "x")));
    }
}
