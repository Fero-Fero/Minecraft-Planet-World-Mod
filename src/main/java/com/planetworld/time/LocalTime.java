package com.planetworld.time;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;

/**
 * Spatial time authority:
 * LocalTime = (GlobalServerTime + (EntityX / WorldWidth) * 24000) % 24000
 */
public final class LocalTime {
    public static final long DAY_LENGTH = 24000L;

    /** Light level vanilla requires for crops and saplings to advance. */
    private static final int GROWTH_LIGHT_LEVEL = 9;

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

    /**
     * True when local nightfall should hold back sunlight-driven growth at {@code lightPos}.
     * <p>
     * Vanilla ties crop and sapling growth to a light level rather than to the clock, so growth under
     * artificial light must keep working at night exactly as it does in an unwrapped world; only the
     * sunlit case follows the local day.
     */
    public static boolean holdsBackSunlitGrowth(Level level, BlockPos lightPos) {
        if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
            return false;
        }
        if (isDay(level, lightPos.getX())) {
            return false;
        }
        return level.getBrightness(LightLayer.BLOCK, lightPos) < GROWTH_LIGHT_LEVEL;
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
