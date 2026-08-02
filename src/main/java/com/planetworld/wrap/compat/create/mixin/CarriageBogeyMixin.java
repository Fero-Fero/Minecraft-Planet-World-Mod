package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A bogey spans two travelling points a wheelbase apart, and Create combines them with plain
 * vector math. While a bogey straddles a bound the two points sit on opposite sides of the world,
 * so those combinations produce a midpoint at the world center and a wheelbase of a full world
 * width. The midpoint teleports the carriage entity out of its loaded chunks; the bogus wheelbase
 * becomes a stress correction of half a world that is fed straight back into travel distance,
 * which throws the train down the track until it reports the end of its rails.
 * <p>
 * Every combination here is redone on the short torus path instead.
 */
@Mixin(CarriageBogey.class)
public abstract class CarriageBogeyMixin {

	@Shadow public Carriage carriage;

	@Shadow public abstract TravellingPoint leading();

	@Shadow public abstract TravellingPoint trailing();

	@Shadow public abstract ResourceKey<Level> getDimension();

	@WrapMethod(method = "getAnchorPosition(Z)Lnet/minecraft/world/phys/Vec3;")
	private Vec3 planetworld$torusAnchorPosition(boolean flipUpsideDown, Operation<Vec3> original) {
		DimensionTransformer t = planetworld$transformer();
		if (t == null) {
			return original.call(flipUpsideDown);
		}
		TrackGraph graph = this.carriage.train.graph;
		Vec3 first = this.leading().getPosition(graph, flipUpsideDown);
		Vec3 second = this.trailing().getPosition(graph, flipUpsideDown);
		return CreateWrapMath.midpoint(t, first, second);
	}

	@WrapOperation(
			method = "getStress",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double planetworld$torusWheelbase(Vec3 from, Vec3 to, Operation<Double> original) {
		DimensionTransformer t = planetworld$transformer();
		return t == null ? original.call(from, to) : CreateWrapMath.unwrapDistance(t, from, to);
	}

	/**
	 * {@code updateAngles} subtracts the trailing point from the leading one; rebasing the trailing
	 * position onto the leading one keeps yaw and pitch pointing along the track over a bound.
	 */
	@ModifyExpressionValue(
			method = "updateAngles",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/trains/entity/TravellingPoint;getPosition(Lcom/simibubi/create/content/trains/graph/TrackGraph;)Lnet/minecraft/world/phys/Vec3;",
					ordinal = 1
			)
	)
	private Vec3 planetworld$rebaseCoupledPosition(Vec3 coupledVec) {
		DimensionTransformer t = planetworld$transformer();
		if (t == null) {
			return coupledVec;
		}
		Vec3 positionVec = this.leading().getPosition(this.carriage.train.graph);
		return CreateWrapMath.unwrapRelative(t, positionVec, coupledVec);
	}

	/**
	 * @return the wrapped dimension both points travel in, or null when there is nothing to correct
	 *         (unwrapped world, missing edge, or a bogey mid-portal between two dimensions).
	 */
	@Nullable
	private DimensionTransformer planetworld$transformer() {
		if (this.carriage == null || this.carriage.train == null) {
			return null;
		}
		ResourceKey<Level> dimension = this.getDimension();
		if (dimension == null) {
			return null;
		}
		DimensionTransformer t = CreateWrapMath.transformer(dimension);
		return t.isWrapped() ? t : null;
	}
}
