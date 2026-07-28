package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.worldgen.ContinentalClimate;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Continents >=4096: shrink rare large-spacing placements (woodland mansions use 80)
 * so at least one attempt fits inside the wrap. Leaves dense structures alone.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {
	/** Vanilla mansion spacing is 80; ignore common structures with smaller spacing. */
	private static final int RARE_SPACING_THRESHOLD = 64;

	@Inject(method = "spacing", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampRareSpacing(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int spacing = cir.getReturnValue();
		if (spacing < RARE_SPACING_THRESHOLD) {
			return;
		}
		int max = Math.max(8, PlanetWorldConfig.chunkWidth() / 3);
		if (spacing > max) {
			cir.setReturnValue(max);
		}
	}

	@Inject(method = "separation", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampRareSeparation(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
		if (self.spacing() < RARE_SPACING_THRESHOLD) {
			return;
		}
		int spacingCap = Math.max(8, PlanetWorldConfig.chunkWidth() / 3);
		int maxSep = Math.max(0, spacingCap - 1);
		if (cir.getReturnValue() > maxSep) {
			cir.setReturnValue(maxSep);
		}
	}
}
