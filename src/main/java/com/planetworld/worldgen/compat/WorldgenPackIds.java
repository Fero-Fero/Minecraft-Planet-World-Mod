package com.planetworld.worldgen.compat;

import net.neoforged.fml.ModList;

/**
 * Soft-dep mod id probes for worldgen packs. Datapack-as-mod jars may use hyphen or underscore ids;
 * we accept any known candidate.
 */
public final class WorldgenPackIds {
	private WorldgenPackIds() {
	}

	public static boolean isTerralithLoaded() {
		return any("terralith");
	}

	public static boolean isLithosphereLoaded() {
		return any("lithosphere");
	}

	public static boolean isStillLifeLoaded() {
		return any("still_life", "stilllife", "still-life");
	}

	/** Still Life requires Lithosphere. */
	public static boolean isStillLifeStackLoaded() {
		return isStillLifeLoaded() && isLithosphereLoaded();
	}

	public static boolean isBloomingBiosphereLoaded() {
		return any("blooming_biosphere", "bloomingbiosphere", "blooming-biosphere");
	}

	public static boolean isSereneSeasonsLoaded() {
		return any("sereneseasons");
	}

	public static boolean isFarmersDelightLoaded() {
		return any("farmersdelight");
	}

	private static boolean any(String... ids) {
		ModList list = ModList.get();
		for (String id : ids) {
			if (list.isLoaded(id)) {
				return true;
			}
		}
		return false;
	}
}
