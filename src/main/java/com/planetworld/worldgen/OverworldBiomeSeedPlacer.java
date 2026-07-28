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
 * Sparse one-cell-per-overworld-biome seeds for Continents at circumference &gt;= 4096.
 * Tiny patches only — not a Complete Coverage grid.
 */
public final class OverworldBiomeSeedPlacer {
	private static final int PATCH_RADIUS_BLOCKS = 24;
	private static final long PLACEMENT_SALT = 0x51EEDB10L;

	private OverworldBiomeSeedPlacer() {
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
		HolderLookup.RegistryLookup<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		long seed = level.getSeed() ^ PLACEMENT_SALT;
		List<ResourceKey<Biome>> keys = overworldBiomeKeys();
		double period = ContinentalClimate.periodBlocks();
		double half = period * 0.5;
		int n = keys.size();
		int grid = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
		double cell = period / grid;

		double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
		double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);

		for (int i = 0; i < n; i++) {
			ResourceKey<Biome> key = keys.get(i);
			double[] pos = seedCenter(seed, i, key, period, half, grid, cell);
			if (shortestDist(x, z, pos[0], pos[1], period) <= PATCH_RADIUS_BLOCKS) {
				return biomes.get(key).orElse(null);
			}
		}
		return null;
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
		// Jitter inside cell from seed+index
		long h = seed ^ ((long) index * 0x9E3779B97F4A7C15L) ^ key.location().hashCode();
		double jx = (((h >>> 11) & 0xFFFF) / 65535.0 - 0.5) * cell * 0.55;
		double jz = (((h >>> 27) & 0xFFFF) / 65535.0 - 0.5) * cell * 0.55;
		double cx = -half + (gx + 0.5) * cell + jx;
		double cz = -half + (gz + 0.5) * cell + jz;

		// Prefer northern band for cold biomes, southern for tropical
		float preference = climatePreference(key);
		if (preference < -0.2f) {
			cz = Mth.clamp(cz, -half * 0.95, -half * 0.25);
		} else if (preference > 0.2f) {
			cz = Mth.clamp(cz, half * 0.25, half * 0.95);
		}
		return new double[] {
				ContinentalClimate.wrapToSignedHalf(cx, period, half),
				ContinentalClimate.wrapToSignedHalf(cz, period, half)
		};
	}

	/** Negative = cold/north, positive = tropical/south. */
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
		if (key == Biomes.MUSHROOM_FIELDS) {
			return 0.0f; // ocean island — any latitude OK
		}
		return 0.0f;
	}

	private static double shortestDist(double x0, double z0, double x1, double z1, double period) {
		double dx = Math.abs(x0 - x1);
		double dz = Math.abs(z0 - z1);
		dx = Math.min(dx, period - dx);
		dz = Math.min(dz, period - dz);
		return Math.sqrt(dx * dx + dz * dz);
	}
}
