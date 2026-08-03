package com.planetworld.worldgen.compat;

import net.neoforged.fml.ModList;

/**
 * Soft-dep mod id probes for worldgen packs.
 * <p>
 * Modrinth datapack-as-mod jars use {@code mr_*} ids (e.g. {@code mr_still_life}).
 * Underscore / hyphen aliases are kept for older or renamed jars.
 */
public final class WorldgenPackIds {
	private WorldgenPackIds() {
	}

	public static boolean isTerralithLoaded() {
		return any("terralith");
	}

	public static boolean isLithosphereLoaded() {
		return any("mr_lithosphere", "lithosphere");
	}

	public static boolean isStillLifeLoaded() {
		return any("mr_still_life", "still_life", "stilllife", "still-life");
	}

	/** Still Life requires Lithosphere. */
	public static boolean isStillLifeStackLoaded() {
		return isStillLifeLoaded() && isLithosphereLoaded();
	}

	public static boolean isBloomingBiosphereLoaded() {
		return any("mr_blooming_biosphere", "blooming_biosphere", "bloomingbiosphere", "blooming-biosphere");
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
