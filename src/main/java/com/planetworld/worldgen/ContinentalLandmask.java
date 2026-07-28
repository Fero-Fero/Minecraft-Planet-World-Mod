package com.planetworld.worldgen;

import com.planetworld.wrap.processing.worldgen.OpenSimplex2S;
import net.minecraft.util.Mth;

/**
 * Low-frequency seamless land/ocean mask for Continents mode.
 * Embedding radius is kept small so a 2048 world gets ~2-3 large plates,
 * not many tiny islands (larger radius = more zero-crossings = speckles).
 * <p>
 * Ridge reshape only escapes the thin river band near 0 — forcing |ridge|
 * high collapses biomes to plains/cherry and turns every inland into peaks.
 */
public final class ContinentalLandmask {
	/**
	 * Noise-space radius of the XZ torus embedding. ~0.8 ≈ two to three
	 * continent-scale blobs on the wrap; values like 2+ shatter into islands.
	 */
	private static final double CONTINENT_RADIUS = 0.8;
	private static final long SEED_A = 0xC0FFEE01L;
	private static final long SEED_MUSH = 0xC0FFEE03L;
	/** Mild land bias — enough plate interior, still real ocean basins. */
	private static final float LAND_BIAS = 0.1f;

	private ContinentalLandmask() {
	}

	/**
	 * @return land fraction in {@code [0,1]} with solid interiors and open oceans.
	 */
	public static float landFactor(double blockX, double blockZ, long worldSeed) {
		double period = ContinentalClimate.periodBlocks();
		double half = period * 0.5;
		double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
		double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);
		double thetaX = ((x + half) / period) * (Math.PI * 2.0);
		double thetaZ = ((z + half) / period) * (Math.PI * 2.0);
		double r = CONTINENT_RADIUS;

		float coarse = OpenSimplex2S.noise4_Fallback(
				worldSeed ^ SEED_A,
				r * Math.sin(thetaX),
				r * Math.cos(thetaX),
				r * Math.sin(thetaZ),
				r * Math.cos(thetaZ)
		);
		float raw = coarse + LAND_BIAS;
		return Mth.clamp(smoothstep(-0.28f, 0.18f, raw), 0.0f, 1.0f);
	}

	/**
	 * Density/climate continentalness: ocean basins, thin coasts, inland plates.
	 */
	public static float continentalness(double blockX, double blockZ, long worldSeed) {
		float land = landFactor(blockX, blockZ, worldSeed);
		float cont;
		if (land < 0.2f) {
			cont = Mth.lerp(land / 0.2f, -1.05f, -0.35f);
		} else if (land < 0.45f) {
			cont = Mth.lerp((land - 0.2f) / 0.25f, -0.35f, 0.2f);
		} else {
			// Inland but not maxed — leaves room for forest/savanna/desert params
			cont = Mth.lerp((land - 0.45f) / 0.55f, 0.2f, 0.55f);
		}

		if (land < 0.22f) {
			double period = ContinentalClimate.periodBlocks();
			double half = period * 0.5;
			double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
			double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);
			// Larger wavelength + softer threshold → slightly bigger mushroom islands
			float island = OpenSimplex2S.noise2(worldSeed ^ SEED_MUSH, x / 190.0, z / 190.0);
			if (island > 0.84f) {
				float mush = smoothstep(0.84f, 0.96f, island);
				cont = Mth.lerp(mush, cont, -1.12f);
			}
		}
		return Mth.clamp(cont, -1.2f, 1.0f);
	}

	/**
	 * Nudge ridge/weirdness out of the thin river band only ({@code |v| < ~0.08}).
	 * Stronger pushes destroy biome variety and create wall-to-wall mountains.
	 */
	public static double reshapeRidge(double ridge, float land) {
		if (land < 0.4f) {
			return ridge;
		}
		double abs = Math.abs(ridge);
		if (abs >= 0.08) {
			return ridge;
		}
		float t = Mth.clamp((land - 0.4f) / 0.35f, 0.0f, 1.0f);
		double sign = ridge >= 0.0 ? 1.0 : -1.0;
		if (abs < 1.0e-6) {
			sign = 1.0;
		}
		return Mth.lerp(t, ridge, sign * 0.14);
	}

	public static float reshapeWeirdness(float weirdness, float land) {
		return (float) reshapeRidge(weirdness, land);
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
