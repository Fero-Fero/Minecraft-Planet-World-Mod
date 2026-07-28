package com.planetworld.weather;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Weather is treated as sweeping fronts across X rather than a single global storm.
 * A position is "in weather" when its local band matches the current front phase.
 */
public final class LocalizedWeatherHandler {
    /** Number of weather bands around the planet. */
    public static final int BAND_COUNT = 8;

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

    public static boolean shouldExtinguishFire(Level level, double x) {
        return isInWeatherBand(level, x);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // Band math is computed on demand; no per-tick world mutation required.
    }
}
