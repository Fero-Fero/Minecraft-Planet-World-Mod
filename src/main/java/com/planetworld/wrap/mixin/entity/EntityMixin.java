/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.entity;

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
import org.spongepowered.asm.mixin.Overwrite;
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
	 * @author Famro Fexl
	 * @reason wrapping
	 */
	@Overwrite
	public boolean closerThan(Entity entity, double horizontalDistance, double verticalDistance) {
		DimensionTransformer transformer = planetworld$serverTransformer();

		double d = entity.getX() - transformer.Coord.X.unwrap(entity.getX(), thiz.getX());
		double e = entity.getY() - thiz.getY();
		double f = entity.getZ() - transformer.Coord.Z.unwrap(entity.getZ(), thiz.getZ());
		return Mth.lengthSquared(d, f) < Mth.square(horizontalDistance) && Mth.square(e) < Mth.square(verticalDistance);
	}

	@ModifyVariable(method = "setPosRaw", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	public double wrapX(double x) {
		return planetworld$serverTransformer().Coord.X.wrap(x);
	}

	@ModifyVariable(method = "setPosRaw", at = @At("HEAD"), ordinal = 2, argsOnly = true)
	public double wrapZ(double z) {
		return planetworld$serverTransformer().Coord.Z.wrap(z);
	}

	/**
	 * @author Famro Fexl
	 * @reason wrapping
	 */
	@Overwrite
	public void absMoveTo(double x, double y, double z) {
		DimensionTransformer transformer = planetworld$serverTransformer();

		double d = Mth.clamp(x, -3.0E7, 3.0E7);
		double e = Mth.clamp(z, -3.0E7, 3.0E7);
		thiz.xo = transformer.Coord.X.wrap(d);
		thiz.yo = y;
		thiz.zo = transformer.Coord.Z.wrap(e);
		thiz.setPos(d, y, e);
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
