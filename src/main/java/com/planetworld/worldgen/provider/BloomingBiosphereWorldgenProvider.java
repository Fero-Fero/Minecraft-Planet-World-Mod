package com.planetworld.worldgen.provider;

import java.util.Optional;

import com.planetworld.config.WorldgenPackChoice;
import com.planetworld.worldgen.compat.WorldgenPackIds;

import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;

final class BloomingBiosphereWorldgenProvider implements WorldgenProvider {
	private static final String NS = "blooming_biosphere";

	@Override
	public WorldgenPackChoice choice() {
		return WorldgenPackChoice.BLOOMING_BIOSPHERE;
	}

	@Override
	public boolean isAvailable() {
		return WorldgenPackIds.isBloomingBiosphereLoaded()
				&& !WorldgenPackIds.isStillLifeStackLoaded()
				&& !WorldgenPackIds.isTerralithLoaded();
	}

	@Override
	public boolean isRemappableOverworldSource(MultiNoiseBiomeSource source) {
		if (source.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
			return true;
		}
		return StillLifeWorldgenProvider.containsNamespace(source, NS)
				|| StillLifeWorldgenProvider.containsNamespace(source, "bloomingbiosphere");
	}

	@Override
	public Optional<String> biomeNamespace() {
		return Optional.of(NS);
	}
}
