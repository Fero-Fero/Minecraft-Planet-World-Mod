package com.planetworld.config;

import com.planetworld.wrap.core.CoordinateConstants;
import com.planetworld.wrap.options.DimensionWrappingSettings;
import com.planetworld.wrap.options.WorldWrappingSettings;
import com.planetworld.wrap.options.WrappingOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds overworld + nether torus bounds from planet circumference.
 * The End is intentionally omitted (stays unwrapped).
 */
public final class PlanetWrappingBounds {
	private PlanetWrappingBounds() {
	}

	/**
	 * @param circumferenceBlocks UI half-period circumference
	 */
	public static WorldWrappingSettings create(int circumferenceBlocks) {
		int halfChunks = Math.max(1, circumferenceBlocks / 16);
		int netherScale = chooseNetherScale(halfChunks * 2);
		int netherHalf = Math.max(1, halfChunks / netherScale);

		DimensionWrappingSettings overworld = new DimensionWrappingSettings(
				-halfChunks, halfChunks, -halfChunks, halfChunks,
				DimensionWrappingSettings.Axis.X, 0, true
		);
		DimensionWrappingSettings nether = new DimensionWrappingSettings(
				-netherHalf, netherHalf, -netherHalf, netherHalf,
				DimensionWrappingSettings.Axis.X, 0, true
		);

		Map<ResourceKey<Level>, DimensionWrappingSettings> dims = new HashMap<>();
		dims.put(Level.OVERWORLD, overworld);
		dims.put(Level.NETHER, nether);
		return new WorldWrappingSettings(new WrappingOptions(1), dims);
	}

	/**
	 * Add nether bounds to an existing OW-only wrap map (existing worlds).
	 */
	public static WorldWrappingSettings withNetherIfMissing(WorldWrappingSettings existing) {
		if (existing.dimensions().containsKey(Level.NETHER)) {
			return existing;
		}
		DimensionWrappingSettings overworld = existing.dimensions().get(Level.OVERWORLD);
		if (overworld == null) {
			return existing;
		}
		int halfChunks = Math.max(1, overworld.xChunkBoundMax());
		int netherScale = chooseNetherScale(halfChunks * 2);
		int netherHalf = Math.max(1, halfChunks / netherScale);
		DimensionWrappingSettings nether = new DimensionWrappingSettings(
				-netherHalf, netherHalf, -netherHalf, netherHalf,
				overworld.shiftAxis(), 0, true
		);
		Map<ResourceKey<Level>, DimensionWrappingSettings> dims = new HashMap<>(existing.dimensions());
		dims.put(Level.NETHER, nether);
		return new WorldWrappingSettings(existing.options(), dims);
	}

	/**
	 * Largest vanilla-like scale (prefer 8) that keeps nether width usable.
	 */
	public static int chooseNetherScale(int overworldChunkWidth) {
		int scale = 8;
		while (scale > 1) {
			if (overworldChunkWidth / scale >= CoordinateConstants.MIN_LEVEL_WIDTH
					&& overworldChunkWidth % scale == 0) {
				return scale;
			}
			scale--;
		}
		return 1;
	}
}
