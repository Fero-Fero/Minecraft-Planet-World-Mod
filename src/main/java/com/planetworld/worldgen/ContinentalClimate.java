package com.planetworld.worldgen;

import com.planetworld.config.PlanetSettings;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.processing.worldgen.OpenSimplex2S;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;

/**
 * Realism climate on a torus: both Z-poles are cold (seamless wrap), equator is warm,
 * arid belts sit at mid-latitudes, and humidity uses smooth curves + noise so biomes
 * blend instead of striping into hard bands.
 */
public final class ContinentalClimate {
	/**
	 * Continents at/above this UI circumference get sparse one-of-each biome seeds
	 * plus structure spacing scaled into the wrap (mansions, strongholds, etc.).
	 */
	public static final int MIN_FULL_COVERAGE_CIRCUMFERENCE = PlanetSettings.MIN_FULL_COVERAGE_CIRCUMFERENCE;

	/** Soft latitude pull — leave room for multi-noise variety. */
	private static final float TEMP_BLEND = 0.72f;
	private static final float HUMIDITY_BLEND = 0.48f;
	/** Fully replace vanilla continentalness with the landmask for solid continents. */
	private static final float LANDMASK_BLEND = 1.0f;
	private static final long HUMIDITY_NOISE_SEED = 0xA71D0001L;

	private ContinentalClimate() {
	}

	public static boolean shouldRemap() {
		return PlanetWorldConfig.isRealism();
	}

	public static boolean shouldSeedBiomes() {
		return PlanetWorldConfig.isRealism()
				&& PlanetWorldConfig.planetCircumference() >= MIN_FULL_COVERAGE_CIRCUMFERENCE;
	}

	/** Full coverage ≥8192: scale rare structure spacing into the wrap. */
	public static boolean shouldScaleStructures() {
		return shouldSeedBiomes();
	}

	/** Same gate as full seeds — structure biomes are included in the sparse grid. */
	public static boolean shouldSeedStructureBiomes() {
		return shouldSeedBiomes();
	}

	/**
	 * Signed latitude in {@code [-1, 1]}: {@code 0} = equator, {@code ±1} = poles (Z wrap seam).
	 * Both poles are cold so the torus seam is continuous.
	 */
	public static double latitude(double blockZ) {
		double period = periodBlocks();
		double half = period * 0.5;
		double z = wrapToSignedHalf(blockZ, period, half);
		return z / half;
	}

	public static Climate.TargetPoint remap(Climate.TargetPoint point, double blockX, double blockZ) {
		long worldSeed = worldSeedOrZero();
		float temperature = Climate.unquantizeCoord(point.temperature());
		float humidity = Climate.unquantizeCoord(point.humidity());
		float erosion = Climate.unquantizeCoord(point.erosion());
		float depth = Climate.unquantizeCoord(point.depth());
		float weirdness = Climate.unquantizeCoord(point.weirdness());
		float vanillaCont = Climate.unquantizeCoord(point.continentalness());

		double period = periodBlocks();
		double half = period * 0.5;
		double x = wrapToSignedHalf(blockX, period, half);
		double z = wrapToSignedHalf(blockZ, period, half);
		double lat = z / half; // -1 / +1 = poles (both cold), 0 = equator (hot)

		// Earth-like on a torus: cos(π·lat) → +1 equator, −1 both poles (seamless at wrap).
		float climateTemp = (float) (Math.cos(Math.PI * lat) * 0.95);
		temperature = Mth.clamp(Mth.lerp(TEMP_BLEND, temperature, climateTemp), -1.0f, 1.0f);

		float humidityTarget = smoothHumidityTarget(lat, x, z, half, worldSeed);
		humidity = Mth.clamp(Mth.lerp(HUMIDITY_BLEND, humidity, humidityTarget), -1.0f, 1.0f);

		float land = ContinentalLandmask.landFactor(blockX, blockZ, worldSeed);
		float mountain = ContinentalMountains.mountainFactor(blockX, blockZ, worldSeed, land);
		float maskCont = ContinentalLandmask.continentalness(blockX, blockZ, worldSeed, land);
		float continentalness = Mth.clamp(
				Mth.lerp(LANDMASK_BLEND, vanillaCont, maskCont),
				-1.2f,
				1.2f
		);
		weirdness = Mth.clamp(
				ContinentalMountains.reshapeWeirdness(weirdness, land, mountain),
				-1.0f,
				1.0f
		);
		erosion = Mth.clamp(
				ContinentalMountains.reshapeErosionClimate(erosion, land, mountain),
				-1.0f,
				1.0f
		);

		return Climate.target(temperature, humidity, continentalness, erosion, depth, weirdness);
	}

	/**
	 * Continuous humidity: wet tropics near equator, arid mid-latitudes, mild poles,
	 * plus low-frequency noise so belts are mottled rather than striped.
	 */
	private static float smoothHumidityTarget(double lat, double x, double z, double half, long worldSeed) {
		float absLat = (float) Math.abs(lat);
		float tropicWet = gaussian(absLat, 0.0f, 0.28f);
		float aridBelt = gaussian(absLat, 0.40f, 0.16f);
		float polarDamp = gaussian(absLat, 0.92f, 0.22f);
		float target = tropicWet * 0.50f - aridBelt * 0.78f + polarDamp * 0.12f;

		float lonWander = (float) Math.sin((x / half) * Math.PI) * 0.14f;
		float noise = OpenSimplex2S.noise2(worldSeed ^ HUMIDITY_NOISE_SEED, x / 320.0, z / 320.0) * 0.32f;
		float detail = OpenSimplex2S.noise2(worldSeed ^ (HUMIDITY_NOISE_SEED + 17), x / 110.0, z / 110.0) * 0.14f;
		return Mth.clamp(target + lonWander + noise + detail, -1.0f, 1.0f);
	}

	private static float gaussian(float x, float mean, float sigma) {
		float d = (x - mean) / sigma;
		return (float) Math.exp(-0.5f * d * d);
	}

	public static double periodBlocks() {
		if (TransformerRequests.noiseLevel != null) {
			DimensionTransformer transformer = TransformerRequests.noiseLevel.getTransformer();
			if (transformer != null && transformer.zWidth > 0) {
				return Math.max(transformer.xWidth, transformer.zWidth) * 16.0;
			}
		}
		return Math.max(16.0, WrapMath.periodBlocks());
	}

	public static double wrapToSignedHalf(double value, double period, double half) {
		double wrapped = ((value + half) % period + period) % period - half;
		if (wrapped >= half) {
			wrapped -= period;
		}
		return wrapped;
	}

	public static long worldSeedOrZero() {
		ServerLevel level = TransformerRequests.noiseLevel;
		return level != null ? level.getSeed() : 0L;
	}
}
