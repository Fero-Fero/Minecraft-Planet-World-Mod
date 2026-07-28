/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.processing.worldgen.TorusNoise;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimplexNoise.class)
public abstract class SimplexNoiseMixin {
	private long source;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void init(RandomSource random, CallbackInfo ci) {
		source = random.nextLong();
	}

	@WrapMethod(method = "getValue(DD)D")
	public double getValue(double x, double z, Operation<Double> original) {
		DimensionTransformer transformer = safeTransformer();
		if (transformer == null || !transformer.wrappingSettings.useWrappedWorldGen()) {
			return original.call(x, z);
		}
		return TorusNoise.sampleHorizontal(source, transformer, x, z);
	}

	@WrapMethod(method = "getValue(DDD)D")
	public double getValue(double x, double y, double z, Operation<Double> original) {
		DimensionTransformer transformer = safeTransformer();
		if (transformer == null || !transformer.wrappingSettings.useWrappedWorldGen()) {
			return original.call(x, y, z);
		}
		return TorusNoise.sample3(source, transformer, x, y, z);
	}

	private static DimensionTransformer safeTransformer() {
		return TransformerRequests.noiseTransformerOrNull();
	}
}
