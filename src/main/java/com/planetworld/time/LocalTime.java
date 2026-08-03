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
 * Spatial time authority:
 * <ul>
 *   <li>Longitude (X): phase of the day — walk east/west to change local time of day.</li>
 *   <li>Latitude (Z): sun altitude — both poles approach eternal night; equator has a full day cycle.</li>
 * </ul>
 * Each client uses the local player's position, so different players see different suns.
 * <p>
 * Chunk sky-light is still global, so sun-burn and monster spawn must use these helpers
 * instead of {@code Level.isDay()} / {@code LightLayer.SKY} alone.
 */
public final class LocalTime {
	public static final long DAY_LENGTH = 24000L;

	/** Light level vanilla requires for crops and saplings to advance. */
	private static final int GROWTH_LIGHT_LEVEL = 9;

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
	 * 1 at the equator (adjusted by seasonal axial tilt), ~0 at the winter pole.
	 */
	public static float latitudeDayFactor(Level level, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 1.0f;
		}
		double lat = SeasonAuthority.latitude(level, z);
		float base = Mth.clamp((float) Math.cos(lat * Math.PI * 0.5), 0.0f, 1.0f);
		float bias = SeasonAuthority.axialDayBias(level);
		// Shift effective latitude toward the summer pole
		double shifted = Mth.clamp(lat - bias, -1.0, 1.0);
		float seasonal = Mth.clamp((float) Math.cos(shifted * Math.PI * 0.5), 0.0f, 1.0f);
		return Mth.clamp(base * 0.35f + seasonal * 0.65f, 0.0f, 1.0f);
	}

	/** Prefer the live dimension transformer period so C=256 worlds don't use a stale config width. */
	private static double periodBlocksX(Level level) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.X.domainLength;
		}
		return PlanetWorldConfig.planetCircumference() * 2.0;
	}

	private static double periodBlocksZ(Level level) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.Z.domainLength;
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

	private static double wrapZ(Level level, double z) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.Z.wrap(z);
		}
		// Same square-torus period as X when no live transformer.
		double width = PlanetWorldConfig.planetCircumference() * 2.0;
		double half = width / 2.0;
		z = ((z + half) % width + width) % width - half;
		if (z >= half) {
			z -= width;
		}
		return z;
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
	 * 0..1 sun strength at (x, z). Longitude sets phase; latitude scales altitude
	 * so poles stay near-dark.
	 */
	public static float sunExposure(Level level, double x, double z) {
		double brightness = Mth.clamp(
				Math.cos(celestialAngle(level, x, z) * Math.PI * 2.0) * 2.0 + 0.5,
				0.0,
				1.0
		);
		return (float) brightness;
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

	/** Celestial angle 0..1 for sky rendering at longitude {@code x} (equator day factor). */
	public static float celestialAngle(Level level, double x) {
		return celestialAngle(level, x, 0.0);
	}

	/**
	 * Celestial angle at (x, z). Longitude drives the day clock; latitude pulls the sun
	 * toward midnight so poles approach eternal night. Per-player via the viewing position.
	 */
	public static float celestialAngle(Level level, double x, double z) {
		float base = celestialAngleFromTicks(localTime(level, x));
		float dayFactor = latitudeDayFactor(level, z);
		// Square softens mid-latitudes; poles (dayFactor→0) lock near midnight (0.5).
		float towardNight = 1.0f - dayFactor * dayFactor;
		return Mth.lerp(towardNight, base, 0.5f);
	}

	/**
	 * Degrees to tilt the celestial sphere toward the opposite pole from latitude,
	 * plus a gentle seasonal axial tilt (±{@link SeasonAuthority#MAX_AXIAL_TILT_DEGREES}):
	 * midsummer nudges the sun north, midwinter south — continuous, no snaps.
	 */
	public static float celestialTiltDegrees(Level level, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 0.0f;
		}
		double lat = SeasonAuthority.latitude(level, z);
		float seasonal = SeasonAuthority.northernAxialTiltDegrees(level);
		return (float) (-lat * 72.0) + seasonal;
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
