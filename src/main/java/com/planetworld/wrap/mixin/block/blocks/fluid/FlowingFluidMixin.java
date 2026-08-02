package com.planetworld.wrap.mixin.block.blocks.fluid;

import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.processing.BlockPosWrapped;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
	@ModifyVariable(method = "spread", at = @At("HEAD"), argsOnly = true, index = 2)
	public BlockPos wrapSpread(BlockPos blockPos, @Local(argsOnly = true) Level level) {
		// Avoid wrapping during WorldGenRegion / early generation — remapped
		// neighbor positions can request chunks outside the gen cache.
		if (!(level instanceof ServerLevel)) {
			return blockPos;
		}
		DimensionTransformer transformer = level.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return blockPos;
		}
		return new BlockPosWrapped(blockPos, transformer.SSO());
	}
}
