package com.planetworld.worldgen;

import com.planetworld.config.PlanetSettings;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;

/**
 * Continents-mode climate: large landmask oceans, Earth-like N/S temperature
 * (north = -Z cold, south = +Z tropical), soft blend at the Z wrap seam.
 */
public final class ContinentalClimate {
	/**
	 * Continents at/above this UI circumference get sparse one-of-each biome seeds
	 * plus structure spacing scaled into the wrap (mansions, strongholds, etc.).
	 */
	public static final int MIN_FULL_COVERAGE_CIRCUMFERENCE = PlanetSettings.MIN_CONTINENTAL_CIRCUMFERENCE;

	/** How strongly signed latitude overrides vanilla temperature. */
	private static final float TEMP_BLEND = 0.85f;
	/** How strongly latitude humidity belts override vanilla humidity. */
	private static final float HUMIDITY_BLEND = 0.7f;
	/** Fully replace vanilla continentalness with the landmask for solid continents. */
	private static final float LANDMASK_BLEND = 1.0f;
	/** Only the outermost ~3% of each hemisphere blends across the Z wrap seam. */
	private static final float SEAM_START = 0.97f;

	private ContinentalClimate() {
	}

	public static boolean shouldRemap() {
		return PlanetWorldConfig.isContinental();
	}

	public static boolean shouldSeedBiomes() {
		return PlanetWorldConfig.isContinental()
				&& PlanetWorldConfig.planetCircumference() >= MIN_FULL_COVERAGE_CIRCUMFERENCE;
	}

	/** Continents ≥2048: scale rare structure spacing into the wrap. */
	public static boolean shouldScaleStructures() {
		return shouldSeedBiomes();
	}

	/** Same gate as full seeds — structure biomes are included in the sparse grid. */
	public static boolean shouldSeedStructureBiomes() {
		return shouldSeedBiomes();
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
		double z = wrapToSignedHalf(blockZ, period, half);
		double lat = z / half; // -1 north (-Z), +1 south (+Z)
		float seam = seamBlend(Math.abs(lat));

		float climateTemp = (float) (lat * 0.95);
		climateTemp = Mth.lerp(seam, climateTemp, 0.0f);
		temperature = Mth.clamp(Mth.lerp(TEMP_BLEND, temperature, climateTemp), -1.0f, 1.0f);

		// Latitude humidity belts so 2048 still gets desert/savanna/jungle naturally:
		// deep south wet (jungle), mid-south arid (desert/savanna), north colder/damper.
		float humidityTarget;
		if (lat > 0.55) {
			humidityTarget = 0.48f; // wet tropics without bamboo-jungle dominance
		} else if (lat > 0.18) {
			humidityTarget = -0.7f;
		} else if (lat < -0.55) {
			humidityTarget = 0.15f;
		} else {
			humidityTarget = 0.05f;
		}
		double x = wrapToSignedHalf(blockX, period, half);
		float lonWander = (float) Math.sin((x / half) * Math.PI) * 0.18f;
		humidityTarget = Mth.clamp(humidityTarget + lonWander, -1.0f, 1.0f);
		float humBlend = HUMIDITY_BLEND * (1.0f - seam * 0.5f);
		humidity = Mth.clamp(Mth.lerp(humBlend, humidity, humidityTarget), -1.0f, 1.0f);

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

	private static float seamBlend(double absLat) {
		double t = Mth.clamp((absLat - SEAM_START) / (1.0 - SEAM_START), 0.0, 1.0);
		return (float) (t * t * (3.0 - 2.0 * t));
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
