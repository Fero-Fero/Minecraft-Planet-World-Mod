package com.planetworld.worldgen.provider;

import com.planetworld.config.WorldgenPackChoice;

import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;

final class VanillaWorldgenProvider implements WorldgenProvider {
	@Override
	public WorldgenPackChoice choice() {
		return WorldgenPackChoice.VANILLA;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public boolean isRemappableOverworldSource(MultiNoiseBiomeSource source) {
		return source.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
	}
}
