package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.entity.TrainMigration;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * After a graph heal or merge, trains rematch travelling points with Euclidean distance and sphere
 * intersects. Across a bound that rematch fails or snaps to the wrong node; use the short path.
 */
@Mixin(TrainMigration.class)
public abstract class TrainMigrationMixin {

	@WrapOperation(
			method = "tryMigratingTo",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double planetworld$torusMigrateCull(Vec3 from, Vec3 to, Operation<Double> original) {
		DimensionTransformer t = CreateWrapMath.bestEffortTransformer();
		return t.isWrapped() ? CreateWrapMath.unwrapDistanceSqr(t, from, to) : original.call(from, to);
	}

	@WrapOperation(
			method = "tryMigratingTo",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double planetworld$torusMigrateDistance(Vec3 from, Vec3 to, Operation<Double> original) {
		DimensionTransformer t = CreateWrapMath.bestEffortTransformer();
		return t.isWrapped() ? CreateWrapMath.unwrapDistance(t, from, to) : original.call(from, to);
	}

	@WrapOperation(
			method = "tryMigratingTo",
			at = @At(
					value = "INVOKE",
					target = "Lnet/createmod/catnip/math/VecHelper;intersectSphere(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"
			)
	)
	private Vec3 planetworld$torusMigrateSphere(
			Vec3 start,
			Vec3 end,
			Vec3 center,
			double radius,
			Operation<Vec3> original
	) {
		DimensionTransformer t = CreateWrapMath.bestEffortTransformer();
		if (!t.isWrapped()) {
			return original.call(start, end, center, radius);
		}
		Vec3 endU = CreateWrapMath.unwrapRelative(t, start, end);
		Vec3 centerU = CreateWrapMath.unwrapRelative(t, start, center);
		Vec3 hit = original.call(start, endU, centerU, radius);
		return hit == null ? null : t.Vector3D.wrap(hit);
	}
}
