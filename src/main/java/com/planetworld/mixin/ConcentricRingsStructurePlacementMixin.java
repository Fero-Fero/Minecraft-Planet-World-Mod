package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.worldgen.ContinentalClimate;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Continents >=4096: keep stronghold rings inside the wrap so an End portal exists.
 */
@Mixin(ConcentricRingsStructurePlacement.class)
public abstract class ConcentricRingsStructurePlacementMixin {
	@Inject(method = "distance", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampDistance(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int maxRing = Math.max(8, PlanetWorldConfig.chunkWidth() / 6);
		if (cir.getReturnValue() > maxRing) {
			cir.setReturnValue(maxRing);
		}
	}

	@Inject(method = "count", at = @At("RETURN"), cancellable = true)
	private void planetworld$ensureCount(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int width = PlanetWorldConfig.chunkWidth();
		int guaranteed = Math.max(3, Math.min(cir.getReturnValue(), Math.max(3, width / 16)));
		cir.setReturnValue(guaranteed);
	}
}
