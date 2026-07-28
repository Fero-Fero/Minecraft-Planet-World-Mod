package com.planetworld.mixin;

import com.planetworld.worldgen.GuaranteedStructures;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Force Continents structure anchors (mansion, villages, outposts, temples,
 * ocean monuments, nether fortresses / bastions).
 */
@Mixin(StructurePlacement.class)
public abstract class StructurePlacementMixin {
	@Inject(method = "isStructureChunk", at = @At("HEAD"), cancellable = true)
	private void planetworld$forceGuaranteedChunks(
			ChunkGeneratorStructureState structureState,
			int chunkX,
			int chunkZ,
			CallbackInfoReturnable<Boolean> cir
	) {
		int salt = ((StructurePlacementAccessor) this).planetworld$getSalt();
		if (GuaranteedStructures.isForcedChunk(salt, structureState.getLevelSeed(), chunkX, chunkZ)) {
			cir.setReturnValue(true);
		}
	}
}
