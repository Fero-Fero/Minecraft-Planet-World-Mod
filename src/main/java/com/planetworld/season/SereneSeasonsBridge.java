package com.planetworld.season;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

/**
 * Reflection bridge to Serene Seasons API — no compile-time dependency.
 * Prefers continuous {@code seasonCycleTicks / cycleDuration} so the sun can
 * glide instead of jumping per sub-season.
 */
final class SereneSeasonsBridge {
	private static final Object LOCK = new Object();
	private static volatile boolean resolved;
	private static volatile java.lang.reflect.Method getSeasonState;
	private static volatile java.lang.reflect.Method getSeasonCycleTicks;
	private static volatile java.lang.reflect.Method getCycleDuration;

	private SereneSeasonsBridge() {
	}

	/**
	 * Continuous northern-hemisphere year progress in {@code [0,1)} aligned with SS:
	 * {@code 0} = early spring, {@code 0.25} = early summer, {@code 0.5} = early autumn,
	 * {@code 0.75} = early winter. Midsummer ≈ {@code 0.375}, midwinter ≈ {@code 0.875}.
	 */
	@Nullable
	static Float trySeasonProgress(Level level) {
		if (!SereneSeasonsCompat.isLoaded()) {
			return null;
		}
		ensureResolved();
		if (getSeasonState == null || getSeasonCycleTicks == null || getCycleDuration == null) {
			return null;
		}
		try {
			Object state = getSeasonState.invoke(null, level);
			if (state == null) {
				return null;
			}
			int ticks = ((Number) getSeasonCycleTicks.invoke(state)).intValue();
			int cycle = ((Number) getCycleDuration.invoke(state)).intValue();
			if (cycle <= 0) {
				return null;
			}
			float progress = Math.floorMod(ticks, cycle) / (float) cycle;
			if (progress >= 1.0f) {
				progress = 0.0f;
			}
			return progress;
		} catch (ReflectiveOperationException | ClassCastException ignored) {
			return null;
		}
	}

	private static void ensureResolved() {
		if (resolved) {
			// Mod may have been added after a failed resolve in the same JVM (dev hot-swap).
			if (getSeasonState == null && SereneSeasonsCompat.isLoaded()) {
				resolved = false;
			} else {
				return;
			}
		}
		synchronized (LOCK) {
			if (resolved && getSeasonState != null) {
				return;
			}
			try {
				Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
				getSeasonState = helper.getMethod("getSeasonState", Level.class);
				Class<?> state = Class.forName("sereneseasons.api.season.ISeasonState");
				getSeasonCycleTicks = state.getMethod("getSeasonCycleTicks");
				getCycleDuration = state.getMethod("getCycleDuration");
			} catch (ReflectiveOperationException ignored) {
				getSeasonState = null;
				getSeasonCycleTicks = null;
				getCycleDuration = null;
			}
			resolved = true;
		}
	}
}
