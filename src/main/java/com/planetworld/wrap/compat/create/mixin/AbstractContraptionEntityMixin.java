package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create compares where a rider boarded with where they dismounted to decide whether they made a
 * long journey. On a wrapped world any ride over a bound reads as most of a world width, so a
 * twenty block trip awards the long-travel advancement. Measure that trip on the torus instead.
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {

	@WrapOperation(
			method = "getDismountLocationForPassenger",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z")
	)
	private boolean planetworld$torusRideLength(Vec3 mounted, Position dismounted, double distance, Operation<Boolean> original) {
		DimensionTransformer t = CreateWrapMath.transformer(((Entity) (Object) this).level());
		if (!t.isWrapped()) {
			return original.call(mounted, dismounted, distance);
		}
		Vec3 to = new Vec3(dismounted.x(), dismounted.y(), dismounted.z());
		return CreateWrapMath.unwrapDistance(t, mounted, to) < distance;
	}
}
