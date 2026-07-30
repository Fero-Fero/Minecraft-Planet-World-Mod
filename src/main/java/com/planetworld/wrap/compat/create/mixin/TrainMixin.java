package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Train-level geometry that spans more than one carriage: coupling spacing and collision rays.
 * Both are plain vector math in Create, so a bound between two carriages reads as a world-width
 * gap (an enormous coupling correction) and a bound inside one carriage reverses its collision ray.
 */
@Mixin(Train.class)
public abstract class TrainMixin {

	@Shadow public double speed;
	@Shadow public TrackGraph graph;
	@Shadow public List<Carriage> carriages;

	/**
	 * Spacing between the anchors of two coupled carriages, which becomes the per-carriage stress
	 * that Create adds to travel distance.
	 */
	@WrapOperation(
			method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double planetworld$torusCouplingSpacing(Vec3 from, Vec3 to, Operation<Double> original) {
		DimensionTransformer t = planetworld$transformer();
		return t.isWrapped() ? CreateWrapMath.unwrapDistanceSqr(t, from, to) : original.call(from, to);
	}

	/**
	 * The collision ray runs from this carriage's leading point to its trailing point. Rebasing the
	 * far end onto the near one keeps the ray pointing along the carriage instead of the long way
	 * around the world, which otherwise reverses its direction and can strike unrelated trains.
	 */
	@ModifyExpressionValue(
			method = "collideWithOtherTrains",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/trains/entity/TravellingPoint;getPosition(Lcom/simibubi/create/content/trains/graph/TrackGraph;)Lnet/minecraft/world/phys/Vec3;",
					ordinal = 1
			)
	)
	private Vec3 planetworld$rebaseCollisionRay(Vec3 end, Level level, Carriage carriage) {
		DimensionTransformer t = planetworld$transformer();
		if (!t.isWrapped()) {
			return end;
		}
		TravellingPoint from = this.speed < 0.0 ? carriage.getTrailingPoint() : carriage.getLeadingPoint();
		return CreateWrapMath.unwrapRelative(t, from.getPosition(this.graph), end);
	}

	/**
	 * Other trains are compared against the ray above, so their points need the same frame. This also
	 * restores the distance cull Create uses to skip far-away trains, which a bound would defeat.
	 */
	@ModifyExpressionValue(
			method = "findCollidingTrain",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/trains/entity/TravellingPoint;getPosition(Lcom/simibubi/create/content/trains/graph/TrackGraph;)Lnet/minecraft/world/phys/Vec3;"
			)
	)
	private Vec3 planetworld$rebaseOtherTrainPoint(Vec3 point, Level level, Vec3 start, Vec3 end, ResourceKey<Level> dimension) {
		DimensionTransformer t = CreateWrapMath.transformer(dimension);
		return t.isWrapped() ? CreateWrapMath.unwrapRelative(t, start, point) : point;
	}

	/** The impact point comes out of that rebased frame, so bring it back into the world. */
	@WrapMethod(method = "findCollidingTrain")
	private Pair<Train, Vec3> planetworld$wrapImpactPoint(
			Level level,
			Vec3 start,
			Vec3 end,
			ResourceKey<Level> dimension,
			Operation<Pair<Train, Vec3>> original
	) {
		Pair<Train, Vec3> collision = original.call(level, start, end, dimension);
		if (collision == null) {
			return null;
		}
		DimensionTransformer t = CreateWrapMath.transformer(dimension);
		if (!t.isWrapped()) {
			return collision;
		}
		return Pair.of(collision.getFirst(), t.Vector3D.wrap(collision.getSecond()));
	}

	private DimensionTransformer planetworld$transformer() {
		if (this.carriages != null) {
			for (Carriage carriage : this.carriages) {
				TravellingPoint leading = carriage.getLeadingPoint();
				if (leading != null && leading.node1 != null) {
					return CreateWrapMath.transformer(leading.node1.getLocation().dimension);
				}
			}
		}
		return CreateWrapMath.bestEffortTransformer();
	}
}
