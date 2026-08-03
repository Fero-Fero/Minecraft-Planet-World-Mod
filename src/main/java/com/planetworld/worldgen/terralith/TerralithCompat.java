package com.planetworld.worldgen.terralith;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.neoforged.fml.ModList;

/**
 * Soft presence / feature gates for Terralith. No Terralith types on the classpath.
 */
public final class TerralithCompat {
	public static final String MOD_ID = "terralith";
	public static final String NAMESPACE = "terralith";

	/** Same gate as Realism full biome seeds — C=2048 is a valid test size. */
	public static final int MIN_SEED_CIRCUMFERENCE = 2048;

	private TerralithCompat() {
	}

	public static boolean isLoaded() {
		return ModList.get().isLoaded(MOD_ID);
	}

	/**
	 * Realism climate remap applies to vanilla Overworld multi-noise, or to a Terralith
	 * overworld source that no longer reports {@code stable(OVERWORLD)}.
	 */
	public static boolean isRemappableOverworldSource(MultiNoiseBiomeSource source) {
		if (source.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
			return true;
		}
		return isLoaded() && containsTerralithBiome(source);
	}

	public static boolean containsTerralithBiome(MultiNoiseBiomeSource source) {
		for (Holder<Biome> holder : source.possibleBiomes()) {
			if (isTerralithHolder(holder)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isTerralithHolder(Holder<Biome> holder) {
		return holder.unwrapKey()
				.map(ResourceKey::location)
				.map(id -> NAMESPACE.equals(id.getNamespace()))
				.orElse(false);
	}
}
