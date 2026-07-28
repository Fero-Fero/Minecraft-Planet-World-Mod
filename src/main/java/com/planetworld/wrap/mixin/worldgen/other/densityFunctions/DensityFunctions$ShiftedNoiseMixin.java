/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.worldgen.other.densityFunctions;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.storage.TransformerRequests;
import com.planetworld.worldgen.ContinentalLandmask;
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

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
public class DensityFunctions$ShiftedNoiseMixin {
	@Shadow @Final private DensityFunction shiftX;
	@Shadow @Final private DensityFunction shiftY;
	@Shadow @Final private DensityFunction shiftZ;
	@Shadow @Final private double xzScale;
	@Shadow @Final private double yScale;
	@Shadow @Final private DensityFunction.NoiseHolder noise;

	@Inject(method = "compute", at = @At("HEAD"), cancellable = true)
	public void compute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
		if (!TransformerRequests.useWrappedWorldGen()) {
			return;
		}
		String path = noisePath(this.noise);
		ServerLevel level = TransformerRequests.noiseLevel;
		long seed = level != null ? level.getSeed() : 0L;
		double x = context.blockX();
		double z = context.blockZ();
		if (PlanetWorldConfig.isContinental() && path.contains("continentalness")) {
			cir.setReturnValue((double) ContinentalLandmask.continentalness(x, z, seed));
			return;
		}
		if (PlanetWorldConfig.isContinental() && path.contains("ridge")) {
			float land = ContinentalLandmask.landFactor(x, z, seed);
			double ridge = this.noise.getValue(x, context.blockY() * this.yScale + this.shiftY.compute(context), z);
			cir.setReturnValue(ridge * (1.0 - 0.8 * land));
			return;
		}
		cir.setReturnValue(this.noise.getValue(x, context.blockY() * this.yScale + this.shiftY.compute(context), z));
	}

	private static String noisePath(DensityFunction.NoiseHolder holder) {
		Holder<NormalNoise.NoiseParameters> data = ((DensityFunctionNoiseHolderAccessor) (Object) holder).planetworld$getNoiseData();
		return data.unwrapKey().map(k -> k.location().getPath()).orElse("");
	}
}
