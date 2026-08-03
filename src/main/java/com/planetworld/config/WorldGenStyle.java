package com.planetworld.config;

/**
 * Overworld climate / biome layout chosen at world creation.
 * {@link #REALISM} is only valid when circumference &gt;= {@link PlanetSettings#MIN_REALISM_CIRCUMFERENCE}.
 */
public enum WorldGenStyle {
	NORMAL,
	/** Earth-like latitudinal climate, large landmasses, wide oceans (formerly CONTINENTAL). */
	REALISM;

	public static WorldGenStyle fromName(String name) {
		if (name == null || name.isBlank()) {
			return NORMAL;
		}
		String key = name.trim().toUpperCase();
		// Saved worlds / packets from before the Realism rename.
		if ("CONTINENTAL".equals(key)) {
			return REALISM;
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
