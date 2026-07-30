package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Placing a signal / station / observer against a track rejects targets that are wrap-adjacent but
 * Euclidean-far. Measure the short path so targeting works across a bound.
 */
@Mixin(TrackTargetingBlockItem.class)
public abstract class TrackTargetingBlockItemMixin {

	@WrapOperation(
			method = "useOn",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z")
	)
	private boolean planetworld$torusTargetRange(BlockPos self, Vec3i other, double distance, Operation<Boolean> original, UseOnContext context) {
		DimensionTransformer t = CreateWrapMath.transformer(context.getLevel());
		if (!t.isWrapped()) {
			return original.call(self, other, distance);
		}
		return CreateWrapMath.unwrapDistSqr(context.getLevel(), self, other) < distance * distance;
	}
}
