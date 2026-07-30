package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Display-link / click-to-link selection range is Euclidean {@code closerThan}. Seam-adjacent
 * targets must measure the short path or linking across the cut fails.
 */
@Mixin(ClickToLinkBlockItem.class)
public abstract class ClickToLinkBlockItemMixin {

	@WrapOperation(
			method = "useOn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
			)
	)
	private boolean planetworld$torusCloserThan(
			BlockPos self,
			Vec3i other,
			double distance,
			Operation<Boolean> original,
			UseOnContext context
	) {
		Level level = context.getLevel();
		DimensionTransformer t = CreateWrapMath.transformer(level);
		if (!t.isWrapped()) {
			return original.call(self, other, distance);
		}
		return CreateWrapMath.unwrapDistSqr(level, self, other) < distance * distance;
	}
}
