package com.planetworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Global defaults in {@code planetworld-common.toml}.
 * Active per-world values come from {@link PlanetSettingsAccess} (world create UI / saved data / sync).
 */
public final class PlanetWorldConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PLANET_CIRCUMFERENCE = BUILDER
            .comment("Default planet circumference when Customize is skipped. Snapped to: 256, 512, … 65536, 102400.")
            .defineInRange("planet_circumference", 8192, PlanetSettings.MIN_CIRCUMFERENCE, PlanetSettings.MAX_CIRCUMFERENCE);

    public static final ModConfigSpec.BooleanValue ENABLE_CURVATURE_SHADER = BUILDER
            .comment("Default: enable physically realistic horizon curvature on the client.")
            .define("enable_curvature_shader", true);

    public static final ModConfigSpec.DoubleValue CURVATURE_INTENSITY = BUILDER
            .comment("Legacy unused: curvature is physical (drop = d^2/(2R)). Kept for config file compatibility.")
            .defineInRange("curvature_intensity", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.BooleanValue ENABLE_LOCALIZED_TIME = BUILDER
            .comment("Default: map time of day to X coordinate.")
            .define("enable_localized_time", true);

    public static final ModConfigSpec.BooleanValue ENABLE_LOCALIZED_WEATHER = BUILDER
            .comment("Default: restrict rain/snow to localized X bands.")
            .define("enable_localized_weather", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_WRAP = BUILDER
            .comment("Reserved legacy toggle (no border teleport). Kept for per-world settings compatibility.")
            .define("enable_entity_wrap", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PlanetWorldConfig() {
    }

    public static int planetCircumference() {
        return PlanetSettingsAccess.get().circumference();
    }

    public static double halfCircumference() {
        return PlanetSettingsAccess.get().halfCircumference();
    }

    public static int chunkWidth() {
        return PlanetSettingsAccess.get().chunkWidth();
    }

    public static boolean enableCurvatureShader() {
        return PlanetSettingsAccess.get().curvatureShader();
    }

    /** Unused by the shader (physical radius only); retained for API compatibility. */
    public static float curvatureIntensity() {
        return PlanetSettingsAccess.get().effectiveCurvatureIntensity();
    }

    public static WorldGenStyle worldGenStyle() {
        return PlanetSettingsAccess.get().worldGenStyle();
    }

    public static boolean isCompleteCoverage() {
        return PlanetSettingsAccess.get().isCompleteCoverage();
    }

    public static boolean enableLocalizedTime() {
        return PlanetSettingsAccess.get().localizedTime();
    }

    public static boolean enableLocalizedWeather() {
        return PlanetSettingsAccess.get().localizedWeather();
    }

    public static boolean enableEntityWrap() {
        return PlanetSettingsAccess.get().entityWrap();
    }
}
