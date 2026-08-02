package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.worldgen.GuaranteedDungeonPlacer;
import com.planetworld.worldgen.GuaranteedStructures;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Locate radius clamp, Continents nether fortress/bastion type pinning,
 * and guaranteed dungeon placement.
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
		int half = Math.max(1, width / 2);
		int maxRings = Math.max(4, half / 4 + 2);
		return Math.min(searchRadius, maxRings);
	}

	@Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
	private void planetworld$pinNetherComplexType(
			StructureSet.StructureSelectionEntry structureSelectionEntry,
			StructureManager structureManager,
			RegistryAccess registryAccess,
			RandomState random,
			StructureTemplateManager structureTemplateManager,
			long seed,
			ChunkAccess chunk,
			ChunkPos chunkPos,
			SectionPos sectionPos,
			CallbackInfoReturnable<Boolean> cir
	) {
		GuaranteedStructures.NetherComplex required =
				GuaranteedStructures.netherComplexAt(seed, chunkPos.x, chunkPos.z);
		if (required == null) {
			return;
		}
		ResourceLocation id = structureSelectionEntry.structure()
				.unwrapKey()
				.map(ResourceKey::location)
				.orElse(null);
		if (id == null) {
			return;
		}
		boolean isFortress = id.equals(ResourceLocation.withDefaultNamespace("fortress"));
		boolean isBastion = id.equals(ResourceLocation.withDefaultNamespace("bastion_remnant"));
		if (required == GuaranteedStructures.NetherComplex.FORTRESS && !isFortress) {
			cir.setReturnValue(false);
		} else if (required == GuaranteedStructures.NetherComplex.BASTION && !isBastion) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
	private void planetworld$forceGuaranteedDungeons(
			WorldGenLevel level,
			ChunkAccess chunk,
			StructureManager structureManager,
			CallbackInfo ci
	) {
		GuaranteedDungeonPlacer.tryPlace(level, chunk);
	}
}
