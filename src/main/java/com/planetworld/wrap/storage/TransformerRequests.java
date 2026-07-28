/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.storage;

import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage for context propagation down call stacks.
 * <p>
 * Chunk-map transformer is thread-local so integrated singleplayer's client
 * occlusion graph cannot overwrite the server's wrap context mid-tracking.
 * {@link #noiseXzFrequency} is the current octave frequency for torus sampling.
 */
public class TransformerRequests {
	private static final ThreadLocal<DimensionTransformer> CHUNK_MAP_TRANSFORMER = new ThreadLocal<>();
	private static final ThreadLocal<Double> NOISE_XZ_FREQUENCY = ThreadLocal.withInitial(() -> 1.0);

	public static MinecraftServer server = null;
	/** Volatile: cleared on stop while gen workers may still sample. */
	public static volatile ServerLevel noiseLevel;
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

	public static void setNoiseXzFrequency(double frequency) {
		NOISE_XZ_FREQUENCY.set(frequency <= 0.0 ? 1.0 : frequency);
	}

	public static double noiseXzFrequency() {
		Double f = NOISE_XZ_FREQUENCY.get();
		return f == null || f <= 0.0 ? 1.0 : f;
	}

	public static void clearNoiseXzFrequency() {
		NOISE_XZ_FREQUENCY.set(1.0);
	}

	/** Safe snapshot for worldgen mixins (null if quitting / not generating). */
	@Nullable
	public static DimensionTransformer noiseTransformerOrNull() {
		ServerLevel level = noiseLevel;
		return level == null ? null : level.getTransformer();
	}

	public static boolean useWrappedWorldGen() {
		DimensionTransformer transformer = noiseTransformerOrNull();
		return transformer != null && transformer.wrappingSettings.useWrappedWorldGen();
	}

	/** Drop strong refs that would keep a stopped server/world reachable. */
	public static void clearSessionState() {
		server = null;
		noiseLevel = null;
		structureChunks.clear();
		CHUNK_MAP_TRANSFORMER.remove();
		NOISE_XZ_FREQUENCY.remove();
		DebugInfo.chunkLoadingLevels.clear();
		com.planetworld.worldgen.OverworldBiomeSeedPlacer.clearCache();
		com.planetworld.worldgen.GuaranteedStructures.clearCache();
	}
}
