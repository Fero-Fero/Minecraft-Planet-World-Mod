/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.gameevent;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Priority 2000 so this wrapper applies last. Sable wraps the same instruction to let vibrations reach
 * listeners inside sub-levels; two wrappers nest cleanly, whereas a plain {@code @Redirect} beside one
 * only survives if it happens to apply first.
 */
@Mixin(value = EuclideanGameEventListenerRegistry.class, priority = 2000)
public class EuclideanGameEventListenerRegistryMixin {
	/**
	 * Vibration range is measured on the short path around the world: move the listener onto the
	 * representative nearest the event and let whatever owns the comparison do the arithmetic.
	 */
	@WrapOperation(method = "getPostableListenerPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
	private static double planetworld$torusDistSqr(BlockPos eventPos, Vec3i listenerPos, Operation<Double> original, @Local(argsOnly = true) ServerLevel level) {
		DimensionTransformer transformer = level.getTransformer();
		if (transformer == null || !transformer.SSO().isWrapped()) {
			return original.call(eventPos, listenerPos);
		}
		BlockPos unwrapped = transformer.SSO().Block.unwrap(eventPos, new BlockPos(listenerPos.getX(), listenerPos.getY(), listenerPos.getZ()));
		return original.call(eventPos, unwrapped);
	}
}
