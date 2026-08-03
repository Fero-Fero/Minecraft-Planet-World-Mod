package com.planetworld.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Global defaults in {@code planetworld-common.toml}.
 * Active per-world values come from {@link PlanetSettingsAccess} (world create UI / saved data / sync).
 */
public final class PlanetWorldConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final int DEFAULT_CIRCUMFERENCE = 8192;
    static final boolean DEFAULT_CURVATURE_SHADER = true;
    static final boolean DEFAULT_LOCALIZED_TIME = true;
    static final boolean DEFAULT_LOCALIZED_WEATHER = true;
    static final boolean DEFAULT_ENTITY_WRAP = true;

    public static final ModConfigSpec.IntValue PLANET_CIRCUMFERENCE = BUILDER
            .comment("Default planet circumference when Customize is skipped. Snapped to: 256, 512, … 65536, 102400.")
            .defineInRange("planet_circumference", DEFAULT_CIRCUMFERENCE, PlanetSettings.MIN_CIRCUMFERENCE, PlanetSettings.MAX_CIRCUMFERENCE);

    public static final ModConfigSpec.BooleanValue ENABLE_CURVATURE_SHADER = BUILDER
            .comment("Default: enable Animal Crossing-style horizon curvature on the client.")
            .define("enable_curvature_shader", DEFAULT_CURVATURE_SHADER);

    public static final ModConfigSpec.DoubleValue CURVATURE_INTENSITY = BUILDER
            .comment("Legacy unused: curvature intensity is derived as circumference/360 (clamped 0.25–12). Kept for config file compatibility.")
            .defineInRange("curvature_intensity", 1.0, 0.1, 10.0);

    public static final ModConfigSpec.BooleanValue ENABLE_LOCALIZED_TIME = BUILDER
            .comment("Default: map time of day to X coordinate.")
            .define("enable_localized_time", DEFAULT_LOCALIZED_TIME);

    public static final ModConfigSpec.BooleanValue ENABLE_LOCALIZED_WEATHER = BUILDER
            .comment("Default: restrict rain/snow to localized X bands.")
            .define("enable_localized_weather", DEFAULT_LOCALIZED_WEATHER);

    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_WRAP = BUILDER
            .comment("Reserved legacy toggle (no border teleport). Kept for per-world settings compatibility.")
            .define("enable_entity_wrap", DEFAULT_ENTITY_WRAP);

    public static final ModConfigSpec.BooleanValue ENABLE_TERRALITH_SEEDS = BUILDER
            .comment("When Terralith is installed: place sparse Terralith surface biome seeds on Realism worlds (circumference ≥2048).")
            .define("enable_terralith_seeds", true);

    public static final ModConfigSpec.BooleanValue INCLUDE_FANTASY_TERRALITH_BIOMES = BUILDER
            .comment("When Terralith seeds are enabled: include fantasy / skylands / magical biomes in the seed catalog.")
            .define("include_fantasy_terralith_biomes", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PlanetWorldConfig() {
    }

    /**
     * Config values only exist once the loader has read the file, but vanilla builds world presets
     * (and therefore chunk generators) during registry bootstrap, before that happens. Ask the spec
     * first and fall back to the same defaults the spec declares.
     */
    static boolean configLoaded() {
        return SPEC.isLoaded();
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

    public static float curvatureIntensity() {
        return PlanetSettingsAccess.get().effectiveCurvatureIntensity();
    }

    public static WorldGenStyle worldGenStyle() {
        return PlanetSettingsAccess.get().worldGenStyle();
    }

    public static boolean isRealism() {
        return PlanetSettingsAccess.get().isRealism();
    }

    /** @deprecated Use {@link #isRealism()}. */
    @Deprecated
    public static boolean isContinental() {
        return isRealism();
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

    public static WorldgenPackChoice worldgenPackChoice() {
        return PlanetSettingsAccess.get().worldgenPackChoice();
    }

    public static boolean enableTerralithSeeds() {
        return !configLoaded() || ENABLE_TERRALITH_SEEDS.get();
    }

    public static boolean includeFantasyTerralithBiomes() {
        return !configLoaded() || INCLUDE_FANTASY_TERRALITH_BIOMES.get();
    }
}
