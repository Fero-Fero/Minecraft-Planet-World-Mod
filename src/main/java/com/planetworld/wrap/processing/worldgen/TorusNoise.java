/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.processing.worldgen;

import com.planetworld.wrap.core.DimensionTransformer;

/**
 * Seamless torus noise matching Circumnavigate's smooth look at the reference
 * planet size, while scaling the embedding radius on larger worlds so hills
 * do not stretch into pancake plains.
 * <p>
 * One sample only (no internal FBM) - BlendedNoise/Perlin already stack octaves.
 * Y is sampled separately and averaged, same as the original wrap path.
 */
public final class TorusNoise {
	/**
	 * Block period where embedding radius is 1 (identical to classic unit-circle).
	 * UI circumference 256 => full period 512.
	 */
	public static final double REFERENCE_PERIOD = 512.0;

	private TorusNoise() {
	}

	public static double sampleHorizontal(long seed, DimensionTransformer transformer, double blockX, double blockZ) {
		double[] sc = embed(transformer, blockX, blockZ);
		return OpenSimplex2S.noise4_Fallback(seed, sc[0], sc[1], sc[2], sc[3]);
	}

	/**
	 * 3D sample: continuous X/Z torus + independent Y, averaged like original.
	 */
	public static double sample3(
			long seed,
			DimensionTransformer transformer,
			double blockX,
			double blockY,
			double blockZ
	) {
		double[] sc = embed(transformer, blockX, blockZ);
		double noise4 = OpenSimplex2S.noise4_Fallback(seed, sc[0], sc[1], sc[2], sc[3]);
		double noiseY = OpenSimplex2S.noise2(seed, 0.0, blockY);
		return (noise4 + noiseY) / 2.0;
	}

	/**
	 * Unit circle at {@link #REFERENCE_PERIOD}; larger planets get proportionally
	 * larger radius so feature wavelength stays near that reference.
	 */
	private static double[] embed(DimensionTransformer transformer, double blockX, double blockZ) {
		double periodX = Math.max(16.0, transformer.xWidth * 16.0);
		double periodZ = Math.max(16.0, transformer.zWidth * 16.0);
		double thetaX = ((blockX + transformer.wrappingSettings.xChunkBoundMin() * 16.0) / periodX) * (Math.PI * 2.0);
		double thetaZ = ((blockZ + transformer.wrappingSettings.zChunkBoundMin() * 16.0) / periodZ) * (Math.PI * 2.0);
		double r = Math.max(1.0, Math.min(periodX, periodZ) / REFERENCE_PERIOD);
		return new double[] {
				r * Math.sin(thetaX),
				r * Math.cos(thetaX),
				r * Math.sin(thetaZ),
				r * Math.cos(thetaZ)
		};
	}
}
