package com.planetworld.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.planetworld.worldgen.compat.WorldgenPackIds;

/**
 * Create-world choice for which installed worldgen pack soft-deps to activate under Realism.
 * Terralith and Still Life are mutually exclusive.
 */
public enum WorldgenPackChoice {
	VANILLA,
	TERRALITH,
	STILL_LIFE,
	BLOOMING_BIOSPHERE;

	public static WorldgenPackChoice fromName(String name) {
		if (name == null || name.isBlank()) {
			return VANILLA;
		}
		try {
			return WorldgenPackChoice.valueOf(name.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return VANILLA;
		}
	}

	public static WorldgenPackChoice fromOrdinalSafe(int ordinal) {
		WorldgenPackChoice[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return VANILLA;
		}
		return values[ordinal];
	}

	/** Choices available given currently loaded mods (conflicts already filtered). */
	public static List<WorldgenPackChoice> available() {
		List<WorldgenPackChoice> list = new ArrayList<>();
		list.add(VANILLA);
		boolean terralith = WorldgenPackIds.isTerralithLoaded();
		boolean stillLife = WorldgenPackIds.isStillLifeStackLoaded();
		boolean blooming = WorldgenPackIds.isBloomingBiosphereLoaded();
		if (terralith && !stillLife) {
			list.add(TERRALITH);
		}
		if (stillLife && !terralith) {
			list.add(STILL_LIFE);
		}
		if (blooming && !stillLife && !terralith) {
			list.add(BLOOMING_BIOSPHERE);
		}
		return list;
	}

	public boolean isCurrentlyAvailable() {
		return available().contains(this);
	}

	/** Snap an invalid/unavailable choice back to VANILLA. */
	public WorldgenPackChoice sanitize() {
		return isCurrentlyAvailable() ? this : VANILLA;
	}
}
