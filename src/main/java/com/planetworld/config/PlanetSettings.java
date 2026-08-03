package com.planetworld.config;

/**
 * Per-world planet settings chosen at world creation (or loaded from disk / synced to clients).
 * Circumference snaps to discrete doubling steps from {@link #MIN_CIRCUMFERENCE} to {@link #MAX_CIRCUMFERENCE}.
 * <p>
 * {@code curvatureIntensity} is kept for save/sync compatibility but runtime curvature is physical {@code d^2/(2R)}; intensity field stores a UI percent hint.
 */
public record PlanetSettings(
        int circumference,
        float curvatureIntensity,
        boolean localizedTime,
        boolean localizedWeather,
        boolean entityWrap,
        boolean curvatureShader,
        WorldGenStyle worldGenStyle,
        WorldgenPackChoice worldgenPackChoice
) {
    public static final int MIN_CIRCUMFERENCE = 256;
    public static final int MAX_CIRCUMFERENCE = 102_400;
    /** Realism climate is only offered at or above this half-period size. */
    public static final int MIN_REALISM_CIRCUMFERENCE = 2048;
    /** Full one-of-each biome + structure guarantees (circumference &gt; 4096). */
    public static final int MIN_FULL_COVERAGE_CIRCUMFERENCE = 8192;
    /** @deprecated Use {@link #MIN_REALISM_CIRCUMFERENCE}. */
    @Deprecated
    public static final int MIN_CONTINENTAL_CIRCUMFERENCE = MIN_REALISM_CIRCUMFERENCE;

    /** Discrete slider steps: 256, 512, … 65536, then 102400. */
    public static final int[] CIRCUMFERENCE_STEPS = {
            256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 102_400
    };

    public PlanetSettings {
        circumference = snapCircumference(circumference);
        worldGenStyle = worldGenStyle == null ? WorldGenStyle.NORMAL : worldGenStyle;
        worldgenPackChoice = worldgenPackChoice == null ? WorldgenPackChoice.VANILLA : worldgenPackChoice;
        if (circumference < MIN_REALISM_CIRCUMFERENCE) {
            worldGenStyle = WorldGenStyle.NORMAL;
        }
        worldgenPackChoice = worldgenPackChoice.sanitize();
        curvatureIntensity = effectiveCurvatureIntensityFor(circumference);
    }

    public float effectiveCurvatureIntensity() {
        return effectiveCurvatureIntensityFor(circumference);
    }

    public static float effectiveCurvatureIntensityFor(int circumferenceBlocks) {
        int snapped = snapCircumference(circumferenceBlocks);
        double radius = Math.max(1.0, snapped / Math.PI);
        double distance = 64.0;
        double drop = (distance * distance) / (2.0 * radius);
        return (float) (100.0 * drop / distance);
    }

    public boolean allowsRealism() {
        return circumference >= MIN_REALISM_CIRCUMFERENCE;
    }

    /** @deprecated Use {@link #allowsRealism()}. */
    @Deprecated
    public boolean allowsContinental() {
        return allowsRealism();
    }

    public boolean isRealism() {
        return worldGenStyle == WorldGenStyle.REALISM && allowsRealism();
    }

    /** @deprecated Use {@link #isRealism()}. */
    @Deprecated
    public boolean isContinental() {
        return isRealism();
    }

    public boolean allowsFullCoverage() {
        return circumference >= MIN_FULL_COVERAGE_CIRCUMFERENCE;
    }

    public static int snapCircumference(int value) {
        int best = CIRCUMFERENCE_STEPS[0];
        int bestDist = Math.abs(value - best);
        for (int step : CIRCUMFERENCE_STEPS) {
            int dist = Math.abs(value - step);
            if (dist < bestDist) {
                best = step;
                bestDist = dist;
            }
        }
        return best;
    }

    public static int clampCircumference(int value) {
        return snapCircumference(value);
    }

    public static int stepIndex(int circumference) {
        int snapped = snapCircumference(circumference);
        for (int i = 0; i < CIRCUMFERENCE_STEPS.length; i++) {
            if (CIRCUMFERENCE_STEPS[i] == snapped) {
                return i;
            }
        }
        return 0;
    }

    public static PlanetSettings defaults() {
        boolean loaded = PlanetWorldConfig.configLoaded();
        int circumference = loaded
                ? PlanetWorldConfig.PLANET_CIRCUMFERENCE.getAsInt()
                : PlanetWorldConfig.DEFAULT_CIRCUMFERENCE;
        return new PlanetSettings(
                circumference,
                effectiveCurvatureIntensityFor(circumference),
                loaded ? PlanetWorldConfig.ENABLE_LOCALIZED_TIME.getAsBoolean() : PlanetWorldConfig.DEFAULT_LOCALIZED_TIME,
                loaded ? PlanetWorldConfig.ENABLE_LOCALIZED_WEATHER.getAsBoolean() : PlanetWorldConfig.DEFAULT_LOCALIZED_WEATHER,
                loaded ? PlanetWorldConfig.ENABLE_ENTITY_WRAP.getAsBoolean() : PlanetWorldConfig.DEFAULT_ENTITY_WRAP,
                loaded ? PlanetWorldConfig.ENABLE_CURVATURE_SHADER.getAsBoolean() : PlanetWorldConfig.DEFAULT_CURVATURE_SHADER,
                WorldGenStyle.NORMAL,
                WorldgenPackChoice.VANILLA
        );
    }

    public double halfCircumference() {
        return circumference;
    }

    public int chunkWidth() {
        return Math.max(1, circumference / 8);
    }

    public PlanetSettings withCircumference(int value) {
        return new PlanetSettings(value, curvatureIntensity, localizedTime, localizedWeather, entityWrap, curvatureShader, worldGenStyle, worldgenPackChoice);
    }

    public PlanetSettings withCurvatureIntensity(float value) {
        return new PlanetSettings(circumference, value, localizedTime, localizedWeather, entityWrap, curvatureShader, worldGenStyle, worldgenPackChoice);
    }

    public PlanetSettings withLocalizedTime(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, value, localizedWeather, entityWrap, curvatureShader, worldGenStyle, worldgenPackChoice);
    }

    public PlanetSettings withLocalizedWeather(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, value, entityWrap, curvatureShader, worldGenStyle, worldgenPackChoice);
    }

    public PlanetSettings withEntityWrap(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, localizedWeather, value, curvatureShader, worldGenStyle, worldgenPackChoice);
    }

    public PlanetSettings withCurvatureShader(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, localizedWeather, entityWrap, value, worldGenStyle, worldgenPackChoice);
    }

    public PlanetSettings withWorldGenStyle(WorldGenStyle style) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, localizedWeather, entityWrap, curvatureShader, style, worldgenPackChoice);
    }

    public PlanetSettings withWorldgenPackChoice(WorldgenPackChoice choice) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, localizedWeather, entityWrap, curvatureShader, worldGenStyle, choice);
    }
}
