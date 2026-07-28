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
 * occlusion graph cannot overwrite the server's wrap context mid-tracking.
 * <p>
 * {@link #noiseXzScale} is the octave scale applied to X/Z before
 * {@code ImprovedNoise} (e.g. {@code blockX * scale}). The torus mapper divides
 * it back out so the wrap seam stays continuous for every octave.
 */
public class TransformerRequests {
	private static final ThreadLocal<DimensionTransformer> CHUNK_MAP_TRANSFORMER = new ThreadLocal<>();
	private static final ThreadLocal<Double> NOISE_XZ_SCALE = ThreadLocal.withInitial(() -> 1.0);

	public static MinecraftServer server = null;
	public static ServerLevel noiseLevel;
	public static List<ChunkAccess> structureChunks = new ArrayList<>();

	public static DimensionTransformer getChunkMapTransformer() {
		return CHUNK_MAP_TRANSFORMER.get();
	}

	public static void setChunkMapTransformer(DimensionTransformer transformer) {
		CHUNK_MAP_TRANSFORMER.set(transformer);
	}

	public static void clearChunkMapTransformer() {
		CHUNK_MAP_TRANSFORMER.remove();
	}

	public static void setNoiseXzScale(double scale) {
		NOISE_XZ_SCALE.set(scale == 0.0 ? 1.0 : scale);
	}

	public static double noiseXzScale() {
		Double scale = NOISE_XZ_SCALE.get();
		return scale == null || scale == 0.0 ? 1.0 : scale;
	}

	public static void clearNoiseXzScale() {
		NOISE_XZ_SCALE.set(1.0);
	}

	/** Drop strong refs that would keep a stopped server/world reachable. */
	public static void clearSessionState() {
		server = null;
		noiseLevel = null;
		structureChunks.clear();
		CHUNK_MAP_TRANSFORMER.remove();
		NOISE_XZ_SCALE.remove();
		DebugInfo.chunkLoadingLevels.clear();
	}
}
