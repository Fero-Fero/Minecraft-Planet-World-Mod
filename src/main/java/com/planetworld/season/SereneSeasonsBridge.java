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
	private static volatile boolean readResolved;
	private static volatile boolean writeResolved;
	private static volatile java.lang.reflect.Method getSeasonState;
	private static volatile java.lang.reflect.Method getSeasonCycleTicks;
	private static volatile java.lang.reflect.Method getCycleDuration;
	private static volatile java.lang.reflect.Method getSeasonSavedData;
	private static volatile java.lang.reflect.Field seasonCycleTicksField;
	private static volatile java.lang.reflect.Method sendSeasonUpdate;

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
		java.lang.reflect.Method stateMethod;
		java.lang.reflect.Method cycleTicksMethod;
		java.lang.reflect.Method cycleDurationMethod;
		synchronized (LOCK) {
			ensureReadResolved();
			stateMethod = getSeasonState;
			cycleTicksMethod = getSeasonCycleTicks;
			cycleDurationMethod = getCycleDuration;
		}
		if (stateMethod == null || cycleTicksMethod == null || cycleDurationMethod == null) {
			return null;
		}
		try {
			Object state = stateMethod.invoke(null, level);
			if (state == null) {
				return null;
			}
			int ticks = ((Number) cycleTicksMethod.invoke(state)).intValue();
			int cycle = ((Number) cycleDurationMethod.invoke(state)).intValue();
			if (cycle <= 0) {
				return null;
			}
			float progress = Math.floorMod(ticks, cycle) / (float) cycle;
			if (progress >= 1.0f) {
				progress = 0.0f;
			}
			return progress;
		} catch (ReflectiveOperationException | ClassCastException | NullPointerException ignored) {
			return null;
		}
	}

	/** @return {@code true} when SS cycle ticks were updated. */
	static boolean trySetNorthernSeasonProgress(Level level, float progress) {
		if (!SereneSeasonsCompat.isLoaded()) {
			return false;
		}
		java.lang.reflect.Method stateMethod;
		java.lang.reflect.Method cycleDurationMethod;
		java.lang.reflect.Method savedDataMethod;
		java.lang.reflect.Field ticksField;
		java.lang.reflect.Method updateMethod;
		synchronized (LOCK) {
			ensureReadResolved();
			ensureWriteResolved();
			stateMethod = getSeasonState;
			cycleDurationMethod = getCycleDuration;
			savedDataMethod = getSeasonSavedData;
			ticksField = seasonCycleTicksField;
			updateMethod = sendSeasonUpdate;
		}
		if (stateMethod == null || cycleDurationMethod == null
				|| savedDataMethod == null || ticksField == null || updateMethod == null) {
			return false;
		}
		try {
			Object state = stateMethod.invoke(null, level);
			if (state == null) {
				return false;
			}
			int cycle = ((Number) cycleDurationMethod.invoke(state)).intValue();
			if (cycle <= 0) {
				return false;
			}
			float p = progress - (float) Math.floor(progress);
			int ticks = Math.floorMod((int) (p * cycle), cycle);
			Object savedData = savedDataMethod.invoke(null, level);
			if (savedData == null) {
				return false;
			}
			ticksField.setInt(savedData, ticks);
			updateMethod.invoke(null, level);
			return true;
		} catch (ReflectiveOperationException | ClassCastException | NullPointerException ignored) {
			return false;
		}
	}

	private static void ensureReadResolved() {
		if (readResolved) {
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
		readResolved = true;
	}

	private static void ensureWriteResolved() {
		if (writeResolved) {
			return;
		}
		try {
			Class<?> handler = Class.forName("sereneseasons.season.SeasonHandler");
			getSeasonSavedData = handler.getMethod("getSeasonSavedData", Level.class);
			sendSeasonUpdate = handler.getMethod("sendSeasonUpdate", Level.class);
			Class<?> savedData = Class.forName("sereneseasons.season.SeasonSavedData");
			seasonCycleTicksField = savedData.getField("seasonCycleTicks");
		} catch (ReflectiveOperationException ignored) {
			getSeasonSavedData = null;
			sendSeasonUpdate = null;
			seasonCycleTicksField = null;
		}
		writeResolved = true;
	}
}
