package com.planetworld.time;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.wrap.WrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Spatial time / sky authority for a globe-like wrapped Overworld:
 * <ul>
 *   <li>Longitude (X): east/west time zones on the fixed solar ring.</li>
 *   <li>Meridian (Z): continuous viewpoint tip + observer-longitude as you walk
 *       over poles (far face at {@code C = P/2} is opposite day; home again at {@code P}).</li>
 *   <li>Orbital obliquity: fixed ~{@link #ORBITAL_OBLIQUITY_DEGREES}° lean of the shared ring.</li>
 * </ul>
 * Planet-fixed sun stays on one ring from shared {@code dayTime}. Walking only changes
 * the observer frame (tip + meridian longitude) — never a hard antipode jump.
 * <p>
 * Chunk sky-light is still global, so sun-burn and monster spawn must use these helpers
 * instead of {@code Level.isDay()} / {@code LightLayer.SKY} alone.
 */
public final class LocalTime {
	public static final long DAY_LENGTH = 24000L;

	/** Light level vanilla requires for crops and saplings to advance. */
	private static final int GROWTH_LIGHT_LEVEL = 9;

	/**
	 * Tip per meridian half-period (equator→pole). South (+Z) raises tip so the path
	 * lowers toward the north.
	 */
	public static final float POLE_TIP_DEGREES = 90.0f;

	/**
	 * Observer-longitude advance per tip turn, as a fraction of the day.
	 * {@code tipTurns = 2} (far equator, {@code Z = C = P/2}) → +½ day.
	 */
	public static final float MERIDIAN_LONGITUDE_PER_TURN = 0.25f;

	/**
	 * Fixed lean of the shared sun/moon orbital plane (degrees), separate from geographic tip.
	 */
	public static final float ORBITAL_OBLIQUITY_DEGREES = 5.0f;

	/** @deprecated use {@link #POLE_TIP_DEGREES}. */
	@Deprecated
	public static final float POLE_TIP_BASE_DEGREES = POLE_TIP_DEGREES;

	/** @deprecated seasonal pole swing removed; obliquity is fixed. */
	@Deprecated
	public static final float POLE_TIP_SEASON_SWING = 0.0f;

	/** @deprecated use {@link #POLE_TIP_DEGREES}. */
	@Deprecated
	public static final float LATITUDE_TILT_DEGREES = POLE_TIP_DEGREES;

	/** Blend gameplay day/night from longitude toward seasonal polar day above this |lat|. */
	private static final float POLAR_EXPOSURE_START = 0.55f;
	/** Stronger day/night length dilation above this |lat|. */
	private static final float POLAR_DILATION_START = 0.70f;

	private LocalTime() {
	}

	public static long globalTime(Level level) {
		return level.getDayTime();
	}

	/**
	 * Longitude-only local ticks (east/west time zones). Prefer
	 * {@link #observerLocalTimeTicks(Level, double, double)} when meridian matters.
	 */
	public static long localTime(Level level, double x) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return globalTime(level);
		}
		double fullWidth = periodBlocksX(level);
		double wrappedX = wrapX(level, x);
		double normalized = wrappedX / fullWidth; // −0.5 .. 0.5
		long offset = Math.round(normalized * DAY_LENGTH);
		return Math.floorMod(globalTime(level) + offset, DAY_LENGTH);
	}

	public static long localTime(Entity entity) {
		return observerLocalTimeTicks(entity);
	}

	/**
	 * Observer solar ticks: world clock + X longitude + continuous meridian longitude.
	 * Meridian offset is {@code tipTurns × ¼ day} so {@code Z = C} (far equator) is
	 * opposite day to {@code Z = 0}, and {@code Z = 2C = P} matches home again.
	 */
	public static long observerLocalTimeTicks(Level level, double x, double continuousZ) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return globalTime(level);
		}
		long ticks = localTime(level, x);
		double tipTurns = MeridianTracker.tipTurnsForPeriod(continuousZ, periodBlocksZ(level));
		long meridianOffset = Math.round(tipTurns * MERIDIAN_LONGITUDE_PER_TURN * (double) DAY_LENGTH);
		return Math.floorMod(ticks + meridianOffset, DAY_LENGTH);
	}

	public static long observerLocalTimeTicks(Entity entity) {
		return observerLocalTimeTicks(entity.level(), entity.getX(), continuousZFor(entity));
	}

	/** Celestial angle 0..1 for an observer at longitude {@code x} and continuous meridian Z. */
	public static float observerCelestialAngle(Level level, double x, double continuousZ) {
		return celestialAngleFromTicks(observerLocalTimeTicks(level, x, continuousZ));
	}

	public static float observerCelestialAngle(Entity entity) {
		return observerCelestialAngle(entity.level(), entity.getX(), continuousZFor(entity));
	}

	/**
	 * How “equatorial” day/night still applies (1 at equator → 0 at poles).
	 * Poles use seasonal elevation instead of locking the sun underfoot/overhead.
	 */
	public static float latitudeDayFactor(Level level, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 1.0f;
		}
		double lat = SeasonAuthority.latitude(level, z);
		return Mth.clamp((float) Math.cos(lat * Math.PI * 0.5), 0.0f, 1.0f);
	}

	/** Prefer the live dimension transformer period so C=256 worlds don't use a stale config width. */
	private static double periodBlocksX(Level level) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.X.domainLength;
		}
		return PlanetWorldConfig.planetCircumference() * 2.0;
	}

	static double periodBlocksZ(Level level) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.Z.domainLength;
		}
		return com.planetworld.worldgen.ContinentalClimate.periodBlocks();
	}

	private static double wrapX(Level level, double x) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.X.wrap(x);
		}
		return WrapMath.wrapX(x);
	}

	static double wrapZ(Level level, double z) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.Z.wrap(z);
		}
		return wrapToSignedHalf(z, periodBlocksZ(level));
	}

	private static double wrapToSignedHalf(double value, double width) {
		if (!(width > 1.0e-3)) {
			return value;
		}
		double half = width * 0.5;
		value = ((value + half) % width + width) % width - half;
		if (value >= half) {
			value -= width;
		}
		return value;
	}

	/**
	 * Update meridian unwrap for {@code entity} and return continuous Z.
	 */
	public static double continuousZFor(Entity entity) {
		Level level = entity.level();
		double period = periodBlocksZ(level);
		double wrapped = wrapZ(level, entity.getZ());
		return MeridianTracker.continuousZ(entity.getUUID(), wrapped, period);
	}

	public static boolean isDay(Level level, double x, double z) {
		return sunExposure(level, x, z) > 0.18f;
	}

	public static boolean isDay(Entity entity) {
		return sunExposure(entity) > 0.18f;
	}

	public static boolean isDay(Level level, double x) {
		return isDay(level, x, 0.0);
	}

	public static boolean isNight(Level level, double x, double z) {
		return !isDay(level, x, z);
	}

	public static boolean isNight(Level level, double x) {
		return isNight(level, x, 0.0);
	}

	/** True when sunlight is strong enough for undead to burn. */
	public static boolean isSunBurnTime(Level level, double x, double z) {
		return isDay(level, x, z);
	}

	public static boolean isSunBurnTime(Entity entity) {
		return isDay(entity);
	}

	public static boolean isSunBurnTime(Level level, double x) {
		return isSunBurnTime(level, x, 0.0);
	}

	/**
	 * 0..1 sun strength for an entity (longitude day curve × latitude altitude + polar rules).
	 */
	public static float sunExposure(Entity entity) {
		Level level = entity.level();
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return equatorialSunBrightness(level, entity.getX(), entity.getZ());
		}
		double continuousZ = continuousZFor(entity);
		return sunExposureAt(level, entity.getX(), continuousZ);
	}

	/**
	 * 0..1 sun strength at block coordinates.
	 * <p>
	 * Without an entity track uses wrapped Z as continuous Z. Prefer
	 * {@link #sunExposure(Entity)} for movers so polar tip stays continuous.
	 */
	public static float sunExposure(Level level, double x, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return equatorialSunBrightness(level, x, z);
		}
		double continuousZ = wrapZ(level, z);
		return sunExposureAt(level, x, continuousZ);
	}

	/**
	 * Day/night from observer angle (X + meridian longitude) attenuated toward the horizon
	 * at the poles. Far equator ({@code tipTurns = 2}, {@code Z = C}) is opposite day to home.
	 */
	public static float sunExposureAt(Level level, double x, double continuousZ) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return brightnessFromTicks(localTime(level, x));
		}
		double period = periodBlocksZ(level);
		double quarter = MeridianTracker.quarterPeriod(period);
		double tipTurns = MeridianTracker.tipTurns(continuousZ, quarter);
		float dayCurve = brightnessFromTicks(observerLocalTimeTicks(level, x, continuousZ));
		float spin = dayCurve * 2.0f - 1.0f;
		// |cos|: 1 at both equators, 0 at poles (ring on horizon).
		float altitude = (float) Math.abs(Math.cos(tipTurns * Math.PI * 0.5));
		float equatorial = 0.5f + 0.5f * spin * altitude;

		double lat = MeridianTracker.latitude(continuousZ, quarter);
		float absLat = (float) Math.abs(lat);
		float warmth = SeasonAuthority.warmthAtLatitude(level, lat);

		float dilateBlend = Mth.clamp((absLat - POLAR_DILATION_START) / (1.0f - POLAR_DILATION_START), 0.0f, 1.0f);
		if (dilateBlend > 0.0f) {
			equatorial = dilateDayNight(equatorial, warmth, dilateBlend);
		}

		float polarBlend = Mth.clamp((absLat - POLAR_EXPOSURE_START) / (1.0f - POLAR_EXPOSURE_START), 0.0f, 1.0f);
		if (polarBlend <= 0.0f) {
			return equatorial;
		}
		float polar = Mth.clamp(0.5f + warmth * 0.65f, 0.0f, 1.0f);
		return Mth.lerp(polarBlend, equatorial, polar);
	}

	/**
	 * Stretch local winter nights / summer days near the poles without stopping the orbit.
	 * Winter ({@code warmth < 0}): darken more of the day curve. Summer: brighten more.
	 */
	private static float dilateDayNight(float brightness, float warmth, float blend) {
		float strength = blend * Math.abs(warmth);
		if (strength < 0.01f) {
			return brightness;
		}
		double exponent = 1.0 + strength * 1.6;
		if (warmth < 0.0f) {
			return (float) Math.pow(brightness, exponent);
		}
		return 1.0f - (float) Math.pow(1.0 - brightness, exponent);
	}

	private static float equatorialSunBrightness(Level level, double x, double z) {
		return brightnessFromTicks(localTime(level, x));
	}

	private static float brightnessFromTicks(long ticks) {
		float angle = celestialAngleFromTicks(ticks);
		return (float) Mth.clamp(Math.cos(angle * Math.PI * 2.0) * 2.0 + 0.5, 0.0, 1.0);
	}

	public static float sunExposure(Level level, double x) {
		return sunExposure(level, x, 0.0);
	}

	/**
	 * Monster surface/cave darkness using local day at {@code pos} rather than global sky light.
	 */
	public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		Level world = level.getLevel();
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(world)) {
			return net.minecraft.world.entity.monster.Monster.isDarkEnoughToSpawn(level, pos, random);
		}

		boolean localDay = isDay(world, pos.getX(), pos.getZ());
		boolean openSky = level.canSeeSky(pos);

		if (localDay && openSky) {
			return false;
		}

		DimensionType dimension = level.dimensionType();
		int blockLightLimit = dimension.monsterSpawnBlockLightLimit();
		if (blockLightLimit < 15 && level.getBrightness(LightLayer.BLOCK, pos) > blockLightLimit) {
			return false;
		}

		int brightness = spawnBrightness(level, pos, localDay, openSky);
		return brightness <= dimension.monsterSpawnLightTest().sample(random);
	}

	private static int spawnBrightness(ServerLevelAccessor level, BlockPos pos, boolean localDay, boolean openSky) {
		if (!localDay && openSky) {
			int block = level.getBrightness(LightLayer.BLOCK, pos);
			return level.getLevel().isThundering() ? Math.max(block, 10) : block;
		}
		return level.getLevel().isThundering()
				? level.getMaxLocalRawBrightness(pos, 10)
				: level.getMaxLocalRawBrightness(pos);
	}

	public static long ticksUntilLocalDawn(Level level, double sleeperX) {
		long local = localTime(level, sleeperX);
		return Math.floorMod(-local, DAY_LENGTH);
	}

	public static long ticksUntilLocalDusk(Level level, double sleeperX) {
		long local = localTime(level, sleeperX);
		if (local <= 12000L) {
			return 12000L - local;
		}
		return DAY_LENGTH - local + 12000L;
	}

	public static long ticksUntilLocalSleepTarget(Level level, double sleeperX) {
		return isDay(level, sleeperX, 0.0)
				? ticksUntilLocalDusk(level, sleeperX)
				: ticksUntilLocalDawn(level, sleeperX);
	}

	public static boolean holdsBackSunlitGrowth(Level level, BlockPos lightPos) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return false;
		}
		if (isDay(level, lightPos.getX(), lightPos.getZ())) {
			return false;
		}
		return level.getBrightness(LightLayer.BLOCK, lightPos) < GROWTH_LIGHT_LEVEL;
	}

	/** Shared world celestial angle 0..1 from {@code dayTime} (no longitude offset). */
	public static float worldCelestialAngle(Level level) {
		return celestialAngleFromTicks(Math.floorMod(globalTime(level), DAY_LENGTH));
	}

	/**
	 * Celestial angle 0..1 for sky rendering at longitude {@code x} (near-face / no meridian).
	 */
	public static float celestialAngle(Level level, double x) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return worldCelestialAngle(level);
		}
		return celestialAngleFromTicks(localTime(level, x));
	}

	/**
	 * Observer celestial angle including meridian longitude from wrapped {@code z}
	 * (no path unwrap). Movers should use {@link #observerCelestialAngle(Entity)}.
	 */
	public static float celestialAngle(Level level, double x, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return worldCelestialAngle(level);
		}
		return observerCelestialAngle(level, x, wrapZ(level, z));
	}

	/**
	 * Sky tip from folded geographic latitude (triangle). Tip returns to ~0° at both
	 * equators so meridian-longitude +½ day can put the far face in opposite day
	 * without a 180° tip double-flip.
	 * <p>
	 * On the far face, tip sign is inverted: local solar longitude is already shifted
	 * by ~½ day, so the same tip sign would lower the sun the wrong way in the sky
	 * (e.g. walking from far equator {@code Z=-C} toward the north pole {@code Z=-C/2}).
	 * Applied around pose-stack Z after {@code YP(-90)} and before {@code XP(time)}.
	 *
	 * @param quarterPeriod equator→pole distance ({@code P/4})
	 */
	public static float celestialTiltDegrees(Level level, double continuousZ, double quarterPeriod) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 0.0f;
		}
		double lat = MeridianTracker.latitude(continuousZ, quarterPeriod);
		float tip = (float) (lat * POLE_TIP_DEGREES);
		if (MeridianTracker.onFarFace(continuousZ, quarterPeriod)) {
			tip = -tip;
		}
		return tip + ORBITAL_OBLIQUITY_DEGREES;
	}

	/**
	 * @deprecated Folded latitude tips bounce 0→90→0 (pendulum). Use
	 * {@link #celestialTiltDegrees(Level, double, double)} with continuous Z.
	 */
	@Deprecated
	public static float celestialTiltDegrees(Level level, double latitude) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 0.0f;
		}
		double lat = Mth.clamp(latitude, -1.0, 1.0);
		return (float) (lat * POLE_TIP_DEGREES) + ORBITAL_OBLIQUITY_DEGREES;
	}

	/**
	 * @deprecated Prefer {@link #celestialTiltDegrees(Level, double, double)}.
	 */
	@Deprecated
	public static float celestialTiltDegreesFromWrappedZ(Level level, double z) {
		return celestialTiltDegrees(level, SeasonAuthority.latitude(level, z));
	}

	private static float celestialAngleFromTicks(long t) {
		double d = (t / (double) DAY_LENGTH) - 0.25;
		if (d < 0) {
			d += 1.0;
		}
		double smoothed = 1.0 - (Math.cos(d * Math.PI) + 1.0) / 2.0;
		return (float) (d * 2.0 + smoothed) / 3.0f;
	}
}
