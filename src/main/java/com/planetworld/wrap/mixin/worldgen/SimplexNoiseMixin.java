/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.processing.worldgen.OpenSimplex2S;
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
	public double getValue(double x, double y, Operation<Double> original) {
		DimensionTransformer transformer = safeTransformer();
		if (transformer == null || !transformer.wrappingSettings.useWrappedWorldGen()) {
			return original.call(x, y);
		}
		return torus2(transformer, x, y);
	}

	@WrapMethod(method = "getValue(DDD)D")
	public double getValue(double x, double y, double z, Operation<Double> original) {
		DimensionTransformer transformer = safeTransformer();
		if (transformer == null || !transformer.wrappingSettings.useWrappedWorldGen()) {
			return original.call(x, y, z);
		}
		// Use Z for the second horizontal axis (upstream Circumnavigate incorrectly used Y).
		double periodX = Math.max(16.0, transformer.xWidth * 16.0);
		double periodZ = Math.max(16.0, transformer.zWidth * 16.0);
		double thetaX = ((x + transformer.wrappingSettings.xChunkBoundMin() * 16.0) / periodX) * (Math.PI * 2.0);
		double thetaZ = ((z + transformer.wrappingSettings.zChunkBoundMin() * 16.0) / periodZ) * (Math.PI * 2.0);
		double r = Math.max(2.0, Math.min(periodX, periodZ) / 128.0);
		return OpenSimplex2S.noise4_Fallback(
				source,
				r * Math.sin(thetaX),
				r * Math.cos(thetaX),
				r * Math.sin(thetaZ),
				y / 32.0
		);
	}

	private double torus2(DimensionTransformer transformer, double x, double z) {
		double periodX = Math.max(16.0, transformer.xWidth * 16.0);
		double periodZ = Math.max(16.0, transformer.zWidth * 16.0);
		double thetaX = ((x + transformer.wrappingSettings.xChunkBoundMin() * 16.0) / periodX) * (Math.PI * 2.0);
		double thetaZ = ((z + transformer.wrappingSettings.zChunkBoundMin() * 16.0) / periodZ) * (Math.PI * 2.0);
		double r = Math.max(2.0, Math.min(periodX, periodZ) / 128.0);
		return OpenSimplex2S.noise4_Fallback(
				source,
				r * Math.sin(thetaX),
				r * Math.cos(thetaX),
				r * Math.sin(thetaZ),
				r * Math.cos(thetaZ)
		);
	}

	private static DimensionTransformer safeTransformer() {
		if (TransformerRequests.noiseLevel == null) {
			return null;
		}
		return TransformerRequests.noiseLevel.getTransformer();
	}
}
