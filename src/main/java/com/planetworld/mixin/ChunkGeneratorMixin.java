package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * {@code /locate structure} walks spacing-rings out to radius 100. On a finite
 * torus that can mean tens of thousands of STRUCTURE_STARTS chunk gens when a
 * rare structure (e.g. woodland mansion) never places — appearing as a full freeze.
 * Cap the ring count to cover about half the planet.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
	@ModifyVariable(
			method = "findNearestMapStructure",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private int planetworld$clampLocateSearchRadius(int searchRadius) {
		int width = PlanetWorldConfig.chunkWidth();
		if (width <= 1) {
			return searchRadius;
		}
		// Rings are in spacing units; rare structures use spacing >= ~8.
		// halfWidth/8 rings is enough to traverse the wrap for those.
		int half = Math.max(1, width / 2);
		int maxRings = Math.max(2, half / 8 + 2);
		return Math.min(searchRadius, maxRings);
	}
}
