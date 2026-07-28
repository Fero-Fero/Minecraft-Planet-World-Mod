package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.worldgen.ContinentalClimate;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Continents ≥2048: keep stronghold rings inside the wrap so an End portal exists.
 */
@Mixin(ConcentricRingsStructurePlacement.class)
public abstract class ConcentricRingsStructurePlacementMixin {
	@Inject(method = "distance", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampDistance(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int maxRing = Math.max(4, PlanetWorldConfig.chunkWidth() / 8);
		if (cir.getReturnValue() > maxRing) {
			cir.setReturnValue(maxRing);
		}
	}

	@Inject(method = "spread", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampSpread(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int maxSpread = Math.max(1, PlanetWorldConfig.chunkWidth() / 16);
		if (cir.getReturnValue() > maxSpread) {
			cir.setReturnValue(maxSpread);
		}
	}

	@Inject(method = "count", at = @At("RETURN"), cancellable = true)
	private void planetworld$ensureCount(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int width = PlanetWorldConfig.chunkWidth();
		// At least one stronghold; cap so rings stay inside the torus
		int guaranteed = Math.max(1, Math.min(cir.getReturnValue(), Math.max(1, width / 24)));
		cir.setReturnValue(guaranteed);
	}
}
