/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldgen;

import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same structure as vanilla {@link BlendedNoise}, but passes blockX/Z * octave into
 * {@link ImprovedNoise} with {@link TransformerRequests#setNoiseXzScale(double)} so
 * the torus mapper can keep the wrap seam continuous.
 */
@Mixin(BlendedNoise.class)
public class BlendedNoiseMixin {
	@Shadow @Final private PerlinNoise minLimitNoise;
	@Shadow @Final private PerlinNoise maxLimitNoise;
	@Shadow @Final private PerlinNoise mainNoise;
	@Shadow @Final private double xzMultiplier;
	@Shadow @Final private double yMultiplier;
	@Shadow @Final private double xzFactor;
	@Shadow @Final private double yFactor;
	@Shadow @Final private double smearScaleMultiplier;

	@Inject(method = "<init>(Lnet/minecraft/util/RandomSource;DDDDD)V", at = @At("TAIL"))
	public void init(RandomSource random, double xzScale, double yScale, double xzFactor, double yFactor, double smearScaleMultiplier, CallbackInfo ci) {
	}

	@Inject(method = "compute", at = @At("HEAD"), cancellable = true)
	public void compute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
		if (TransformerRequests.noiseLevel == null
				|| !TransformerRequests.noiseLevel.getTransformer().wrappingSettings.useWrappedWorldGen()) {
			return;
		}

		double d = context.blockX() * this.xzMultiplier;
		double e = context.blockY() * this.yMultiplier;
		double f = context.blockZ() * this.xzMultiplier;
		double g = d / this.xzFactor;
		double h = e / this.yFactor;
		double i = f / this.xzFactor;
		double j = this.yMultiplier * this.smearScaleMultiplier;
		double k = j / this.yFactor;
		double l = 0.0;
		double m = 0.0;
		double n = 0.0;
		double o = 1.0;

		try {
			for (int p = 0; p < 8; p++) {
				ImprovedNoise improvedNoise = this.mainNoise.getOctaveNoise(p);
				if (improvedNoise != null) {
					TransformerRequests.setNoiseXzScale(o);
					n += improvedNoise.noise(
							context.blockX() * o,
							PerlinNoise.wrap(h * o),
							context.blockZ() * o,
							k * o,
							h * o
					) / o;
				}
				o /= 2.0;
			}

			double q = (n / 10.0 + 1.0) / 2.0;
			boolean bl2 = q >= 1.0;
			boolean bl3 = q <= 0.0;
			o = 1.0;

			for (int r = 0; r < 16; r++) {
				double t = PerlinNoise.wrap(e * o);
				double v = j * o;
				if (!bl2) {
					ImprovedNoise improvedNoise2 = this.minLimitNoise.getOctaveNoise(r);
					if (improvedNoise2 != null) {
						TransformerRequests.setNoiseXzScale(o);
						l += improvedNoise2.noise(context.blockX() * o, t, context.blockZ() * o, v, e * o) / o;
					}
				}
				if (!bl3) {
					ImprovedNoise improvedNoise2 = this.maxLimitNoise.getOctaveNoise(r);
					if (improvedNoise2 != null) {
						TransformerRequests.setNoiseXzScale(o);
						m += improvedNoise2.noise(context.blockX() * o, t, context.blockZ() * o, v, e * o) / o;
					}
				}
				o /= 2.0;
			}

			cir.setReturnValue(Mth.clampedLerp(l / 512.0, m / 512.0, q) / 128.0);
		} finally {
			TransformerRequests.clearNoiseXzScale();
		}
	}
}
