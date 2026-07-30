/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.entity.collisions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.processing.BlockHitResultWrapped;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code clip} lives on {@link BlockGetter} as a default method — mixing {@link Level} never sees it.
 * Priority 2000 so this sits outside Sable's clip overwrite when both apply.
 */
@Mixin(value = BlockGetter.class, priority = 2000)
public interface LevelClipMixin {

	/**
	 * Cast along the short torus path when the ray endpoints straddle a bound.
	 */
	@WrapOperation(
			method = "clip",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ClipContext;getTo()Lnet/minecraft/world/phys/Vec3;")
	)
	default Vec3 planetworld$shortPathTo(ClipContext context, Operation<Vec3> original) {
		Vec3 to = original.call(context);
		if (!(this instanceof Level level)) {
			return to;
		}
		DimensionTransformer transformer = level.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return to;
		}
		transformer = transformer.SSO();
		Vec3 from = context.getFrom();
		if (!transformer.Coord.X.needsUnwrap(from.x, to.x) && !transformer.Coord.Z.needsUnwrap(from.z, to.z)) {
			return to;
		}
		return transformer.Vector3D.unwrap(from, to);
	}

	/**
	 * Present a wrapped hit so downstream distance / use-item code stays in domain.
	 */
	@WrapMethod(method = "clip")
	default BlockHitResult planetworld$wrapHit(ClipContext context, Operation<BlockHitResult> original) {
		BlockHitResult hit = original.call(context);
		if (hit.getType() == HitResult.Type.MISS || !(this instanceof Level level)) {
			return hit;
		}
		DimensionTransformer transformer = level.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return hit;
		}
		return new BlockHitResultWrapped(hit, transformer.SSO());
	}
}
