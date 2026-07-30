package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateTrackGraphSeam;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Seam travel continuity: Create's direction-dot {@code canTravelTo} rejects opposite-edge
 * neighbours even when the graph already has a short seam edge. Relax that check with torus
 * geometry. Missing edges must come from placement/propagate/heal — not a post-{@code blocked}
 * rescue hop.
 */
@Mixin(TravellingPoint.class)
public abstract class TravellingPointMixin {

	@WrapOperation(
			method = "travel(Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint$ITrackSelector;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IEdgePointListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$ITurnListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IPortalListener;)D",
			at = @At(
					value = "INVOKE",
					target = "Lcom/simibubi/create/content/trains/graph/TrackEdge;canTravelTo(Lcom/simibubi/create/content/trains/graph/TrackEdge;)Z"
			)
	)
	private boolean planetworld$wrapCanTravelTo(TrackEdge self, TrackEdge other, Operation<Boolean> original) {
		if (original.call(self, other)) {
			return true;
		}
		return CreateTrackGraphSeam.canTravelAcrossSeam(self, other);
	}
}
