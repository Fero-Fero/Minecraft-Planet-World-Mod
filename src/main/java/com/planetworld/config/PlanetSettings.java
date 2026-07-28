package com.planetworld.config;

/**
 * Per-world planet settings chosen at world creation (or loaded from disk / synced to clients).
 * Circumference snaps to discrete doubling steps from {@link #MIN_CIRCUMFERENCE} to {@link #MAX_CIRCUMFERENCE}.
 * <p>
 * {@code curvatureIntensity} is kept for save/sync compatibility. Runtime curvature is purely physical
 * ({@code drop = d^2 / (2R)} with {@code R = circumference / pi}).
 */
public record PlanetSettings(
        int circumference,
        float curvatureIntensity,
        boolean localizedTime,
        boolean localizedWeather,
        boolean entityWrap,
        boolean curvatureShader,
        WorldGenStyle worldGenStyle
) {
    public static final int MIN_CIRCUMFERENCE = 256;
    public static final int MAX_CIRCUMFERENCE = 102_400;

    /** Discrete slider steps: 256, 512, … 65536, then 102400. */
    public static final int[] CIRCUMFERENCE_STEPS = {
            256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 102_400
    };

    public PlanetSettings {
        circumference = snapCircumference(circumference);
        worldGenStyle = worldGenStyle == null ? WorldGenStyle.NORMAL : worldGenStyle;
        // Persist a display hint; shader uses physical radius only.
        curvatureIntensity = physicalCurvePercentAt(circumference, 64.0);
    }

    /**
     * Approximate vertical drop as a percent of horizontal distance at {@code distanceBlocks}
     * for a sphere of radius {@code C / pi}: {@code 100 * d / (2R)}.
     */
    public static float physicalCurvePercentAt(int circumferenceBlocks, double distanceBlocks) {
        int snapped = snapCircumference(circumferenceBlocks);
        double radius = Math.max(1.0, snapped / Math.PI);
        if (distanceBlocks <= 0.0) {
            return 0f;
        }
        double drop = (distanceBlocks * distanceBlocks) / (2.0 * radius);
        return (float) (100.0 * drop / distanceBlocks);
    }

    /** @deprecated Use {@link #physicalCurvePercentAt(int, double)}; kept for callers/UI. */
    @Deprecated
    public float effectiveCurvatureIntensity() {
        return physicalCurvePercentAt(circumference, 64.0);
    }

    /** @deprecated Use {@link #physicalCurvePercentAt(int, double)}. */
    @Deprecated
    public static float effectiveCurvatureIntensityFor(int circumferenceBlocks) {
        return physicalCurvePercentAt(circumferenceBlocks, 64.0);
    }

    public boolean isCompleteCoverage() {
        return worldGenStyle == WorldGenStyle.COMPLETE;
    }

    /** Snap to the nearest allowed circumference step. */
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
        int circumference = PlanetWorldConfig.PLANET_CIRCUMFERENCE.getAsInt();
        return new PlanetSettings(
                circumference,
                physicalCurvePercentAt(circumference, 64.0),
                PlanetWorldConfig.ENABLE_LOCALIZED_TIME.getAsBoolean(),
                PlanetWorldConfig.ENABLE_LOCALIZED_WEATHER.getAsBoolean(),
                PlanetWorldConfig.ENABLE_ENTITY_WRAP.getAsBoolean(),
                PlanetWorldConfig.ENABLE_CURVATURE_SHADER.getAsBoolean(),
                WorldGenStyle.NORMAL
        );
    }

    public double halfCircumference() {
        return circumference;
    }

    public int chunkWidth() {
        return Math.max(1, circumference / 8);
    }

    public PlanetSettings withCircumference(int value) {
        return new PlanetSettings(value, curvatureIntensity, localizedTime, localizedWeather, entityWrap, curvatureShader, worldGenStyle);
    }

    public PlanetSettings withCurvatureIntensity(float value) {
        return new PlanetSettings(circumference, value, localizedTime, localizedWeather, entityWrap, curvatureShader, worldGenStyle);
    }

    public PlanetSettings withLocalizedTime(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, value, localizedWeather, entityWrap, curvatureShader, worldGenStyle);
    }

    public PlanetSettings withLocalizedWeather(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, value, entityWrap, curvatureShader, worldGenStyle);
    }

    public PlanetSettings withEntityWrap(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, localizedWeather, value, curvatureShader, worldGenStyle);
    }

    public PlanetSettings withCurvatureShader(boolean value) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, localizedWeather, entityWrap, value, worldGenStyle);
    }

    public PlanetSettings withWorldGenStyle(WorldGenStyle style) {
        return new PlanetSettings(circumference, curvatureIntensity, localizedTime, localizedWeather, entityWrap, curvatureShader, style);
    }
}
