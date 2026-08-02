package com.planetworld.time;

import com.planetworld.config.PlanetWorldConfig;
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
 * LocalTime = (GlobalServerTime + (EntityX / WorldWidth) * 24000) % 24000
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
		double fullWidth = periodBlocks(level);
		double wrappedX = wrapX(level, x);
		double normalized = wrappedX / fullWidth; // −0.5 .. 0.5
		long offset = Math.round(normalized * DAY_LENGTH);
		return Math.floorMod(globalTime(level) + offset, DAY_LENGTH);
	}

	public static long localTime(Entity entity) {
		return localTime(entity.level(), entity.getX());
	}

	/** Prefer the live dimension transformer period so C=256 worlds don't use a stale config width. */
	private static double periodBlocks(Level level) {
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

	public static boolean isDay(Level level, double x) {
		long t = localTime(level, x);
		return t >= 0 && t < 12000;
	}

	public static boolean isNight(Level level, double x) {
		return !isDay(level, x);
	}

	/** True when sunlight is strong enough for undead to burn (vanilla: 0–12000ish with sky access). */
	public static boolean isSunBurnTime(Level level, double x) {
		return isDay(level, x);
	}

	/**
	 * 0..1 sun strength at longitude {@code x}, matching daylight-detector / sky-darken math.
	 * Use this instead of {@code getLightLevelDependentMagicValue()} when global night has
	 * zeroed the sky-light map on a locally sunny side of the planet.
	 */
	public static float sunExposure(Level level, double x) {
		double brightness = Mth.clamp(
				Math.cos(celestialAngle(level, x) * Math.PI * 2.0) * 2.0 + 0.5,
				0.0,
				1.0
		);
		return (float) brightness;
	}

	/**
	 * Monster surface/cave darkness using local day at {@code pos} rather than global sky light.
	 * <p>
	 * During global night the sky-light map is dark everywhere, so vanilla would spawn hostiles
	 * on the sunny longitude; during global day it would refuse them on the midnight longitude.
	 * Sky access + local clock replace the global {@code LightLayer.SKY} sample.
	 */
	public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		Level world = level.getLevel();
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(world)) {
			return net.minecraft.world.entity.monster.Monster.isDarkEnoughToSpawn(level, pos, random);
		}

		boolean localDay = isDay(world, pos.getX());
		boolean openSky = level.canSeeSky(pos);

		// Local noon under open sky: never dark enough, same as vanilla daytime surface.
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

	/**
	 * Sky contribution for spawn light tests: local night under open sky counts as dark even when
	 * the chunk sky-light map still holds daytime values from global noon.
	 */
	private static int spawnBrightness(ServerLevelAccessor level, BlockPos pos, boolean localDay, boolean openSky) {
		if (!localDay && openSky) {
			// Midnight surface: ignore stale daytime sky light; thunder still darkens a little.
			int block = level.getBrightness(LightLayer.BLOCK, pos);
			return level.getLevel().isThundering() ? Math.max(block, 10) : block;
		}
		// Caves, or local day without sky: vanilla max raw light.
		return level.getLevel().isThundering()
				? level.getMaxLocalRawBrightness(pos, 10)
				: level.getMaxLocalRawBrightness(pos);
	}

	/**
	 * How many global ticks to advance so the sleeper's local time becomes dawn (0).
	 */
	public static long ticksUntilLocalDawn(Level level, double sleeperX) {
		long local = localTime(level, sleeperX);
		return Math.floorMod(-local, DAY_LENGTH);
	}

	/**
	 * How many global ticks to advance so the sleeper's local time becomes dusk (12000),
	 * i.e. the start of local night.
	 */
	public static long ticksUntilLocalDusk(Level level, double sleeperX) {
		long local = localTime(level, sleeperX);
		if (local <= 12000L) {
			return 12000L - local;
		}
		// Already past dusk in the same cycle — wait until next dusk.
		return DAY_LENGTH - local + 12000L;
	}

	/**
	 * Sleep skips to the next local half-cycle: dusk when it is local day, dawn when it is local night.
	 */
	public static long ticksUntilLocalSleepTarget(Level level, double sleeperX) {
		return isDay(level, sleeperX)
				? ticksUntilLocalDusk(level, sleeperX)
				: ticksUntilLocalDawn(level, sleeperX);
	}

	/**
	 * True when local nightfall should hold back sunlight-driven growth at {@code lightPos}.
	 * <p>
	 * Vanilla ties crop and sapling growth to a light level rather than to the clock, so growth under
	 * artificial light must keep working at night exactly as it does in an unwrapped world; only the
	 * sunlit case follows the local day.
	 */
	public static boolean holdsBackSunlitGrowth(Level level, BlockPos lightPos) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return false;
		}
		if (isDay(level, lightPos.getX())) {
			return false;
		}
		return level.getBrightness(LightLayer.BLOCK, lightPos) < GROWTH_LIGHT_LEVEL;
	}

	/** Celestial angle 0..1 matching vanilla day cycle for sky rendering. */
	public static float celestialAngle(Level level, double x) {
		long t = localTime(level, x);
		double d = (t / (double) DAY_LENGTH) - 0.25;
		if (d < 0) {
			d += 1.0;
		}
		// Smooth like vanilla
		double smoothed = 1.0 - (Math.cos(d * Math.PI) + 1.0) / 2.0;
		return (float) (d * 2.0 + smoothed) / 3.0f;
	}
}
