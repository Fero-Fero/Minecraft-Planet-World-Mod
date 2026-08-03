package com.planetworld.worldgen.provider;

import java.util.Optional;

import com.planetworld.config.WorldgenPackChoice;

import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/**
 * Soft worldgen pack adapter. Realism climate is generic; seeds/catalogs are per-provider.
 */
public interface WorldgenProvider {
	WorldgenPackChoice choice();

	boolean isAvailable();

	/** True when Realism climate remap may drive this biome source. */
	boolean isRemappableOverworldSource(MultiNoiseBiomeSource source);

	default Optional<String> biomeNamespace() {
		return Optional.empty();
	}
}
