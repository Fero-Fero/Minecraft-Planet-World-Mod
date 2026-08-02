package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Curved track length and handle sizing sample points with Euclidean {@code distanceTo}. A curve
 * that straddles a bound otherwise reports nearly a full world width, which breaks turn-edge travel,
 * intersection bounds, and schedule path costs. Measure each segment on the short path.
 * <p>
 * {@code BezierConnection.Runtime} is a private nested class, so it is named as a target string
 * rather than a class literal.
 */
@Mixin(targets = "com.simibubi.create.content.trains.track.BezierConnection$Runtime")
public abstract class BezierConnectionRuntimeMixin {

	@WrapOperation(
			method = "computeLength",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private static double planetworld$torusComputeLength(Vec3 from, Vec3 to, Operation<Double> original) {
		return planetworld$unwrapDistance(from, to, original);
	}

	@WrapOperation(
			method = {"<init>", "determineHandles"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double planetworld$torusRuntimeDistance(Vec3 from, Vec3 to, Operation<Double> original) {
		return planetworld$unwrapDistance(from, to, original);
	}

	private static double planetworld$unwrapDistance(Vec3 from, Vec3 to, Operation<Double> original) {
		DimensionTransformer t = CreateWrapMath.bestEffortTransformer();
		return t.isWrapped() ? CreateWrapMath.unwrapDistance(t, from, to) : original.call(from, to);
	}
}
