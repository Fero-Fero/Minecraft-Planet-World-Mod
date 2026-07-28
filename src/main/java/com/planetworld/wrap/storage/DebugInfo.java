/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.storage;

import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;

/** Optional debug maps; always cleared via {@link TransformerRequests#clearSessionState()}. */
public class DebugInfo {
	public static final HashMap<ChunkPos, Integer> chunkLoadingLevels = new HashMap<>();
}
