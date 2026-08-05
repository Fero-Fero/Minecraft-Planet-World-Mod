package com.planetworld.debug;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-dimension debug toggles for celestial / season / polar testing commands.
 */
public final class CelestialDebugState {
	public enum PolarHemisphere {
		NORTH,
		SOUTH,
		BOTH;

		public boolean matchesLatitude(double latitude) {
			return switch (this) {
				case NORTH -> latitude < -0.70;
				case SOUTH -> latitude > 0.70;
				case BOTH -> Math.abs(latitude) >= 0.70;
			};
		}
	}

	public record PolarStorm(long untilGameTime, PolarHemisphere hemisphere) {
		public boolean isActive(long gameTime) {
			return gameTime < untilGameTime;
		}

		public boolean matches(double latitude, long gameTime) {
			return isActive(gameTime) && hemisphere.matchesLatitude(latitude);
		}
	}

	private static final class DimensionState {
		double timeSpeedMultiplier = 1.0;
		double timeSpeedAccumulator;
		boolean seasonLogging;
		SeasonQuarter lastLoggedQuarter = SeasonQuarter.SPRING;
		PolarStorm polarStorm;
	}

	private static final Map<ResourceLocation, DimensionState> BY_DIMENSION = new ConcurrentHashMap<>();

	private CelestialDebugState() {
	}

	public static void clearAll() {
		BY_DIMENSION.clear();
	}

	private static DimensionState state(Level level) {
		ResourceKey<Level> key = level.dimension();
		return BY_DIMENSION.computeIfAbsent(key.location(), id -> new DimensionState());
	}

	public static double timeSpeedMultiplier(Level level) {
		return state(level).timeSpeedMultiplier;
	}

	public static void setTimeSpeedMultiplier(Level level, double multiplier) {
		DimensionState s = state(level);
		s.timeSpeedMultiplier = multiplier;
		s.timeSpeedAccumulator = 0.0;
	}

	public static double accumulateTimeSpeed(Level level) {
		DimensionState s = state(level);
		if (s.timeSpeedMultiplier <= 1.0) {
			return 0.0;
		}
		s.timeSpeedAccumulator += s.timeSpeedMultiplier - 1.0;
		long whole = (long) s.timeSpeedAccumulator;
		s.timeSpeedAccumulator -= whole;
		return whole;
	}

	public static boolean isSeasonLogging(Level level) {
		return state(level).seasonLogging;
	}

	public static void setSeasonLogging(Level level, boolean enabled) {
		state(level).seasonLogging = enabled;
	}

	public static SeasonQuarter lastLoggedQuarter(Level level) {
		return state(level).lastLoggedQuarter;
	}

	public static void setLastLoggedQuarter(Level level, SeasonQuarter quarter) {
		state(level).lastLoggedQuarter = quarter;
	}

	public static void setPolarStorm(Level level, PolarStorm storm) {
		state(level).polarStorm = storm;
	}

	public static PolarStorm polarStorm(Level level) {
		return state(level).polarStorm;
	}

	public static boolean isForcedPolarStorm(Level level, double latitude) {
		PolarStorm storm = polarStorm(level);
		return storm != null && storm.matches(latitude, level.getGameTime());
	}

	public static void clearPolarStorm(Level level) {
		state(level).polarStorm = null;
	}
}
