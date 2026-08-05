package com.planetworld.worldgen.provider;

import java.util.Optional;

import com.planetworld.config.WorldgenPackChoice;
import com.planetworld.worldgen.compat.WorldgenPackIds;

import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

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
		// Lithosphere owns noise_settings / continents. Realism landmask remap is off
		// until a Still Life seed catalog exists (see WORLDGEN_SEASONS_POLES_PLAN phase 5).
		return false;
	}

	@Override
	public Optional<String> biomeNamespace() {
		return Optional.of(NS);
	}
}
