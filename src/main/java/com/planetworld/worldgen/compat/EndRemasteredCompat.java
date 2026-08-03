package com.planetworld.worldgen.compat;

import net.neoforged.fml.ModList;

/**
 * Soft presence for End Remastered ({@code endrem}). Eyes locate via vanilla
 * {@code StructureTags.EYE_OF_ENDER_LOCATED}; PlanetWorld guarantees a land+plains
 * stronghold on Realism ≥2048 so those eyes have a target.
 */
public final class EndRemasteredCompat {
	public static final String MOD_ID = "endrem";

	private EndRemasteredCompat() {
	}

	public static boolean isLoaded() {
		return ModList.get().isLoaded(MOD_ID);
	}
}
