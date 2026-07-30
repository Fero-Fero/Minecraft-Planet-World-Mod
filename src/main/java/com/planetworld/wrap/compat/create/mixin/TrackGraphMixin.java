package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Graph-wide "nearest node to point" culls use Euclidean {@code distanceToSqr}. Across a bound the
 * nearest seam neighbour looks farthest; unwrap so lookups and dump tooling stay local.
 */
@Mixin(TrackGraph.class)
public abstract class TrackGraphMixin {

	@WrapOperation(
			method = "distanceToLocationSqr",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double planetworld$torusNodeCull(Vec3 from, Vec3 to, Operation<Double> original, Level level, Vec3 location) {
		DimensionTransformer t = CreateWrapMath.transformer(level);
		return t.isWrapped() ? CreateWrapMath.unwrapDistanceSqr(t, from, to) : original.call(from, to);
	}
}
