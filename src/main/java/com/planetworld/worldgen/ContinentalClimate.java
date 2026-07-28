package com.planetworld.worldgen;

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
	/** UI circumference at/above which sparse biome seeds + structure scaling apply. */
	public static final int MIN_FULL_COVERAGE_CIRCUMFERENCE = 4096;

	private static final float CLIMATE_BLEND = 0.78f;
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

	public static boolean shouldScaleStructures() {
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

		float climateTemp = (float) (lat * 0.92);
		climateTemp = Mth.lerp(seam, climateTemp, 0.0f);
		temperature = Mth.clamp(Mth.lerp(CLIMATE_BLEND, temperature, climateTemp), -1.0f, 1.0f);

		float humidityBias = (float) (lat * 0.28) - 0.16f * (float) (1.0 - Math.abs(lat));
		if (lat < -0.55) {
			humidityBias += 0.1f;
		}
		humidity = Mth.clamp(humidity + humidityBias * (1.0f - seam * 0.5f), -1.0f, 1.0f);

		double x = wrapToSignedHalf(blockX, period, half);
		float lonWander = (float) Math.sin((x / half) * Math.PI) * 0.12f;
		humidity = Mth.clamp(humidity + lonWander, -1.0f, 1.0f);
		weirdness = Mth.clamp(weirdness + lonWander * 0.35f, -1.0f, 1.0f);

		float land = ContinentalLandmask.landFactor(blockX, blockZ, worldSeed);
		float maskCont = ContinentalLandmask.continentalness(blockX, blockZ, worldSeed);
		float continentalness = Mth.clamp(
				Mth.lerp(LANDMASK_BLEND, vanillaCont, maskCont),
				-1.2f,
				1.2f
		);
		// Dampen ridge/river weirdness inland so continents stay solid plates
		weirdness = Mth.clamp(weirdness * (1.0f - 0.75f * land), -1.0f, 1.0f);

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
