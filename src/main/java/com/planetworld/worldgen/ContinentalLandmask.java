package com.planetworld.worldgen;

import com.planetworld.wrap.processing.worldgen.OpenSimplex2S;
import net.minecraft.util.Mth;

/**
 * Low-frequency seamless land/ocean mask for Continents mode.
 * Produces a few large landmasses with wide oceans (not river spaghetti).
 */
public final class ContinentalLandmask {
	/** ~2-3 continent-scale blobs around the torus. */
	private static final double CONTINENT_COUNT = 2.35;
	private static final long SEED_A = 0xC0FFEE01L;
	private static final long SEED_B = 0xC0FFEE02L;
	private static final long SEED_MUSH = 0xC0FFEE03L;

	private ContinentalLandmask() {
	}

	/**
	 * @return land fraction in {@code [0,1]} — sharpened so interiors stay solid
	 * and oceans stay open (reduces inland seas / river mesh).
	 */
	public static float landFactor(double blockX, double blockZ, long worldSeed) {
		double period = ContinentalClimate.periodBlocks();
		double half = period * 0.5;
		double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
		double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);
		double thetaX = ((x + half) / period) * (Math.PI * 2.0);
		double thetaZ = ((z + half) / period) * (Math.PI * 2.0);
		double r = CONTINENT_COUNT;

		float coarse = OpenSimplex2S.noise4_Fallback(
				worldSeed ^ SEED_A,
				r * Math.sin(thetaX),
				r * Math.cos(thetaX),
				r * Math.sin(thetaZ),
				r * Math.cos(thetaZ)
		);
		float detail = OpenSimplex2S.noise4_Fallback(
				worldSeed ^ SEED_B,
				(r * 1.6) * Math.sin(thetaX),
				(r * 1.6) * Math.cos(thetaX),
				(r * 1.6) * Math.sin(thetaZ),
				(r * 1.6) * Math.cos(thetaZ)
		);
		float raw = coarse + 0.08f * detail;
		float land = smoothstep(-0.28f, 0.08f, raw);
		land = land * land * (3.0f - 2.0f * land);
		return Mth.clamp(land, 0.0f, 1.0f);
	}

	/**
	 * Density/climate continentalness: deep oceans vs solid continents.
	 */
	public static float continentalness(double blockX, double blockZ, long worldSeed) {
		float land = landFactor(blockX, blockZ, worldSeed);
		float cont;
		if (land < 0.25f) {
			cont = Mth.lerp(land / 0.25f, -1.05f, -0.35f);
		} else if (land < 0.55f) {
			cont = Mth.lerp((land - 0.25f) / 0.3f, -0.35f, 0.15f);
		} else {
			cont = Mth.lerp((land - 0.55f) / 0.45f, 0.15f, 0.75f);
		}

		if (land < 0.2f) {
			double period = ContinentalClimate.periodBlocks();
			double half = period * 0.5;
			double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
			double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);
			float island = OpenSimplex2S.noise2(worldSeed ^ SEED_MUSH, x / 96.0, z / 96.0);
			if (island > 0.91f) {
				cont = -1.12f;
			} else if (island > 0.78f) {
				cont = Math.min(cont, -0.95f);
			}
		}
		return Mth.clamp(cont, -1.2f, 1.0f);
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
