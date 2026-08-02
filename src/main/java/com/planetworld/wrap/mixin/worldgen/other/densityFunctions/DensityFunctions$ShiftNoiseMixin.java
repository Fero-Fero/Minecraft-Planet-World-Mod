/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.worldgen.other.densityFunctions;

import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftNoise")
public interface DensityFunctions$ShiftNoiseMixin {
	@Shadow DensityFunction.NoiseHolder offsetNoise();

	@Inject(method = "compute", at = @At("HEAD"), cancellable = true)
	default void compute(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
		if(TransformerRequests.useWrappedWorldGen()) {
			cir.setReturnValue(offsetNoise().getValue(x, y * 0.25, z) * 4.0);
		}
	}
}
