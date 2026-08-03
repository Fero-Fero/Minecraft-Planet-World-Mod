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
 *   <li>Longitude (X): phase of the day — walk east/west to change local solar time.</li>
 *   <li>Latitude (Z): tips the celestial sphere so the sun’s equatorial orbit rises toward the
 *       horizon at the poles (Minecraft: {@code +Z} south, {@code -Z} north).</li>
 *   <li>Season: pole tip swings 70–80°; equator gets ±{@link SeasonAuthority#MAX_AXIAL_TILT_DEGREES}° lean.</li>
 * </ul>
 * Each client uses the local player’s position, so different players see different suns.
 * <p>
 * Chunk sky-light is still global, so sun-burn and monster spawn must use these helpers
 * instead of {@code Level.isDay()} / {@code LightLayer.SKY} alone.
 */
public final class LocalTime {
	public static final long DAY_LENGTH = 24000L;

	/** Light level vanilla requires for crops and saplings to advance. */
	private static final int GROWTH_LIGHT_LEVEL = 9;

	/**
	 * Base tip at the poles before seasonal adjust. N-pole summer → 70°, winter → 80°
	 * via {@code tip = 75 - northernWarmth * 5}.
	 */
	public static final float POLE_TIP_BASE_DEGREES = 75.0f;
	/** Seasonal swing of pole tip magnitude (degrees). */
	public static final float POLE_TIP_SEASON_SWING = 5.0f;

	/** @deprecated use {@link #POLE_TIP_BASE_DEGREES}; kept for call-site clarity. */
	@Deprecated
	public static final float LATITUDE_TILT_DEGREES = POLE_TIP_BASE_DEGREES;

	/** Blend gameplay day/night from longitude toward seasonal polar day above this |lat|. */
	private static final float POLAR_EXPOSURE_START = 0.55f;
	/** Stronger day/night length dilation above this |lat|. */
	private static final float POLAR_DILATION_START = 0.70f;

	private LocalTime() {
	}

	public static long globalTime(Level level) {
		return level.getDayTime();
	}

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
		return localTime(entity.level(), entity.getX());
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

	private static double wrapX(Level level, double x) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.X.wrap(x);
		}
		return WrapMath.wrapX(x);
	}

	public static boolean isDay(Level level, double x, double z) {
		return sunExposure(level, x, z) > 0.18f;
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

	public static boolean isSunBurnTime(Level level, double x) {
		return isSunBurnTime(level, x, 0.0);
	}

	/**
	 * 0..1 sun strength. Equator: longitude phase. Poles: seasonal path above/below horizon
	 * (summer pole stays lit, winter pole stays dark) while the disc still circles visually.
	 * Near poles, day/night length is dilated so local winter nights last longer.
	 */
	public static float sunExposure(Level level, double x, double z) {
		float equatorial = equatorialSunBrightness(level, x);
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return equatorial;
		}
		double lat = SeasonAuthority.latitude(level, z);
		float absLat = (float) Math.abs(lat);
		float warmth = SeasonAuthority.localWarmth(level, z);

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

	private static float equatorialSunBrightness(Level level, double x) {
		float angle = celestialAngleFromTicks(localTime(level, x));
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

	/** Celestial angle 0..1 from global dayTime — same sun/moon for every player. */
	public static float worldCelestialAngle(Level level) {
		return celestialAngleFromTicks(Math.floorMod(globalTime(level), DAY_LENGTH));
	}

	/** Celestial angle 0..1 for sky rendering at longitude {@code x}. */
	public static float celestialAngle(Level level, double x) {
		return celestialAngle(level, x, 0.0);
	}

	/**
	 * Longitude-local celestial angle (gameplay helpers). Sky rendering uses
	 * {@link #worldCelestialAngle(Level)} so all players share one sun/moon.
	 */
	public static float celestialAngle(Level level, double x, double z) {
		return celestialAngleFromTicks(localTime(level, x));
	}

	/**
	 * Degrees to tip the celestial sphere for a continuous meridian latitude + season.
	 * Applied around sky pose-stack <em>Z</em> after {@code YP(-90)} and before {@code XP(time)}.
	 * <p>
	 * Pass latitude from {@link com.planetworld.render.ContinuousMeridian} on the client so
	 * crossing the polar wrap seam does not flip the tip (+1 ↔ −1).
	 * <ul>
	 *   <li>{@code lat = 0}: E–W overhead arc; ±10° equatorial seasonal lean only.</li>
	 *   <li>{@code lat = -1} (north pole): summer → 70°, winter → 80° tip.</li>
	 *   <li>{@code lat = +1} (south pole): mirrored via latitude sign.</li>
	 * </ul>
	 */
	public static float celestialTiltDegrees(Level level, double latitude) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 0.0f;
		}
		double lat = Mth.clamp(latitude, -1.0, 1.0);
		float warmth = SeasonAuthority.northernWarmth(level);
		float poleMagnitude = POLE_TIP_BASE_DEGREES - warmth * POLE_TIP_SEASON_SWING;
		float latTip = (float) (-lat * poleMagnitude);
		float equatorLean = warmth * SeasonAuthority.MAX_AXIAL_TILT_DEGREES * (1.0f - (float) Math.abs(lat));
		return latTip + equatorLean;
	}

	/**
	 * @deprecated Prefer {@link #celestialTiltDegrees(Level, double)} with continuous latitude.
	 * Uses wrapped {@code z/half}, which flips at the polar seam.
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
