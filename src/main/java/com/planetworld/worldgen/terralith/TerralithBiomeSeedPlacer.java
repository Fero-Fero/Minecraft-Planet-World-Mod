package com.planetworld.worldgen.terralith;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.worldgen.ContinentalClimate;
import com.planetworld.worldgen.ContinentalLandmask;
import com.planetworld.wrap.storage.TransformerRequests;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/**
 * Sparse wrap-safe Terralith surface seeds for Realism worlds at circumference ≥2048.
 * Additive to {@link com.planetworld.worldgen.OverworldBiomeSeedPlacer}.
 */
public final class TerralithBiomeSeedPlacer {
	private static final int PATCH_RADIUS_BLOCKS = 36;
	private static final int RARE_RADIUS_BLOCKS = 48;
	private static final long PLACEMENT_SALT = 0x7E22A11C4L;

	private static final Object LOCK = new Object();
	private static volatile SeedLayout cache;

	private TerralithBiomeSeedPlacer() {
	}

	public static void clearCache() {
		cache = null;
	}

	public static boolean shouldSeed() {
		return TerralithCompat.isLoaded()
				&& PlanetWorldConfig.isRealism()
				&& PlanetWorldConfig.enableTerralithSeeds()
				&& ContinentalClimate.shouldSeedBiomes()
				&& PlanetWorldConfig.worldgenPackChoice() == com.planetworld.config.WorldgenPackChoice.TERRALITH;
	}

	@Nullable
	public static Holder<Biome> biomeAt(double blockX, double blockZ) {
		if (!shouldSeed()) {
			return null;
		}
		ServerLevel level = TransformerRequests.noiseLevel;
		if (level == null) {
			return null;
		}
		SeedLayout layout = layoutFor(level);
		if (layout.count == 0) {
			return null;
		}
		double period = layout.period;
		double half = period * 0.5;
		double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
		double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);

		for (int i = 0; i < layout.count; i++) {
			double dx = Math.abs(x - layout.x[i]);
			double dz = Math.abs(z - layout.z[i]);
			dx = Math.min(dx, period - dx);
			dz = Math.min(dz, period - dz);
			double r = layout.radius[i];
			if (dx * dx + dz * dz <= r * r) {
				return layout.holders[i];
			}
		}
		return null;
	}

	private static SeedLayout layoutFor(ServerLevel level) {
		long seed = level.getSeed() ^ PLACEMENT_SALT;
		double period = ContinentalClimate.periodBlocks();
		boolean fantasy = PlanetWorldConfig.includeFantasyTerralithBiomes();
		SeedLayout local = cache;
		if (local != null
				&& local.seed == seed
				&& Double.compare(local.period, period) == 0
				&& local.includeFantasy == fantasy) {
			return local;
		}
		synchronized (LOCK) {
			local = cache;
			if (local != null
					&& local.seed == seed
					&& Double.compare(local.period, period) == 0
					&& local.includeFantasy == fantasy) {
				return local;
			}
			local = build(level, seed, period, fantasy);
			cache = local;
			return local;
		}
	}

	private static SeedLayout build(ServerLevel level, long seed, double period, boolean includeFantasy) {
		MinecraftServer server = level.getServer();
		EnumMap<TerralithClimateBand, List<Holder<Biome>>> byBand =
				TerralithBiomeCatalog.resolveSurfaceHolders(server, includeFantasy);

		List<Holder<Biome>> holdersList = new ArrayList<>();
		List<TerralithClimateBand> bands = new ArrayList<>();
		for (TerralithClimateBand band : TerralithClimateBand.values()) {
			if (!band.isSurfaceSeeded()) {
				continue;
			}
			if (band.isFantasy() && !includeFantasy) {
				continue;
			}
			for (Holder<Biome> holder : byBand.get(band)) {
				holdersList.add(holder);
				bands.add(band);
			}
		}

		int n = holdersList.size();
		double half = period * 0.5;
		double quarter = period * 0.25;
		long worldSeed = level.getSeed();
		double[] xs = new double[n];
		double[] zs = new double[n];
		double[] radii = new double[n];
		@SuppressWarnings("unchecked")
		Holder<Biome>[] holders = (Holder<Biome>[]) new Holder<?>[n];

		for (int i = 0; i < n; i++) {
			TerralithClimateBand band = bands.get(i);
			Holder<Biome> holder = holdersList.get(i);
			double[] pos = placeInBand(seed, i, band, period, half, quarter, worldSeed);
			xs[i] = pos[0];
			zs[i] = pos[1];
			radii[i] = band.isFantasy() ? RARE_RADIUS_BLOCKS : PATCH_RADIUS_BLOCKS;
			holders[i] = holder;
		}
		return new SeedLayout(seed, period, includeFantasy, n, xs, zs, radii, holders);
	}

	/**
	 * Pick a wrap-safe center that satisfies landFactor + latitude for the climate band.
	 */
	private static double[] placeInBand(
			long seed,
			int index,
			TerralithClimateBand band,
			double period,
			double half,
			double quarter,
			long worldSeed
	) {
		double bestX = 0.0;
		double bestZ = 0.0;
		float bestScore = Float.NEGATIVE_INFINITY;

		for (int attempt = 0; attempt < 40; attempt++) {
			long h = seed ^ ((long) index * 0x9E3779B97F4A7C15L) ^ (attempt * 0xC2B2AE3D27L);
			double cx = ((h >>> 9) & 0xFFFF) / 65535.0 * period - half;
			double absLat = band.absLatMin
					+ (((h >>> 25) & 0xFFFF) / 65535.0) * (band.absLatMax - band.absLatMin);
			absLat = Mth.clamp((float) absLat, 0.0f, 0.98f);
			double sign = ((h >>> 7) & 1L) == 0L ? 1.0 : -1.0;
			double cz = sign * absLat * quarter;
			cx = ContinentalClimate.wrapToSignedHalf(cx, period, half);
			cz = ContinentalClimate.wrapToSignedHalf(cz, period, half);

			float land = ContinentalLandmask.landFactor(cx, cz, worldSeed);
			float score;
			if (band == TerralithClimateBand.COASTAL) {
				float target = 0.32f;
				score = 1.0f - Math.abs(land - target);
				if (land < 0.12f || land > 0.55f) {
					score -= 0.5f;
				}
			} else if (band == TerralithClimateBand.POLAR) {
				// Prefer coastal-ish land near poles (islands / polar shores).
				float target = 0.28f;
				score = 1.0f - Math.abs(land - target) * 0.8f;
				if (land < 0.08f) {
					score -= 0.35f;
				}
			} else {
				if (land < band.minLand) {
					score = land - band.minLand;
				} else {
					score = land;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestX = cx;
				bestZ = cz;
				if (band == TerralithClimateBand.COASTAL || band == TerralithClimateBand.POLAR) {
					if (score > 0.85f) {
						break;
					}
				} else if (land >= band.minLand + 0.1f) {
					break;
				}
			}
		}
		return new double[]{bestX, bestZ};
	}

	private record SeedLayout(
			long seed,
			double period,
			boolean includeFantasy,
			int count,
			double[] x,
			double[] z,
			double[] radius,
			Holder<Biome>[] holders
	) {
	}
}
