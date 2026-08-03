package com.planetworld.worldgen;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.planetworld.worldgen.compat.WorldgenPackIds;
import com.planetworld.worldgen.terralith.TerralithCompat;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;

/**
 * Decides whether Realism climate remap may touch a multi-noise source.
 * <p>
 * Results are cached per source instance — Still Life / Blooming / Terralith lists
 * are large, and scanning {@code possibleBiomes()} on every quart sample stalls chunk gen.
 */
public final class RealismBiomeSources {
	private static final Map<MultiNoiseBiomeSource, Boolean> CACHE =
			Collections.synchronizedMap(new WeakHashMap<>());

	private RealismBiomeSources() {
	}

	public static void clearCache() {
		CACHE.clear();
	}

	public static boolean isRemappableOverworldSource(MultiNoiseBiomeSource source) {
		if (source == null) {
			return false;
		}
		Boolean cached = CACHE.get(source);
		if (cached != null) {
			return cached;
		}
		boolean result = compute(source);
		CACHE.put(source, result);
		return result;
	}

	public static boolean containsNamespace(MultiNoiseBiomeSource source, String namespace) {
		for (Holder<Biome> holder : source.possibleBiomes()) {
			boolean match = holder.unwrapKey()
					.map(ResourceKey::location)
					.map(id -> namespace.equals(id.getNamespace()))
					.orElse(false);
			if (match) {
				return true;
			}
		}
		return false;
	}

	private static boolean compute(MultiNoiseBiomeSource source) {
		if (source.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
			return true;
		}
		// Pack-owned overworld sources no longer report stable(OVERWORLD).
		if (WorldgenPackIds.isStillLifeStackLoaded() && containsNamespace(source, "still_life")) {
			return true;
		}
		if (WorldgenPackIds.isBloomingBiosphereLoaded()
				&& (containsNamespace(source, "blooming_biosphere")
				|| containsNamespace(source, "bloomingbiosphere"))) {
			return true;
		}
		if (TerralithCompat.isLoaded() && TerralithCompat.containsTerralithBiome(source)) {
			return true;
		}
		if (WorldgenPackIds.isLithosphereLoaded() && containsNamespace(source, "lithosphere")) {
			return true;
		}
		return false;
	}
}
