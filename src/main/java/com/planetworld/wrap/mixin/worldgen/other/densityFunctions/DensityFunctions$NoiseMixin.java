/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.worldgen.other.densityFunctions;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.storage.TransformerRequests;
import com.planetworld.worldgen.ContinentalLandmask;
import com.planetworld.worldgen.ContinentalMountains;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public class DensityFunctions$NoiseMixin {
	@Shadow @Final private DensityFunction.NoiseHolder noise;
	@Shadow @Final private double xzScale;
	@Shadow @Final private double yScale;

	@Inject(method = "compute", at = @At("HEAD"), cancellable = true)
	public void compute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
		if (!TransformerRequests.useWrappedWorldGen()) {
			return;
		}
		if (PlanetWorldConfig.isContinental() && applyContinentalDensity(context, cir)) {
			return;
		}
		cir.setReturnValue(this.noise.getValue(context.blockX(), (double) context.blockY() * this.yScale, context.blockZ()));
	}

	private boolean applyContinentalDensity(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
		String path = noisePath(this.noise);
		ServerLevel level = TransformerRequests.noiseLevel;
		long seed = level != null ? level.getSeed() : 0L;
		double x = context.blockX();
		double z = context.blockZ();
		if (path.contains("continentalness")) {
			cir.setReturnValue((double) ContinentalLandmask.continentalness(x, z, seed));
			return true;
		}
		float land = ContinentalLandmask.landFactor(x, z, seed);
		float mountain = ContinentalMountains.mountainFactor(x, z, seed, land);
		if (path.contains("ridge")) {
			double ridge = this.noise.getValue(x, (double) context.blockY() * this.yScale, z);
			cir.setReturnValue(ContinentalMountains.reshapeRidge(ridge, land, mountain));
			return true;
		}
		if (path.contains("erosion")) {
			double erosion = this.noise.getValue(x, (double) context.blockY() * this.yScale, z);
			cir.setReturnValue(ContinentalMountains.reshapeErosion(erosion, land, mountain));
			return true;
		}
		if (path.contains("jagged")) {
			double jagged = this.noise.getValue(x, (double) context.blockY() * this.yScale, z);
			cir.setReturnValue(ContinentalMountains.reshapeJaggedness(jagged, mountain));
			return true;
		}
		return false;
	}

	private static String noisePath(DensityFunction.NoiseHolder holder) {
		Holder<NormalNoise.NoiseParameters> data = ((DensityFunctionNoiseHolderAccessor) (Object) holder).planetworld$getNoiseData();
		return data.unwrapKey().map(k -> k.location().getPath()).orElse("");
	}
}
