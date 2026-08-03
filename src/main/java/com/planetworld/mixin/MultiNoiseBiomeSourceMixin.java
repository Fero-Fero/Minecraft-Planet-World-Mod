package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.worldgen.ContinentalClimate;
import com.planetworld.worldgen.GuaranteedStructures;
import com.planetworld.worldgen.OverworldBiomeSeedPlacer;
import com.planetworld.worldgen.terralith.TerralithBiomeSeedPlacer;
import com.planetworld.worldgen.terralith.TerralithCompat;
import com.planetworld.wrap.processing.worldgen.OpenSimplex2S;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Realism: landmask + signed N/S climate; sparse vanilla + Terralith biome seeds; bamboo dampening.
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
		if (!TerralithCompat.isRemappableOverworldSource(self)) {
			return original.call(quartX, quartY, quartZ, sampler);
		}

		double blockX = QuartPos.toBlock(quartX);
		double blockZ = QuartPos.toBlock(quartZ);
		ServerLevel level = TransformerRequests.noiseLevel;

		if (level != null) {
			Holder<Biome> forced = GuaranteedStructures.biomeOverride(blockX, blockZ, level);
			if (forced != null) {
				return forced;
			}
		}

		Holder<Biome> seeded = OverworldBiomeSeedPlacer.biomeAt(blockX, blockZ);
		if (seeded != null) {
			return seeded;
		}

		Holder<Biome> terralithSeeded = TerralithBiomeSeedPlacer.biomeAt(blockX, blockZ);
		if (terralithSeeded != null) {
			return terralithSeeded;
		}

		Climate.TargetPoint point = sampler.sample(quartX, quartY, quartZ);
		Holder<Biome> biome = this.getNoiseBiome(ContinentalClimate.remap(point, blockX, blockZ));
		return dampenBamboo(biome, blockX, blockZ, level);
	}

	/**
	 * Bamboo jungle only survives rare noise peaks (or explicit seeds/anchors).
	 */
	private Holder<Biome> dampenBamboo(Holder<Biome> biome, double blockX, double blockZ, ServerLevel level) {
		if (!biome.is(Biomes.BAMBOO_JUNGLE)) {
			return biome;
		}
		long seed = level != null ? level.getSeed() : 0L;
		float keep = OpenSimplex2S.noise2(seed ^ 0xBA4B001L, blockX / 180.0, blockZ / 180.0);
		if (keep > 0.88f) {
			return biome; // rare small bamboo pockets
		}
		if (level == null) {
			return biome;
		}
		return level.registryAccess()
				.lookupOrThrow(Registries.BIOME)
				.get(Biomes.JUNGLE)
				.map(h -> (Holder<Biome>) h)
				.orElse(biome);
	}
}
