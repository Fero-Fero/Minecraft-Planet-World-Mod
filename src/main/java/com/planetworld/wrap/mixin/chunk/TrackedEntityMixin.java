/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Priority 2000 so this wrapper applies last. Sable also patches {@code updatePlayer}, feeding it
 * sub-level-projected entity positions, and those belong in world space before the range test.
 */
@Mixin(value = ChunkMap.TrackedEntity.class, priority = 2000)
public abstract class TrackedEntityMixin {
	/**
	 * Updates an entity's player tracking based on the player's distance. Modified to support wrapped distances.
	 */
	@WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
	public Vec3 unwrapVec(Vec3 playerPos, Vec3 entityPos, Operation<Vec3> original, @Local(argsOnly = true) ServerPlayer player) {
		DimensionTransformer transformer = player.level().getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return original.call(playerPos, entityPos);
		}
		return original.call(playerPos, transformer.Vector3D.unwrap(playerPos, entityPos));
	}
}
