/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.accessors.WrapsOwnPosition;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Priority 2000 so the wrappers below sit outside anything else that owns these methods. Sable
 * {@code @Overwrite}s all three distance queries to reach into sub-levels; applying last means Planet
 * World corrects the operands and then hands them to that body instead of replacing it.
 */
@Mixin(value = Entity.class, priority = 2000)
public abstract class EntityMixin {
	@Shadow private Level level;

	@Unique
	private Entity planetworld$self() {
		return (Entity) (Object) this;
	}

	@Unique
	private DimensionTransformer planetworld$serverTransformer() {
		DimensionTransformer transformer = level == null ? null : level.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return DimensionTransformer.DISABLED;
		}
		// Only wrap on the live server level. During WorldGenRegion spawn/fluid work,
		// remapping positions can request chunks outside the generation cache.
		if (!(level instanceof ServerLevel)) {
			return DimensionTransformer.DISABLED;
		}
		return transformer.SSO();
	}

	/**
	 * Proximity is measured on the short path around the world.
	 */
	@Inject(method = "closerThan(Lnet/minecraft/world/entity/Entity;DD)Z", at = @At("HEAD"), cancellable = true)
	public void wrapCloserThan(Entity entity, double horizontalDistance, double verticalDistance, CallbackInfoReturnable<Boolean> cir) {
		DimensionTransformer transformer = planetworld$serverTransformer();
		Entity self = planetworld$self();

		double d = entity.getX() - transformer.Coord.X.unwrap(entity.getX(), self.getX());
		double e = entity.getY() - self.getY();
		double f = entity.getZ() - transformer.Coord.Z.unwrap(entity.getZ(), self.getZ());
		cir.setReturnValue(Mth.lengthSquared(d, f) < Mth.square(horizontalDistance) && Mth.square(e) < Mth.square(verticalDistance));
	}

	/**
	 * Crossing a bound is a change of coordinate frame, not a jump. Wrap the new position and shift
	 * the stored previous positions by the same amount, so everything derived from a per-tick delta
	 * (movement statistics, animation, fall distance) keeps measuring the short path.
	 */
	@WrapMethod(method = "setPosRaw")
	private void planetworld$wrapPosition(double x, double y, double z, Operation<Void> original) {
		DimensionTransformer transformer = planetworld$serverTransformer();
		if (!transformer.isWrapped() || planetworld$wrapsOwnPosition()) {
			original.call(x, y, z);
			return;
		}

		Entity self = planetworld$self();
		double wrappedX = transformer.Coord.X.wrap(x);
		double wrappedZ = transformer.Coord.Z.wrap(z);

		if (wrappedX != x) {
			double shift = wrappedX - x;
			self.xo += shift;
			self.xOld += shift;
		}
		if (wrappedZ != z) {
			double shift = wrappedZ - z;
			self.zo += shift;
			self.zOld += shift;
		}

		original.call(wrappedX, y, wrappedZ);
	}

	/**
	 * Entities that manage their own wrapping (and their riders) must not be wrapped here as well,
	 * or the two owners fight over the position every tick at a bound.
	 */
	@Unique
	private boolean planetworld$wrapsOwnPosition() {
		Entity self = planetworld$self();
		if (self instanceof WrapsOwnPosition) {
			return true;
		}
		return self.getVehicle() instanceof WrapsOwnPosition;
	}

	@Redirect(method = "isColliding", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
	public boolean wrapAABB(VoxelShape shape1, VoxelShape shape2, BooleanOp resultOperator) {
		AABB empty = new AABB(0, 0, 0, 0, 0, 0);
		VoxelShape result = Shapes.create(planetworld$serverTransformer().AABoundingBox.unwrap(shape1.isEmpty() ? empty : shape1.bounds(), shape2.isEmpty() ? empty : shape2.bounds()));
		return Shapes.joinIsNotEmpty(shape1, result, resultOperator);
	}

	/**
	 * Measure to the nearest representative of the queried point rather than answering here, so an
	 * owner of this method keeps its own reading. When both points sit on the same side of every bound
	 * the arguments are unchanged, which is every query away from a seam.
	 */
	@WrapMethod(method = "distanceToSqr(DDD)D")
	private double planetworld$torusDistanceToSqr(double x, double y, double z, Operation<Double> original) {
		DimensionTransformer transformer = planetworld$serverTransformer();
		if (!transformer.isWrapped()) {
			return original.call(x, y, z);
		}
		Entity self = planetworld$self();
		return original.call(
				transformer.Coord.X.unwrap(self.getX(), x),
				y,
				transformer.Coord.Z.unwrap(self.getZ(), z)
		);
	}

	@WrapMethod(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
	private double planetworld$torusDistanceToSqrVec(Vec3 vec, Operation<Double> original) {
		DimensionTransformer transformer = planetworld$serverTransformer();
		if (!transformer.isWrapped()) {
			return original.call(vec);
		}
		return original.call(transformer.Vector3D.unwrap(planetworld$self().position(), vec));
	}

	/**
	 * Vanilla measures this inline instead of delegating, so route it through {@code distanceToSqr}
	 * where the unwrap above already applies.
	 */
	@WrapMethod(method = "distanceTo")
	private float planetworld$torusDistanceTo(Entity entity, Operation<Float> original) {
		DimensionTransformer transformer = planetworld$serverTransformer();
		if (!transformer.isWrapped()) {
			return original.call(entity);
		}
		return (float) Math.sqrt(planetworld$self().distanceToSqr(entity.position()));
	}

	@Redirect(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value="INVOKE", target ="Lnet/minecraft/world/entity/Entity;getX()D", ordinal = 0))
	public double modifyGetX(Entity entity) {
		return planetworld$serverTransformer().Coord.X.unwrap(planetworld$self().getX(), entity.getX());
	}

	@Redirect(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value="INVOKE", target ="Lnet/minecraft/world/entity/Entity;getZ()D", ordinal = 0))
	public double modifyGetZ(Entity entity) {
		return planetworld$serverTransformer().Coord.Z.unwrap(planetworld$self().getZ(), entity.getZ());
	}
}
