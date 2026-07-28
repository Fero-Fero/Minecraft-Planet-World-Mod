package com.planetworld.mixin;

import com.planetworld.worldgen.BiomeStructureCoverage;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Complete coverage: reserve a patch per biome inside the wrap period so every
 * overworld biome appears at least once. Edges of each cell stay vanilla.
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
	@Inject(
			method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void planetworld$forceCoverageBiome(
			int quartX,
			int quartY,
			int quartZ,
			Climate.Sampler sampler,
			CallbackInfoReturnable<Holder<Biome>> cir
	) {
		Holder<Biome> forced = BiomeStructureCoverage.forcedBiomeAt(
				QuartPos.toBlock(quartX),
				QuartPos.toBlock(quartZ),
				(MultiNoiseBiomeSource) (Object) this
		);
		if (forced != null) {
			cir.setReturnValue(forced);
		}
	}
}
