/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldgen.other.densityFunctions;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunction$NoiseHolder")
public interface DensityFunctionNoiseHolderAccessor {
	@Accessor("noiseData")
	Holder<NormalNoise.NoiseParameters> planetworld$getNoiseData();
}
