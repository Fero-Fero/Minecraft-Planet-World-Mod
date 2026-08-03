package com.planetworld.weather;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.wrap.WrapMath;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Weather is treated as sweeping fronts across X rather than a single global storm.
 * A position is "in weather" when its local band matches the current front phase.
 * Polar winter adds latitude blizzards on top of band rain.
 */
public final class LocalizedWeatherHandler {
    /** Number of weather bands around the planet. */
    public static final int BAND_COUNT = 8;
    private static final double POLAR_BLIZZARD_ABS_LAT = 0.70;

    private LocalizedWeatherHandler() {
    }

    public static boolean isInWeatherBand(Level level, double x) {
        if (!PlanetWorldConfig.enableLocalizedWeather() || !WrapMath.isWrappedDimension(level)) {
            return level.isRaining();
        }
        if (!level.isRaining()) {
            return false;
        }
        return isBandRainy(level, x);
    }

    /**
     * Pure band math — does not consult {@link Level#isRaining()}, so it is safe to
     * call from a {@code getRainLevel} mixin without recursing.
     */
    public static boolean isBandRainy(Level level, double x) {
        double width = PlanetWorldConfig.planetCircumference() * 2.0;
        double wx = WrapMath.wrapX(x);
        // Front drifts with global time so storms sweep around the planet
        double phase = (level.getGameTime() % 24000L) / 24000.0;
        double band = ((wx / width) + 0.5 + phase) % 1.0;
        int index = (int) (band * BAND_COUNT);
        // Alternate rainy / clear bands while global rain is active
        return (index % 2) == 0;
    }

    /**
     * Polar winter blizzard: forces precipitation visuals / extinguish even when the
     * X-band is clear, as long as global rain is active or localized time is on.
     */
    public static boolean isPolarBlizzard(Level level, double z) {
        if (!WrapMath.isWrappedDimension(level) || !PlanetWorldConfig.isRealism()) {
            return false;
        }
        if (!PlanetWorldConfig.enableLocalizedWeather() && !PlanetWorldConfig.enableLocalizedTime()) {
            return false;
        }
        double absLat = Math.abs(SeasonAuthority.latitude(level, z));
        if (absLat < POLAR_BLIZZARD_ABS_LAT) {
            return false;
        }
        return SeasonAuthority.isLocalWinter(level, z);
    }

    /** Band rain or polar blizzard at the player position. */
    public static boolean isStormyAt(Level level, double x, double z) {
        if (isPolarBlizzard(level, z)) {
            return true;
        }
        if (!PlanetWorldConfig.enableLocalizedWeather() || !WrapMath.isWrappedDimension(level)) {
            return level.isRaining();
        }
        if (!level.isRaining()) {
            return false;
        }
        return isBandRainy(level, x);
    }

    public static boolean shouldExtinguishFire(Level level, double x) {
        return isInWeatherBand(level, x);
    }

    public static boolean shouldExtinguishFire(Level level, double x, double z) {
        return isStormyAt(level, x, z);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // Band math is computed on demand; no per-tick world mutation required.
    }
}
