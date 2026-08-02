package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Signal / station / observer attachment resolves a graph location by measuring node spacing and
 * direction matches with plain {@code Vec3} math. Across a bound those comparisons fail or attach
 * at the wrong edge parameter; rebase onto the short path.
 */
@Mixin(TrackGraphHelper.class)
public abstract class TrackGraphHelperMixin {

	@WrapOperation(
			method = "getGraphLocationAt",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private static double planetworld$torusNodeSpacing(Vec3 from, Vec3 to, Operation<Double> original, Level level) {
		DimensionTransformer t = CreateWrapMath.transformer(level);
		return t.isWrapped() ? CreateWrapMath.unwrapDistance(t, from, to) : original.call(from, to);
	}

	@WrapOperation(
			method = "getGraphLocationAt",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private static double planetworld$torusDirectionMatch(Vec3 from, Vec3 to, Operation<Double> original, Level level) {
		DimensionTransformer t = CreateWrapMath.transformer(level);
		return t.isWrapped() ? CreateWrapMath.unwrapDistanceSqr(t, from, to) : original.call(from, to);
	}

	@WrapOperation(
			method = "getGraphLocationAt",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
			)
	)
	private static Vec3 planetworld$torusSubtract(Vec3 self, Vec3 other, Operation<Vec3> original, Level level) {
		DimensionTransformer t = CreateWrapMath.transformer(level);
		if (!t.isWrapped()) {
			return original.call(self, other);
		}
		return self.subtract(CreateWrapMath.unwrapRelative(t, self, other));
	}
}
