/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.processing.worldgen.OpenSimplex2S;
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
 * Periodic XZ noise via a 4D torus embedding so terrain matches across the wrap.
 * <p>
 * Callers pass octave-scaled coordinates ({@code block * scale}). We convert back
 * to block space for the angle so every octave shares the same seam, then scale
 * the embedding radius by {@code scale} for higher-frequency detail.
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
		if (TransformerRequests.noiseLevel == null) {
			return original.call(x, y, z, yScale, yMax);
		}
		DimensionTransformer transformer = TransformerRequests.noiseLevel.getTransformer();
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

		double scale = TransformerRequests.noiseXzScale();
		double blockX = x / scale;
		double blockZ = z / scale;

		double periodX = Math.max(16.0, transformer.xWidth * 16.0);
		double periodZ = Math.max(16.0, transformer.zWidth * 16.0);
		double thetaX = ((blockX + transformer.wrappingSettings.xChunkBoundMin() * 16.0) / periodX) * (Math.PI * 2.0);
		double thetaZ = ((blockZ + transformer.wrappingSettings.zChunkBoundMin() * 16.0) / periodZ) * (Math.PI * 2.0);
		// Feature size ~vanilla; multiply by octave scale for higher frequencies without breaking the seam.
		double r = Math.max(2.0, Math.min(periodX, periodZ) / 128.0) * Math.abs(scale);
		double yCoord = (y - n) / 32.0;

		return OpenSimplex2S.noise4_Fallback(
				source,
				r * Math.sin(thetaX),
				r * Math.cos(thetaX),
				r * Math.sin(thetaZ),
				yCoord
		);
	}
}
