package com.planetworld.worldgen.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.config.WorldgenPackChoice;
import com.planetworld.worldgen.terralith.TerralithCompat;

import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/**
 * Registry of soft worldgen providers. Active choice comes from per-world settings.
 */
public final class WorldgenProviders {
	private static final List<WorldgenProvider> PROVIDERS = List.of(
			new VanillaWorldgenProvider(),
			new TerralithWorldgenProvider(),
			new StillLifeWorldgenProvider(),
			new BloomingBiosphereWorldgenProvider()
	);

	private WorldgenProviders() {
	}

	public static List<WorldgenProvider> all() {
		return PROVIDERS;
	}

	public static Optional<WorldgenProvider> active() {
		WorldgenPackChoice choice = PlanetWorldConfig.worldgenPackChoice();
		return PROVIDERS.stream().filter(p -> p.choice() == choice && p.isAvailable()).findFirst();
	}

	public static boolean isRemappableOverworldSource(MultiNoiseBiomeSource source) {
		if (TerralithCompat.isRemappableOverworldSource(source)) {
			return true;
		}
		return active().map(p -> p.isRemappableOverworldSource(source)).orElse(false);
	}

	public static List<WorldgenPackChoice> availableChoices() {
		List<WorldgenPackChoice> out = new ArrayList<>();
		for (WorldgenProvider p : PROVIDERS) {
			if (p.isAvailable()) {
				out.add(p.choice());
			}
		}
		if (out.isEmpty()) {
			out.add(WorldgenPackChoice.VANILLA);
		}
		return out;
	}
}
