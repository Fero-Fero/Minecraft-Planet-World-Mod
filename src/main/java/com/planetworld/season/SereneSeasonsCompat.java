package com.planetworld.season;

import com.planetworld.worldgen.compat.WorldgenPackIds;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Soft-dep presence for Serene Seasons. Season values are read via reflection so SS is not a compile dep.
 */
public final class SereneSeasonsCompat {
	public static final String MOD_ID = "sereneseasons";

	private SereneSeasonsCompat() {
	}

	public static boolean isLoaded() {
		return WorldgenPackIds.isSereneSeasonsLoaded() || ModList.get().isLoaded(MOD_ID);
	}
}
