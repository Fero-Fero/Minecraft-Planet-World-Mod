package com.planetworld.worldgen;

import net.minecraft.util.Mth;

/**
 * Continents: place 1–2 real mountain massifs on temperate inland (plains / forest /
 * spruce latitudes), and keep the rest of the land from growing spike peaks.
 */
public final class ContinentalMountains {
	private static final long SEED_A = 0xA0A0A0A01L;
	private static final long SEED_B = 0xB0B0B0B02L;
	/** Preferred absolute latitude: temperate mid-bands on either hemisphere. */
	private static final double ABS_LAT_MIN = 0.12;
	private static final double ABS_LAT_MAX = 0.48;
	/** Massif radius in blocks (wide foothills, one clear summit each). */
	private static final double RADIUS_MIN = 220.0;
	private static final double RADIUS_MAX = 340.0;

	private static final Object LOCK = new Object();
	private static volatile CenterCache cache;

	private ContinentalMountains() {
	}

	/**
	 * @return {@code 0..1} mountain influence (1 at summit cores).
	 */
	public static float mountainFactor(double blockX, double blockZ, long worldSeed) {
		return mountainFactor(blockX, blockZ, worldSeed, ContinentalLandmask.landFactor(blockX, blockZ, worldSeed));
	}

	public static float mountainFactor(double blockX, double blockZ, long worldSeed, float land) {
		if (land < 0.42f) {
			return 0.0f;
		}

		double period = ContinentalClimate.periodBlocks();
		double half = period * 0.5;
		double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
		double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);
		CenterCache centers = centersFor(worldSeed, period, half);

		float best = 0.0f;
		for (int i = 0; i < centers.count; i++) {
			double dist = torusDistance(x, z, centers.x[i], centers.z[i], period);
			float t = 1.0f - (float) (dist / centers.radius[i]);
			if (t <= 0.0f) {
				continue;
			}
			float dome = t * t * (3.0f - 2.0f * t);
			dome = dome * dome;
			if (dome > best) {
				best = dome;
			}
		}
		return best * Mth.clamp((land - 0.42f) / 0.25f, 0.0f, 1.0f);
	}

	/**
	 * Terrain/biome ridge: midland elsewhere, peak values only inside massifs.
	 * Suppresses vanilla peak spikes that read as tall needles.
	 */
	public static double reshapeRidge(double ridge, float land, float mountain) {
		ridge = ContinentalLandmask.reshapeRidge(ridge, land);
		if (land < 0.35f) {
			return ridge;
		}

		double sign = ridge >= 0.0 ? 1.0 : -1.0;
		double abs = Math.abs(ridge);
		if (abs < 1.0e-6) {
			sign = 1.0;
		}

		double plainsTarget = 0.30;
		if (abs > 0.42) {
			double crush = Mth.clamp((abs - 0.42) / 0.35, 0.0, 1.0);
			abs = Mth.lerp(crush * (1.0 - mountain), abs, plainsTarget);
		}
		double plainsRidge = sign * abs;
		double peakRidge = sign * 0.66;
		return Mth.lerp(mountain, plainsRidge, peakRidge);
	}

	/**
	 * High erosion = worn/flat; low erosion = mountains. Flatten land, carve massifs.
	 */
	public static double reshapeErosion(double erosion, float land, float mountain) {
		if (land < 0.35f) {
			return erosion;
		}
		double flat = Mth.lerp(0.55, erosion, 0.42);
		double alpine = -0.75;
		return Mth.lerp(mountain, flat, alpine);
	}

	/** Jaggedness only on massifs — kills needle spikes on normal land. */
	public static double reshapeJaggedness(double jagged, float mountain) {
		return jagged * mountain * mountain;
	}

	public static float reshapeWeirdness(float weirdness, float land, float mountain) {
		return (float) reshapeRidge(weirdness, land, mountain);
	}

	public static float reshapeErosionClimate(float erosion, float land, float mountain) {
		return (float) reshapeErosion(erosion, land, mountain);
	}

	private static CenterCache centersFor(long worldSeed, double period, double half) {
		CenterCache local = cache;
		if (local != null && local.seed == worldSeed && Double.compare(local.period, period) == 0) {
			return local;
		}
		synchronized (LOCK) {
			local = cache;
			if (local != null && local.seed == worldSeed && Double.compare(local.period, period) == 0) {
				return local;
			}
			int count = 1 + (int) ((mix(worldSeed ^ SEED_A) >>> 1) & 1L);
			double[] xs = new double[count];
			double[] zs = new double[count];
			double[] radii = new double[count];
			for (int i = 0; i < count; i++) {
				double[] center = pickCenter(worldSeed, i, period, half);
				xs[i] = center[0];
				zs[i] = center[1];
				radii[i] = RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * frac(mix(worldSeed, SEED_B, i));
			}
			local = new CenterCache(worldSeed, period, count, xs, zs, radii);
			cache = local;
			return local;
		}
	}

	private static double[] pickCenter(long worldSeed, int index, double period, double half) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double u = frac(mix(worldSeed, SEED_A, index * 17L + attempt));
			double v = frac(mix(worldSeed, SEED_B, index * 31L + attempt * 3L));
			double mx = (u - 0.5) * period;
			double absLat = ABS_LAT_MIN + v * (ABS_LAT_MAX - ABS_LAT_MIN);
			double sign = (mix(worldSeed, SEED_A, index + attempt) & 1L) == 0L ? 1.0 : -1.0;
			double mz = sign * absLat * half;
			if (ContinentalLandmask.landFactor(mx, mz, worldSeed) >= 0.5f) {
				return new double[]{mx, mz};
			}
		}
		double u = frac(mix(worldSeed, SEED_A, index));
		double v = frac(mix(worldSeed, SEED_B, index + 99L));
		double absLat = ABS_LAT_MIN + v * (ABS_LAT_MAX - ABS_LAT_MIN);
		double sign = (mix(worldSeed, SEED_B, index) & 1L) == 0L ? 1.0 : -1.0;
		return new double[]{(u - 0.5) * period, sign * absLat * half};
	}

	private static double torusDistance(double x, double z, double ox, double oz, double period) {
		double dx = ContinentalClimate.wrapToSignedHalf(x - ox, period, period * 0.5);
		double dz = ContinentalClimate.wrapToSignedHalf(z - oz, period, period * 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static long mix(long a) {
		a = (a ^ (a >>> 30)) * 0xBF58476D1CE4E5B9L;
		a = (a ^ (a >>> 27)) * 0x94D049BB133111EBL;
		return a ^ (a >>> 31);
	}

	private static long mix(long a, long b, long c) {
		return mix(a ^ mix(b + c * 0x9E3779B97F4A7C15L));
	}

	private static double frac(long bits) {
		return (bits >>> 11) * 0x1.0p-53;
	}

	private record CenterCache(long seed, double period, int count, double[] x, double[] z, double[] radius) {
	}
}
