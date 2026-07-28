package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.worldgen.ContinentalClimate;
import com.planetworld.worldgen.OverworldBiomeSeedPlacer;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Continents: landmask + signed N/S climate; at >=4096 sparse biome seeds.
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
	@Shadow
	public abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint targetPoint);

	@WrapMethod(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;")
	private Holder<Biome> planetworld$continentalLatitude(
			int quartX,
			int quartY,
			int quartZ,
			Climate.Sampler sampler,
			Operation<Holder<Biome>> original
	) {
		if (!ContinentalClimate.shouldRemap()) {
			return original.call(quartX, quartY, quartZ, sampler);
		}
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		if (!self.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
			return original.call(quartX, quartY, quartZ, sampler);
		}

		double blockX = QuartPos.toBlock(quartX);
		double blockZ = QuartPos.toBlock(quartZ);

		Holder<Biome> seeded = OverworldBiomeSeedPlacer.biomeAt(blockX, blockZ);
		if (seeded != null) {
			return seeded;
		}

		Climate.TargetPoint point = sampler.sample(quartX, quartY, quartZ);
		return this.getNoiseBiome(ContinentalClimate.remap(point, blockX, blockZ));
	}
}
