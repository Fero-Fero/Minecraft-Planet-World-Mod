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
        WorldGenStyle worldGenStyle
) {
    public static final int MIN_CIRCUMFERENCE = 256;
    public static final int MAX_CIRCUMFERENCE = 102_400;
    /** Continental climate is only offered at or above this half-period size. */
    public static final int MIN_CONTINENTAL_CIRCUMFERENCE = 2048;

    /** Discrete slider steps: 256, 512, … 65536, then 102400. */
    public static final int[] CIRCUMFERENCE_STEPS = {
            256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 102_400
    };

    public PlanetSettings {
        circumference = snapCircumference(circumference);
        worldGenStyle = worldGenStyle == null ? WorldGenStyle.NORMAL : worldGenStyle;
        if (circumference < MIN_CONTINENTAL_CIRCUMFERENCE) {
            worldGenStyle = WorldGenStyle.NORMAL;
        }
        // Persist the auto-derived intensity so NBT/config stay consistent with runtime.
        curvatureIntensity = effectiveCurvatureIntensityFor(circumference);
    }

    /**
     * Display helper: physical curve percent at 64 blocks ({@code 100 * d / (2R)}).
     * Shader uses {@code drop = d^2/(2R)} directly (no intensity boost).
     */
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

    public boolean allowsContinental() {
        return circumference >= MIN_CONTINENTAL_CIRCUMFERENCE;
    }

    public boolean isContinental() {
        return worldGenStyle == WorldGenStyle.CONTINENTAL && allowsContinental();
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
                effectiveCurvatureIntensityFor(circumference),
                PlanetWorldConfig.ENABLE_LOCALIZED_TIME.getAsBoolean(),
                PlanetWorldConfig.ENABLE_LOCALIZED_WEATHER.getAsBoolean(),
                PlanetWorldConfig.ENABLE_ENTITY_WRAP.getAsBoolean(),
                PlanetWorldConfig.ENABLE_CURVATURE_SHADER.getAsBoolean(),
                WorldGenStyle.NORMAL
        );
    }

    public double halfCircumference() {
        // `circumference` in UI/config is treated as the *half-period* (what you
        // asked for: e.g. seam effects around x=±256 inside a 512-wide world).
        return circumference;
    }

    public int chunkWidth() {
        // `chunkWidth()` is the full torus width in chunks.
        // fullBlocks = 2*circumference, so fullChunks = (2*circumference)/16 = circumference/8
        return Math.max(1, circumference / 8);
    }

    public PlanetSettings withCircumference(int value) {
        return new PlanetSettings(value, curvatureIntensity, localizedTime, localizedWeather, entityWrap, curvatureShader, worldGenStyle);
    }

    public PlanetSettings withCurvatureIntensity(float value) {
        // Intensity is auto-derived; keep API for callers but ignore manual value.
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
