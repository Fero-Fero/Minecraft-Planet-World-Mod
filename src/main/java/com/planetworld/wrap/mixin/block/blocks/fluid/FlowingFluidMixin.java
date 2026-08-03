package com.planetworld.wrap.mixin.block.blocks.fluid;

import com.planetworld.wrap.core.DimensionTransformer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Normalize the fluid origin into wrapped bounds.
 * <p>
 * Do <em>not</em> use {@code BlockPosWrapped}: its {@code relative()} remaps across the
 * seam, so Lithium's diamond-offset cache ({@code indexFromDiamondXZOffset}) sees
 * {@code |dx|/|dz|} ≫ search radius and crashes with
 * {@code ArrayIndexOutOfBoundsException} in {@code getBlock}. Level already wraps
 * {@code getBlockState}/{@code setBlock} via {@code LevelMixin}, which keeps local
 * ±1 offsets intact for Lithium while still resolving the far side of the seam.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
	@ModifyVariable(method = "spread", at = @At("HEAD"), argsOnly = true, index = 2)
	public BlockPos wrapSpread(BlockPos blockPos, @Local(argsOnly = true) Level level) {
		if (!(level instanceof ServerLevel)) {
			return blockPos;
		}
		DimensionTransformer transformer = level.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return blockPos;
		}
		return transformer.SSO().Block.wrap(blockPos);
	}
}
