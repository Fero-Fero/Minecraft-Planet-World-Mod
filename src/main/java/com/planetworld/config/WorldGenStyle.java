package com.planetworld.config;

/**
 * Overworld generation style chosen at world creation.
 * NORMAL is vanilla climate/structure density (with wrap-safe clamps as needed).
 * COMPLETE reserves patches so every overworld biome appears at least once and
 * tightens structure spacing so each structure set can place inside the torus.
 */
public enum WorldGenStyle {
    NORMAL,
    COMPLETE;

    public static WorldGenStyle fromName(String name) {
        if (name == null || name.isBlank()) {
            return NORMAL;
        }
        String key = name.trim().toUpperCase();
        // Migrate worlds saved with the removed Continental option.
        if ("CONTINENTAL".equals(key)) {
            return COMPLETE;
        }
        try {
            return WorldGenStyle.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return NORMAL;
        }
    }

    public static WorldGenStyle fromOrdinalSafe(int ordinal) {
        WorldGenStyle[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return NORMAL;
        }
        return values[ordinal];
    }
}
