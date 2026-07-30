package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep bogey/position anchors in wrapped space so alignEntity never sees Euclidean
 * endpoints on opposite sides of the cut, and measure the span between them the short way.
 */
@Mixin(Carriage.class)
public abstract class CarriageAnchorMixin {

	/**
	 * The leading-to-trailing anchor span drives the whole-carriage stress correction, which Create
	 * adds to the distance its travelling points are asked to move. Measured Euclidean, a carriage
	 * straddling a bound reports a world-width span and the correction launches the train.
	 */
	@WrapOperation(
			method = "getAnchorDiff",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double planetworld$torusAnchorSpan(Vec3 from, Vec3 to, Operation<Double> original) {
		Carriage self = (Carriage) (Object) this;
		TravellingPoint leading = self.getLeadingPoint();
		ResourceKey<Level> dimension = leading.node1 == null ? null : leading.node1.getLocation().dimension;
		DimensionTransformer t = CreateWrapMath.transformer(dimension);
		return t.isWrapped() ? CreateWrapMath.unwrapDistance(t, from, to) : original.call(from, to);
	}

	@Inject(method = "updateContraptionAnchors", at = @At("RETURN"))
	private void planetworld$wrapAnchors(CallbackInfo ci) {
		Carriage self = (Carriage) (Object) this;
		DimensionTransformer t = CreateWrapMath.bestEffortTransformer();
		if (!t.isWrapped()) {
			return;
		}
		self.forEachPresentEntity(cce -> {
			Carriage.DimensionalCarriageEntity dce = self.getDimensional(cce.level());
			if (dce.positionAnchor != null) {
				dce.positionAnchor = t.Vector3D.wrap(dce.positionAnchor);
			}
			if (dce.rotationAnchors != null) {
				Vec3 first = dce.rotationAnchors.getFirst();
				Vec3 second = dce.rotationAnchors.getSecond();
				if (first != null) {
					dce.rotationAnchors.setFirst(t.Vector3D.wrap(first));
				}
				if (second != null) {
					dce.rotationAnchors.setSecond(t.Vector3D.wrap(second));
				}
			}
		});
	}
}
