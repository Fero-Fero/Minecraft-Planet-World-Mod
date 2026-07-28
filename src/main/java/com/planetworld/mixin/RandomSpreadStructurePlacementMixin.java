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
 * Continents ≥2048: keep random-spread structures inside the wrap so mansions,
 * monuments, outposts, etc. can actually generate on a finite planet.
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
		RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
		int maxSep = Math.max(0, self.spacing() - 1);
		if (cir.getReturnValue() > maxSep) {
			cir.setReturnValue(maxSep);
		}
	}

	/**
	 * Vanilla reads raw fields here; re-run with clamped {@link #spacing()}/{@link #separation()}
	 * so worldgen matches locate.
	 */
	@Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
	private void planetworld$clampedPotentialChunk(long seed, int regionX, int regionZ, CallbackInfoReturnable<ChunkPos> cir) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
		int spacing = self.spacing();
		int separation = self.separation();
		int i = Math.floorDiv(regionX, spacing);
		int j = Math.floorDiv(regionZ, spacing);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		int salt = ((StructurePlacementAccessor) self).planetworld$getSalt();
		random.setLargeFeatureWithSalt(seed, i, j, salt);
		int range = Math.max(1, spacing - separation);
		int ox = this.spreadType().evaluate(random, range);
		int oz = this.spreadType().evaluate(random, range);
		cir.setReturnValue(new ChunkPos(i * spacing + ox, j * spacing + oz));
	}

	/** At least ~4 placement cells across the torus (width/4), never below 12. */
	private static int maxSpacingChunks() {
		return Math.max(12, PlanetWorldConfig.chunkWidth() / 4);
	}
}
