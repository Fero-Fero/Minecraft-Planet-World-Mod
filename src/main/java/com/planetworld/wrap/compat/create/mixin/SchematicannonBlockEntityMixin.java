package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Schematicannon range checks use Euclidean {@code closerThan}. Across a bound the short chord
 * looks too far, so printing / shouldPlace stop at the cut.
 */
@Mixin(SchematicannonBlockEntity.class)
public abstract class SchematicannonBlockEntityMixin {

	@WrapOperation(
			method = {"initializePrinter", "shouldPlace"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
			)
	)
	private boolean planetworld$torusCloserThan(BlockPos self, Vec3i other, double distance, Operation<Boolean> original) {
		Level level = ((BlockEntity) (Object) this).getLevel();
		DimensionTransformer t = CreateWrapMath.transformer(level);
		if (!t.isWrapped()) {
			return original.call(self, other, distance);
		}
		return CreateWrapMath.unwrapDistSqr(level, self, other) < distance * distance;
	}
}
