/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.processing.worldgen.TorusNoise;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Seamless torus replacement for ImprovedNoise. Matches classic wrap feel at
 * circumference 256; scales embedding on larger planets only.
 */
@Mixin(ImprovedNoise.class)
public class ImprovedNoiseMixin {
	@Final @Shadow private byte[] p;
	@Final @Shadow public double xo;
	@Final @Shadow public double yo;
	@Final @Shadow public double zo;

	private long source;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void init(RandomSource random, CallbackInfo ci) {
		source = random.nextLong();
	}

	@WrapMethod(method = "noise(DDDDD)D")
	public double noise(double x, double y, double z, double yScale, double yMax, Operation<Double> original) {
		DimensionTransformer transformer = TransformerRequests.noiseTransformerOrNull();
		if (transformer == null || !transformer.wrappingSettings.useWrappedWorldGen()) {
			return original.call(x, y, z, yScale, yMax);
		}

		int intY = Mth.floor(y);
		double deltaY = y - intY;
		double n;
		if (yScale != 0.0) {
			double m = (yMax >= 0.0 && yMax < deltaY) ? yMax : deltaY;
			n = (double) Mth.floor(m / yScale + 1.0E-7F) * yScale;
		} else {
			n = 0.0;
		}

		return TorusNoise.sample3(source, transformer, x, y - n, z);
	}
}
