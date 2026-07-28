package com.planetworld.time;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Spatial time authority:
 * LocalTime = (GlobalServerTime + (EntityX / WorldWidth) * 24000) % 24000
 */
public final class LocalTime {
    public static final long DAY_LENGTH = 24000L;

    private LocalTime() {
    }

    public static long globalTime(Level level) {
        return level.getDayTime();
    }

    public static long localTime(Level level, double x) {
        if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
            return globalTime(level);
        }
        double fullWidth = PlanetWorldConfig.planetCircumference() * 2.0;
        double normalized = WrapMath.wrapX(x) / fullWidth; // −0.5 .. 0.5
        long offset = Math.round(normalized * DAY_LENGTH);
        long local = Math.floorMod(globalTime(level) + offset, DAY_LENGTH);
        return local;
    }

    public static long localTime(Entity entity) {
        return localTime(entity.level(), entity.getX());
    }

    public static boolean isDay(Level level, double x) {
        long t = localTime(level, x);
        return t >= 0 && t < 12000;
    }

    public static boolean isNight(Level level, double x) {
        return !isDay(level, x);
    }

    /** True when sunlight is strong enough for undead to burn (vanilla: 0–12000ish with sky access). */
    public static boolean isSunBurnTime(Level level, double x) {
        long t = localTime(level, x);
        return t >= 0 && t < 12000;
    }

    /**
     * How many global ticks to advance so that the sleeper's local time becomes dawn (0).
     */
    public static long ticksUntilLocalDawn(Level level, double sleeperX) {
        long local = localTime(level, sleeperX);
        return Math.floorMod(-local, DAY_LENGTH);
    }

    /** Celestial angle 0..1 matching vanilla day cycle for sky rendering. */
    public static float celestialAngle(Level level, double x) {
        long t = localTime(level, x);
        double d = (t / (double) DAY_LENGTH) - 0.25;
        if (d < 0) {
            d += 1.0;
        }
        // Smooth like vanilla
        double smoothed = 1.0 - (Math.cos(d * Math.PI) + 1.0) / 2.0;
        return (float) (d * 2.0 + smoothed) / 3.0f;
    }
}
