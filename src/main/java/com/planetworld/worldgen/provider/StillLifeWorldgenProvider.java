package com.planetworld.worldgen.provider;

import java.util.Optional;

import com.planetworld.config.WorldgenPackChoice;
import com.planetworld.worldgen.compat.WorldgenPackIds;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;

final class StillLifeWorldgenProvider implements WorldgenProvider {
	private static final String NS = "still_life";

	@Override
	public WorldgenPackChoice choice() {
		return WorldgenPackChoice.STILL_LIFE;
	}

	@Override
	public boolean isAvailable() {
		return WorldgenPackIds.isStillLifeStackLoaded() && !WorldgenPackIds.isTerralithLoaded();
	}

	@Override
	public boolean isRemappableOverworldSource(MultiNoiseBiomeSource source) {
		if (source.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
			return true;
		}
		return containsNamespace(source, NS) || containsNamespace(source, "lithosphere");
	}

	@Override
	public Optional<String> biomeNamespace() {
		return Optional.of(NS);
	}

	static boolean containsNamespace(MultiNoiseBiomeSource source, String namespace) {
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
}
