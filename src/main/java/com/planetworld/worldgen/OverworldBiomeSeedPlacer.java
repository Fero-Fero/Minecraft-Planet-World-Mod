package com.planetworld.worldgen;

import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sparse one-cell-per-overworld-biome seeds for Continents ≥2048.
 * Dark forest is placed on inland terrain and sized for woodland mansions.
 */
public final class OverworldBiomeSeedPlacer {
	private static final int PATCH_RADIUS_BLOCKS = 28;
	private static final int STRUCTURE_BIOME_RADIUS_BLOCKS = 160;
	private static final int MUSHROOM_RADIUS_BLOCKS = 72;
	private static final long PLACEMENT_SALT = 0x51EEDB10L;

	private static final Object LOCK = new Object();
	private static volatile SeedLayout cache;
	private static volatile double[] darkForestCenterCache;
	private static volatile long darkForestSeedCache = Long.MIN_VALUE;

	private OverworldBiomeSeedPlacer() {
	}

	public static void clearCache() {
		cache = null;
		darkForestCenterCache = null;
		darkForestSeedCache = Long.MIN_VALUE;
	}

	/**
	 * Inland dark-forest center used by biome seeds and guaranteed mansion placement.
	 */
	public static double[] ensureDarkForestOnLand(long worldSeed) {
		long seed = worldSeed ^ PLACEMENT_SALT;
		if (darkForestCenterCache != null && darkForestSeedCache == seed) {
			return darkForestCenterCache;
		}
		synchronized (LOCK) {
			if (darkForestCenterCache != null && darkForestSeedCache == seed) {
				return darkForestCenterCache;
			}
			double period = ContinentalClimate.periodBlocks();
			double half = period * 0.5;
			double[] best = null;
			float bestLand = -1.0f;
			for (int attempt = 0; attempt < 48; attempt++) {
				long h = seed ^ (0xDA12F02E57L + attempt * 0x9E3779B97F4A7C15L);
				double cx = ((h >>> 9) & 0xFFFF) / 65535.0 * period - half;
				double cz = (((h >>> 25) & 0xFFFF) / 65535.0 - 0.5) * half * 0.7;
				cx = ContinentalClimate.wrapToSignedHalf(cx, period, half);
				cz = ContinentalClimate.wrapToSignedHalf(cz, period, half);
				float land = ContinentalLandmask.landFactor(cx, cz, worldSeed);
				if (land > bestLand) {
					bestLand = land;
					best = new double[]{cx, cz};
					if (land >= 0.55f) {
						break;
					}
				}
			}
			if (best == null) {
				best = new double[]{0.0, 0.0};
			}
			darkForestCenterCache = best;
			darkForestSeedCache = seed;
			return best;
		}
	}

	@Nullable
	public static Holder<Biome> biomeAt(double blockX, double blockZ) {
		if (!ContinentalClimate.shouldSeedBiomes()) {
			return null;
		}
		ServerLevel level = TransformerRequests.noiseLevel;
		if (level == null) {
			return null;
		}
		SeedLayout layout = layoutFor(level);
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
		SeedLayout local = cache;
		if (local != null && local.seed == seed && Double.compare(local.period, period) == 0) {
			return local;
		}
		synchronized (LOCK) {
			local = cache;
			if (local != null && local.seed == seed && Double.compare(local.period, period) == 0) {
				return local;
			}
			local = build(level, seed, period);
			cache = local;
			return local;
		}
	}

	private static SeedLayout build(ServerLevel level, long seed, double period) {
		HolderLookup.RegistryLookup<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		List<ResourceKey<Biome>> keys = overworldBiomeKeys();
		double half = period * 0.5;
		int n = keys.size();
		int grid = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
		double cell = period / grid;
		long worldSeed = level.getSeed();

		double[] xs = new double[n];
		double[] zs = new double[n];
		double[] radii = new double[n];
		@SuppressWarnings("unchecked")
		Holder<Biome>[] holders = (Holder<Biome>[]) new Holder<?>[n];
		int written = 0;
		for (int i = 0; i < n; i++) {
			ResourceKey<Biome> key = keys.get(i);
			Holder.Reference<Biome> holder = biomes.get(key).orElse(null);
			if (holder == null) {
				continue;
			}
			double[] pos;
			if (key == Biomes.DARK_FOREST) {
				pos = ensureDarkForestOnLand(worldSeed);
			} else {
				pos = seedCenter(seed, i, key, period, half, grid, cell);
			}
			xs[written] = pos[0];
			zs[written] = pos[1];
			radii[written] = patchRadius(key);
			holders[written] = holder;
			written++;
		}
		return new SeedLayout(seed, period, written, xs, zs, radii, holders);
	}

	private static int patchRadius(ResourceKey<Biome> key) {
		if (key == Biomes.MUSHROOM_FIELDS) {
			return MUSHROOM_RADIUS_BLOCKS;
		}
		if (key == Biomes.DARK_FOREST || key == Biomes.DEEP_DARK) {
			return STRUCTURE_BIOME_RADIUS_BLOCKS;
		}
		if (key == Biomes.BAMBOO_JUNGLE) {
			return 16; // tiny — climate remap also converts most bamboo to jungle
		}
		return PATCH_RADIUS_BLOCKS;
	}

	private static List<ResourceKey<Biome>> overworldBiomeKeys() {
		List<ResourceKey<Biome>> keys = new ArrayList<>(
				MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD.usedBiomes().toList()
		);
		keys.sort(Comparator.comparing(k -> k.location().toString()));
		return keys;
	}

	private static double[] seedCenter(
			long seed,
			int index,
			ResourceKey<Biome> key,
			double period,
			double half,
			int grid,
			double cell
	) {
		int gx = index % grid;
		int gz = index / grid;
		long h = seed ^ ((long) index * 0x9E3779B97F4A7C15L) ^ key.location().hashCode();
		double jx = (((h >>> 11) & 0xFFFF) / 65535.0 - 0.5) * cell * 0.55;
		double jz = (((h >>> 27) & 0xFFFF) / 65535.0 - 0.5) * cell * 0.55;
		double cx = -half + (gx + 0.5) * cell + jx;
		double cz = -half + (gz + 0.5) * cell + jz;

		float preference = climatePreference(key);
		if (preference < -0.2f) {
			cz = Mth.clamp(cz, -half * 0.95, -half * 0.25);
		} else if (preference > 0.2f) {
			cz = Mth.clamp(cz, half * 0.25, half * 0.95);
		}

		if (key == Biomes.MUSHROOM_FIELDS) {
			long mushHash = h ^ 0xA0151504ADL;
			cx = ContinentalClimate.wrapToSignedHalf(((mushHash >>> 9) & 0xFFFF) / 65535.0 * period - half, period, half);
			cz = ContinentalClimate.wrapToSignedHalf(((mushHash >>> 25) & 0xFFFF) / 65535.0 * period - half, period, half);
		}

		return new double[] {
				ContinentalClimate.wrapToSignedHalf(cx, period, half),
				ContinentalClimate.wrapToSignedHalf(cz, period, half)
		};
	}

	private static float climatePreference(ResourceKey<Biome> key) {
		if (key == Biomes.FROZEN_OCEAN || key == Biomes.DEEP_FROZEN_OCEAN || key == Biomes.FROZEN_RIVER
				|| key == Biomes.SNOWY_PLAINS || key == Biomes.ICE_SPIKES || key == Biomes.SNOWY_TAIGA
				|| key == Biomes.SNOWY_BEACH || key == Biomes.GROVE || key == Biomes.SNOWY_SLOPES
				|| key == Biomes.FROZEN_PEAKS || key == Biomes.JAGGED_PEAKS || key == Biomes.COLD_OCEAN
				|| key == Biomes.DEEP_COLD_OCEAN || key == Biomes.TAIGA || key == Biomes.OLD_GROWTH_PINE_TAIGA
				|| key == Biomes.OLD_GROWTH_SPRUCE_TAIGA) {
			return -1.0f;
		}
		if (key == Biomes.JUNGLE || key == Biomes.SPARSE_JUNGLE || key == Biomes.BAMBOO_JUNGLE
				|| key == Biomes.WARM_OCEAN || key == Biomes.LUKEWARM_OCEAN || key == Biomes.DEEP_LUKEWARM_OCEAN
				|| key == Biomes.MANGROVE_SWAMP || key == Biomes.CHERRY_GROVE || key == Biomes.DESERT
				|| key == Biomes.BADLANDS || key == Biomes.WOODED_BADLANDS || key == Biomes.ERODED_BADLANDS) {
			return 1.0f;
		}
		return 0.0f;
	}

	private record SeedLayout(
			long seed,
			double period,
			int count,
			double[] x,
			double[] z,
			double[] radius,
			Holder<Biome>[] holders
	) {
	}
}
