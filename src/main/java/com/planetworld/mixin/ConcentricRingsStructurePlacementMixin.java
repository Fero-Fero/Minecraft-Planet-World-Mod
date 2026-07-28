package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Complete coverage: clamp stronghold ring distance so rings stay inside the wrap.
 */
@Mixin(ConcentricRingsStructurePlacement.class)
public abstract class ConcentricRingsStructurePlacementMixin {
	@Inject(method = "distance", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampDistance(CallbackInfoReturnable<Integer> cir) {
		int maxRing = planetworld$maxRingChunks();
		if (maxRing <= 0) {
			return;
		}
		if (cir.getReturnValue() > maxRing) {
			cir.setReturnValue(maxRing);
		}
	}

	private static int planetworld$maxRingChunks() {
		if (!PlanetWorldConfig.isCompleteCoverage()) {
			return -1;
		}
		return Math.max(4, PlanetWorldConfig.chunkWidth() / 4);
	}
}
