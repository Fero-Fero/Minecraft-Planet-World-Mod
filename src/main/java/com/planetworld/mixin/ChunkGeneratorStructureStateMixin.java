package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.worldgen.ContinentalClimate;
import com.planetworld.worldgen.GuaranteedStructures;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Continents: place stronghold rings without expensive biome scans, and always
 * include a land-anchored stronghold so an End portal exists.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {
	@Shadow
	public abstract long getLevelSeed();

	@Inject(method = "generateRingPositions", at = @At("HEAD"), cancellable = true)
	private void planetworld$fastStrongholdRings(
			Holder<StructureSet> structureSet,
			ConcentricRingsStructurePlacement placement,
			CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> cir
	) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return;
		}
		int count = Math.max(1, placement.count());
		int distance = placement.distance();
		int spread = Math.max(1, placement.spread());
		int width = Math.max(8, PlanetWorldConfig.chunkWidth());
		List<ChunkPos> positions = new ArrayList<>(count);

		ChunkPos guaranteed = GuaranteedStructures.strongholdChunk(this.getLevelSeed());
		if (guaranteed != null) {
			positions.add(guaranteed);
		}

		double angle = Math.PI * 0.25;
		int placedOnRing = 0;
		int ringIndex = 0;
		int ringSpread = spread;
		while (positions.size() < count) {
			double radius = (4.0 * distance + distance * ringIndex * 6) + distance;
			radius = Math.min(radius, width * 0.35);
			int cx = (int) Math.round(Math.cos(angle) * radius);
			int cz = (int) Math.round(Math.sin(angle) * radius);
			ChunkPos next = new ChunkPos(cx, cz);
			if (guaranteed == null || !next.equals(guaranteed)) {
				positions.add(next);
			}
			angle += (Math.PI * 2.0) / ringSpread;
			if (++placedOnRing >= ringSpread) {
				ringIndex++;
				placedOnRing = 0;
				ringSpread = Math.max(1, ringSpread + 1);
				angle += Math.PI * 0.35;
			}
		}
		cir.setReturnValue(CompletableFuture.completedFuture(positions));
	}
}
