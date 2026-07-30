/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.entity.collisions;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * EntityGetter hooks that stay valid when Sable {@code @Overwrite}s {@code isUnobstructed}.
 * <p>
 * A {@code @Redirect} on that method fails mixin apply once Sable replaces the body. MixinExtras
 * {@link WrapOperation} attaches to {@code Shapes.joinIsNotEmpty} inside whichever body remains
 * (vanilla or Sable). Remap only when the two AABBs sit across a bound so Sable's sub-level-local
 * tests are not disturbed.
 */
@Mixin(value = EntityGetter.class, priority = 2000)
public interface EntityGetterMixin {
	@Shadow List<? extends Player> players();

	@WrapOperation(
			method = "isUnobstructed",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"
			)
	)
	default boolean planetworld$torusJoinIsNotEmpty(
			VoxelShape shape,
			VoxelShape other,
			BooleanOp op,
			Operation<Boolean> original
	) {
		EntityGetter self = (EntityGetter) (Object) this;
		if (!(self instanceof ServerLevel level)) {
			return original.call(shape, other, op);
		}
		DimensionTransformer transformer = level.getTransformer();
		if (transformer == null || !transformer.isWrapped() || shape.isEmpty() || other.isEmpty()) {
			return original.call(shape, other, op);
		}

		AABB ref = shape.bounds();
		AABB candidate = other.bounds();
		if (!planetworld$needsHorizontalUnwrap(transformer, ref, candidate)) {
			return original.call(shape, other, op);
		}

		VoxelShape remapped = Shapes.create(transformer.AABoundingBox.unwrap(ref, candidate));
		return original.call(shape, remapped, op);
	}

	private static boolean planetworld$needsHorizontalUnwrap(DimensionTransformer transformer, AABB ref, AABB other) {
		return transformer.Coord.X.needsUnwrap(ref.minX, other.minX)
				|| transformer.Coord.X.needsUnwrap(ref.maxX, other.maxX)
				|| transformer.Coord.Z.needsUnwrap(ref.minZ, other.minZ)
				|| transformer.Coord.Z.needsUnwrap(ref.maxZ, other.maxZ);
	}

	@Inject(method = "getNearbyPlayers", at = @At("HEAD"), cancellable = true)
	default void includeWrappedPlayers(TargetingConditions predicate, LivingEntity target, AABB area, CallbackInfoReturnable<List<Player>> cir) {
		List<Player> list = Lists.newArrayList();

		for (Player player : this.players()) {
			DimensionTransformer transformer = player.level().getTransformer();
			if (area.contains(transformer.Coord.X.unwrap(area.minX, player.getX()), player.getY(), transformer.Coord.Z.unwrap(area.minZ, player.getZ())) && predicate.test(target, player)) {
				list.add(player);
			}
		}

		cir.setReturnValue(list);
	}
}
