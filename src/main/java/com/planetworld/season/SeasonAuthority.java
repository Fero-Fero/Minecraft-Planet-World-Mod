package com.planetworld.season;

import com.planetworld.time.LocalTime;
import com.planetworld.worldgen.ContinentalClimate;
import com.planetworld.wrap.WrapMath;
import com.planetworld.wrap.core.DimensionTransformer;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Season clock for axial tilt and atmosphere.
 * <ul>
 *   <li>Serene Seasons when present — continuous {@code cycleTicks / year} (default 96 days).</li>
 *   <li>Else continuous fallback year of {@link #FALLBACK_YEAR_DAYS} (10 days = 5-day half-year).</li>
 * </ul>
 * Warmth / progress drive fog, crops, and polar day rules. Orbital plane lean for the
 * sky is the fixed {@code LocalTime.ORBITAL_OBLIQUITY_DEGREES} (not this ±10° knob).
 * Motion is a smooth cosine — no teleports. Installing SS later picks up on the next
 * progress read (world load / tick).
 */
public final class SeasonAuthority {
	/** Days per half-year when SS is absent (summer↔winter one way). */
	public static final int FALLBACK_HALF_YEAR_DAYS = 5;
	/** Full fallback year length in Minecraft days. */
	public static final int FALLBACK_YEAR_DAYS = FALLBACK_HALF_YEAR_DAYS * 2;

	/**
	 * Peak celestial tilt from equator toward a pole (degrees).
	 * +north at midsummer, −south at midwinter.
	 */
	public static final float MAX_AXIAL_TILT_DEGREES = 10.0f;

	/**
	 * Year progress of northern midsummer peak (center of summer quarter).
	 * SS: early spring = 0 → midsummer ≈ 0.375 → midwinter ≈ 0.875.
	 */
	public static final float MIDSUMMER_PROGRESS = 0.375f;

	private SeasonAuthority() {
	}

	/**
	 * Northern-hemisphere season progress in {@code [0,1)}:
	 * 0=early spring, 0.25=early summer, 0.5=early autumn, 0.75=early winter.
	 * Continuous — advances every tick with dayTime / SS cycle ticks.
	 */
	public static float northernSeasonProgress(Level level) {
		if (level == null) {
			return MIDSUMMER_PROGRESS;
		}
		if (SereneSeasonsCompat.isLoaded()) {
			Float ss = SereneSeasonsBridge.trySeasonProgress(level);
			if (ss != null) {
				return ss;
			}
		}
		return fallbackSeasonProgress(level);
	}

	/**
	 * Smooth fallback year from world dayTime. Same phase layout as SS
	 * (spring→summer→autumn→winter), just much shorter (10 days default).
	 */
	public static float fallbackSeasonProgress(Level level) {
		double days = level.getDayTime() / (double) LocalTime.DAY_LENGTH;
		double progress = days / (double) FALLBACK_YEAR_DAYS;
		progress = progress - Math.floor(progress);
		return (float) progress;
	}

	/**
	 * Local season progress at Z. Minecraft {@code +Z} is south — southern hemisphere is
	 * offset by half a year from the northern calendar.
	 */
	public static float localSeasonProgress(Level level, double blockZ) {
		float north = northernSeasonProgress(level);
		double lat = latitude(level, blockZ);
		if (lat > 0.0) {
			// South (+Z): opposite
			return (north + 0.5f) % 1.0f;
		}
		return north;
	}

	/**
	 * Warmth in {@code [-1,1]}: +1 midsummer, -1 midwinter at this latitude.
	 * Continuous cosine of year progress.
	 */
	public static float localWarmth(Level level, double blockZ) {
		return warmthFromProgress(localSeasonProgress(level, blockZ));
	}

	/**
	 * Warmth in {@code [-1,1]} at an explicit latitude (south {@code lat > 0} is opposite season).
	 * Prefer this with {@link com.planetworld.time.MeridianTracker} latitude so polar
	 * wrap crossings do not flip summer↔winter atmosphere.
	 */
	public static float warmthAtLatitude(Level level, double latitude) {
		float north = northernSeasonProgress(level);
		float progress = latitude > 0.0 ? (north + 0.5f) % 1.0f : north;
		return warmthFromProgress(progress);
	}

	/** Northern-hemisphere warmth (+1 midsummer, −1 midwinter). */
	public static float northernWarmth(Level level) {
		return warmthFromProgress(northernSeasonProgress(level));
	}

	private static float warmthFromProgress(float progress) {
		// cos(2π · (progress − midsummer)) → +1 at midsummer, −1 at midwinter
		return (float) Math.cos((progress - MIDSUMMER_PROGRESS) * Math.PI * 2.0);
	}

	public static boolean isLocalWinter(Level level, double blockZ) {
		return localWarmth(level, blockZ) < -0.2f;
	}

	public static boolean isLocalSummer(Level level, double blockZ) {
		return localWarmth(level, blockZ) > 0.2f;
	}

	/**
	 * Random-tick growth speed for sunlit plants. Local summer → {@code 1.25} (25% faster),
	 * else {@code 1.0}. Callers still apply night hold-back separately.
	 */
	public static float localGrowthMultiplier(Level level, double blockZ) {
		return isLocalSummer(level, blockZ) ? 1.25f : 1.0f;
	}

	/**
	 * Bias added to latitude for sun altitude: positive = more day toward north.
	 * Northern summer → positive bias (north brighter).
	 */
	public static float axialDayBias(Level level) {
		// Keep polar day/night swing tied to the same continuous warmth curve
		return northernWarmth(level) * 0.55f;
	}

	/**
	 * Celestial-sphere axial tilt in degrees for the northern calendar:
	 * {@code +MAX} at midsummer (toward north), {@code -MAX} at midwinter (toward south).
	 * Glides continuously; at summer→autumn the value is already falling toward south.
	 */
	public static float northernAxialTiltDegrees(Level level) {
		return northernWarmth(level) * MAX_AXIAL_TILT_DEGREES;
	}

	/**
	 * Jump the northern calendar to a season quarter (debug). Uses Serene Seasons when
	 * installed; otherwise adjusts {@code dayTime} on the fallback 10-day year.
	 *
	 * @return {@code true} when SS cycle ticks were set directly
	 */
	public static boolean setNorthernSeason(Level level, com.planetworld.debug.SeasonQuarter quarter) {
		float progress = quarter.northernProgress();
		if (SereneSeasonsCompat.isLoaded() && SereneSeasonsBridge.trySetNorthernSeasonProgress(level, progress)) {
			return true;
		}
		if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
			return false;
		}
		long yearTicks = (long) FALLBACK_YEAR_DAYS * LocalTime.DAY_LENGTH;
		long target = (long) (progress * yearTicks);
		long dayTime = server.getDayTime();
		long base = (dayTime / yearTicks) * yearTicks;
		server.setDayTime(base + target);
		return false;
	}

	public static double latitude(Level level, double blockZ) {
		if (level != null && WrapMath.isWrappedDimension(level)) {
			DimensionTransformer t = level.getTransformer();
			double period;
			double z = blockZ;
			if (t != null && t.isWrapped()) {
				period = t.Coord.Z.domainLength;
				z = t.Coord.Z.wrap(blockZ);
			} else {
				period = ContinentalClimate.periodBlocks();
				z = ContinentalClimate.wrapToSignedHalf(blockZ, period, period * 0.5);
			}
			return Mth.clamp(com.planetworld.time.MeridianTracker.latitudeForPeriod(z, period), -1.0, 1.0);
		}
		return ContinentalClimate.latitude(blockZ);
	}
}
