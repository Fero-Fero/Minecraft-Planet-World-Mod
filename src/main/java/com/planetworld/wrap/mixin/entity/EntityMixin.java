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

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Shadow private Level level;

	Entity thiz = (Entity) (Object) this;

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

		double d = entity.getX() - transformer.Coord.X.unwrap(entity.getX(), thiz.getX());
		double e = entity.getY() - thiz.getY();
		double f = entity.getZ() - transformer.Coord.Z.unwrap(entity.getZ(), thiz.getZ());
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

		double wrappedX = transformer.Coord.X.wrap(x);
		double wrappedZ = transformer.Coord.Z.wrap(z);

		if (wrappedX != x) {
			double shift = wrappedX - x;
			thiz.xo += shift;
			thiz.xOld += shift;
		}
		if (wrappedZ != z) {
			double shift = wrappedZ - z;
			thiz.zo += shift;
			thiz.zOld += shift;
		}

		original.call(wrappedX, y, wrappedZ);
	}

	/**
	 * Entities that manage their own wrapping (and their riders) must not be wrapped here as well,
	 * or the two owners fight over the position every tick at a bound.
	 */
	@Unique
	private boolean planetworld$wrapsOwnPosition() {
		if (thiz instanceof WrapsOwnPosition) {
			return true;
		}
		return thiz.getVehicle() instanceof WrapsOwnPosition;
	}

	@Redirect(method = "isColliding", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
	public boolean wrapAABB(VoxelShape shape1, VoxelShape shape2, BooleanOp resultOperator) {
		AABB empty = new AABB(0, 0, 0, 0, 0, 0);
		VoxelShape result = Shapes.create(planetworld$serverTransformer().AABoundingBox.unwrap(shape1.isEmpty() ? empty : shape1.bounds(), shape2.isEmpty() ? empty : shape2.bounds()));
		return Shapes.joinIsNotEmpty(shape1, result, resultOperator);
	}

	@Inject(method = "distanceTo", at = @At("HEAD"), cancellable = true)
	public void wrapDistanceSquared1(Entity entity, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Mth.sqrt((float)planetworld$serverTransformer().Coord.sqrDistToBounds(entity.getX(), entity.getY(), entity.getZ(), thiz.getX(), thiz.getY(), thiz.getZ())));
	}

	@Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
	public void wrapDistanceSquared2(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(planetworld$serverTransformer().Coord.sqrDistToBounds(x, y, z, thiz.getX(), thiz.getY(), thiz.getZ()));
	}

	@Inject(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", at = @At("HEAD"), cancellable = true)
	public void wrapDistanceSquared3(Vec3 vec, CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(planetworld$serverTransformer().Vector3D.sqrDistToBounds(vec, new Vec3(thiz.getX(), thiz.getY(), thiz.getZ())));
	}

	@Redirect(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value="INVOKE", target ="Lnet/minecraft/world/entity/Entity;getX()D", ordinal = 0))
	public double modifyGetX(Entity entity) {
		return planetworld$serverTransformer().Coord.X.unwrap(thiz.getX(), entity.getX());
	}

	@Redirect(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value="INVOKE", target ="Lnet/minecraft/world/entity/Entity;getZ()D", ordinal = 0))
	public double modifyGetZ(Entity entity) {
		return planetworld$serverTransformer().Coord.Z.unwrap(thiz.getZ(), entity.getZ());
	}
}
