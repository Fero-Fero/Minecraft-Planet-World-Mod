package com.planetworld.worldgen.provider;

import java.util.Optional;

import com.planetworld.config.WorldgenPackChoice;
import com.planetworld.worldgen.compat.WorldgenPackIds;
import com.planetworld.worldgen.terralith.TerralithCompat;

import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

final class TerralithWorldgenProvider implements WorldgenProvider {
	@Override
	public WorldgenPackChoice choice() {
		return WorldgenPackChoice.TERRALITH;
	}

	@Override
	public boolean isAvailable() {
		return WorldgenPackIds.isTerralithLoaded() && !WorldgenPackIds.isStillLifeStackLoaded();
	}

	@Override
	public boolean isRemappableOverworldSource(MultiNoiseBiomeSource source) {
		return TerralithCompat.isRemappableOverworldSource(source);
	}

	@Override
	public Optional<String> biomeNamespace() {
		return Optional.of(TerralithCompat.NAMESPACE);
	}
}
