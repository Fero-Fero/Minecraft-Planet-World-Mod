package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.worldgen.ContinentalClimate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Continents ≥2048: keep rare large-spacing structures inside the wrap.
 * Only overrides {@code getPotentialStructureChunk} when spacing was actually clamped.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {
	@Shadow
	public abstract RandomSpreadType spreadType();

	@Inject(method = "spacing", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampSpacing(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int max = maxSpacingChunks();
		if (cir.getReturnValue() > max) {
			cir.setReturnValue(max);
		}
	}

	@Inject(method = "separation", at = @At("RETURN"), cancellable = true)
	private void planetworld$clampSeparation(CallbackInfoReturnable<Integer> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		RandomSpreadStructurePlacementAccessor raw = (RandomSpreadStructurePlacementAccessor) this;
		if (raw.planetworld$rawSpacing() <= maxSpacingChunks()) {
			return;
		}
		int spacing = Math.min(raw.planetworld$rawSpacing(), maxSpacingChunks());
		int maxSep = Math.max(0, spacing - 1);
		if (cir.getReturnValue() > maxSep) {
			cir.setReturnValue(maxSep);
		}
	}

	@Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
	private void planetworld$clampedPotentialChunk(long seed, int regionX, int regionZ, CallbackInfoReturnable<ChunkPos> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		RandomSpreadStructurePlacementAccessor raw = (RandomSpreadStructurePlacementAccessor) this;
		int max = maxSpacingChunks();
		if (raw.planetworld$rawSpacing() <= max) {
			return; // vanilla fields already fine — avoid extra work on every structure check
		}
		int spacing = max;
		int separation = Math.min(raw.planetworld$rawSeparation(), Math.max(0, spacing - 1));
		int i = Math.floorDiv(regionX, spacing);
		int j = Math.floorDiv(regionZ, spacing);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		int salt = ((StructurePlacementAccessor) this).planetworld$getSalt();
		random.setLargeFeatureWithSalt(seed, i, j, salt);
		int range = Math.max(1, spacing - separation);
		int ox = this.spreadType().evaluate(random, range);
		int oz = this.spreadType().evaluate(random, range);
		cir.setReturnValue(new ChunkPos(i * spacing + ox, j * spacing + oz));
	}

	private static int maxSpacingChunks() {
		return Math.max(12, PlanetWorldConfig.chunkWidth() / 4);
	}
}
