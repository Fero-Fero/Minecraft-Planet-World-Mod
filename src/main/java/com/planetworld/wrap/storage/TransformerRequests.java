/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.storage;

import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage for context propagation down call stacks.
 * <p>
 * Chunk-map transformer is thread-local so integrated singleplayer's client
 * occlusion graph cannot overwrite the server's wrap context mid-tracking
 * (that caused sparse unloaded chunks near the seam).
 */
public class TransformerRequests {
	private static final ThreadLocal<DimensionTransformer> CHUNK_MAP_TRANSFORMER = new ThreadLocal<>();

	public static MinecraftServer server = null;
	public static ServerLevel noiseLevel;
	public static List<ChunkAccess> structureChunks = new ArrayList<>();

	public static DimensionTransformer getChunkMapTransformer() {
		return CHUNK_MAP_TRANSFORMER.get();
	}

	public static void setChunkMapTransformer(DimensionTransformer transformer) {
		CHUNK_MAP_TRANSFORMER.set(transformer);
	}
}
