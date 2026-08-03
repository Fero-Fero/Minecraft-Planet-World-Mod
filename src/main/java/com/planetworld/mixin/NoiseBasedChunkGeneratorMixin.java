package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Realism mode: swap overworld noise settings to vanilla LARGE_BIOMES
 * for stretched climate / larger landmasses. Terralith 2.6+ supports Large Biomes;
 * does not touch wrap mixins.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
	@Shadow @Final @Mutable
	private Holder<NoiseGeneratorSettings> settings;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void planetworld$applyContinentalLargeBiomes(
			BiomeSource biomeSource,
			Holder<NoiseGeneratorSettings> settingsIn,
			CallbackInfo ci
	) {
		if (!PlanetWorldConfig.isRealism()) {
			return;
		}
		if (!settingsIn.is(NoiseGeneratorSettings.OVERWORLD)) {
			return;
		}

		HolderLookup.RegistryLookup<NoiseGeneratorSettings> lookup = settingsIn.unwrapLookup();
		if (lookup != null) {
			this.settings = lookup.getOrThrow(NoiseGeneratorSettings.LARGE_BIOMES);
			return;
		}

		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return;
		}
		this.settings = server.registryAccess()
				.registryOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
				.getHolderOrThrow(NoiseGeneratorSettings.LARGE_BIOMES);
	}
}
