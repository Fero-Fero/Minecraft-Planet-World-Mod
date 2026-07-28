package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Complete coverage: soft-clamp spacing so every random-spread structure set can
 * place at least once inside the torus period.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {
	@Inject(method = "spacing", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampSpacing(CallbackInfoReturnable<Integer> cir) {
		int max = planetworld$maxSpacingChunks();
		if (max <= 0) {
			return;
		}
		int spacing = cir.getReturnValue();
		if (spacing > max) {
			cir.setReturnValue(max);
		}
	}

	@Inject(method = "separation", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampSeparation(CallbackInfoReturnable<Integer> cir) {
		int spacingCap = planetworld$maxSpacingChunks();
		if (spacingCap <= 0) {
			return;
		}
		int separation = cir.getReturnValue();
		int maxSep = Math.max(0, spacingCap - 1);
		if (separation > maxSep) {
			cir.setReturnValue(maxSep);
		}
	}

	private static int planetworld$maxSpacingChunks() {
		if (!PlanetWorldConfig.isCompleteCoverage()) {
			return -1;
		}
		int width = Math.max(8, PlanetWorldConfig.chunkWidth());
		// Fit at least one placement cell; half-width keeps density closer to vanilla.
		return Math.max(4, width / 2);
	}
}
