package com.planetworld.wrap.compat.create.mixin;

import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wrap-aware edge length and interpolation so trains cross the seam on the short path
 * instead of lerping through world center (spawn) and thrashing chunks.
 */
@Mixin(TrackEdge.class)
public abstract class TrackEdgeMixin {

	@Shadow public TrackNode node1;
	@Shadow public TrackNode node2;

	@Shadow public abstract boolean isInterDimensional();

	@Shadow public abstract boolean isTurn();

	@Inject(method = "getLength", at = @At("HEAD"), cancellable = true)
	private void planetworld$wrapLength(CallbackInfoReturnable<Double> cir) {
		if (this.isInterDimensional() || this.isTurn()) {
			return;
		}
		DimensionTransformer t = CreateWrapMath.transformer(this.node1.getLocation().dimension);
		if (!t.isWrapped()) {
			return;
		}
		Vec3 a = this.node1.getLocation().getLocation();
		Vec3 b = this.node2.getLocation().getLocation();
		cir.setReturnValue(CreateWrapMath.unwrapDistance(t, a, b));
	}

	/**
	 * Replace straight-edge sampling entirely. A WrapOperation on {@code VecHelper.lerp} is easy
	 * to miss; HEAD cancel guarantees seam edges never Euclidean-lerp through spawn.
	 */
	@Inject(method = "getPosition", at = @At("HEAD"), cancellable = true)
	private void planetworld$wrapGetPosition(TrackGraph graph, double t, CallbackInfoReturnable<Vec3> cir) {
		if (this.isTurn() || this.isInterDimensional()) {
			return;
		}
		DimensionTransformer transformer = CreateWrapMath.transformer(this.node1.getLocation().dimension);
		if (!transformer.isWrapped()) {
			return;
		}
		Vec3 from = this.node1.getLocation().getLocation();
		Vec3 to = this.node2.getLocation().getLocation();
		cir.setReturnValue(CreateWrapMath.lerpWrapped(transformer, (float) Mth.clamp(t, 0.0, 1.0), from, to));
	}

	@Inject(method = "getDirection", at = @At("HEAD"), cancellable = true)
	private void planetworld$wrapGetDirection(boolean fromFirst, CallbackInfoReturnable<Vec3> cir) {
		DimensionTransformer transformer = CreateWrapMath.transformer(this.node1.getLocation().dimension);
		if (!transformer.isWrapped() || this.isTurn() || this.isInterDimensional()) {
			return;
		}
		TrackEdge self = (TrackEdge) (Object) this;
		Vec3 a = self.getPosition(null, fromFirst ? 0.0 : 0.75);
		Vec3 b = self.getPosition(null, fromFirst ? 0.25 : 1.0);
		Vec3 bu = CreateWrapMath.unwrapRelative(transformer, a, b);
		cir.setReturnValue(bu.subtract(a).normalize());
	}

	@Inject(method = "getDirectionAt", at = @At("HEAD"), cancellable = true)
	private void planetworld$wrapGetDirectionAt(double position, CallbackInfoReturnable<Vec3> cir) {
		DimensionTransformer transformer = CreateWrapMath.transformer(this.node1.getLocation().dimension);
		if (!transformer.isWrapped() || this.isTurn() || this.isInterDimensional()) {
			return;
		}
		TrackEdge self = (TrackEdge) (Object) this;
		double length = self.getLength();
		if (length < 1.0e-6) {
			return;
		}
		double t = position / length;
		double step = 0.5 / length;
		Vec3 behind = self.getPosition(null, Math.max(0.0, t - step));
		Vec3 ahead = self.getPosition(null, Math.min(1.0, t + step));
		Vec3 aheadU = CreateWrapMath.unwrapRelative(transformer, behind, ahead);
		cir.setReturnValue(aheadU.subtract(behind).normalize());
	}
}
