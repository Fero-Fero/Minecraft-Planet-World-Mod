package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateTrackGraphSeam;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * When Create would mark a carriage blocked at the torus seam, stitch missing graph edges
 * (including 16-block seam segments) and hop onto the wrap-adjacent track.
 */
@Mixin(TravellingPoint.class)
public abstract class TravellingPointMixin {

	@Shadow public TrackNode node1;
	@Shadow public TrackNode node2;
	@Shadow public TrackEdge edge;
	@Shadow public double position;
	@Shadow public boolean blocked;

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

	@WrapMethod(
			method = "travel(Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint$ITrackSelector;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IEdgePointListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$ITurnListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IPortalListener;)D"
	)
	private double planetworld$rescueBlockedTravel(
			TrackGraph graph,
			double distance,
			TravellingPoint.ITrackSelector trackSelector,
			TravellingPoint.IEdgePointListener signalListener,
			TravellingPoint.ITurnListener turnListener,
			TravellingPoint.IPortalListener portalListener,
			Operation<Double> original
	) {
		double traveled = original.call(graph, distance, trackSelector, signalListener, turnListener, portalListener);
		if (!this.blocked || graph == null || this.edge == null || Math.abs(distance) < 1.0e-6) {
			return traveled;
		}
		if (!CreateWrapMath.wrappingActive()) {
			return traveled;
		}

		CreateTrackGraphSeam.stitchSeamEdges(null, graph);

		boolean forward = distance > 0.0;
		TrackNode at = forward ? this.node2 : this.node1;
		TrackNode back = forward ? this.node1 : this.node2;
		if (at == null) {
			return traveled;
		}

		Vec3 leaveDir = forward ? this.edge.getDirection(false) : this.edge.getDirection(true).scale(-1.0);
		TrackEdge chosenEdge = null;
		TrackNode chosenNode = pickExistingContinuation(graph, at, back, forward);

		if (chosenNode == null) {
			TrackNode seamNeighbor = CreateTrackGraphSeam.findForwardSeamNeighbor(graph, at, back, leaveDir);
			if (seamNeighbor != null) {
				CreateTrackGraphSeam.ensureConnected(null, graph, at, seamNeighbor);
				chosenNode = seamNeighbor;
			}
		}

		if (chosenNode != null) {
			Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(forward ? at : chosenNode);
			if (connections != null) {
				chosenEdge = forward ? connections.get(chosenNode) : connections.get(at);
			}
		}

		if (chosenEdge == null || chosenNode == null) {
			return traveled;
		}

		double remaining = distance - traveled;
		if (forward) {
			this.node1 = at;
			this.node2 = chosenNode;
			this.edge = chosenEdge;
			this.position = 0.0;
		} else {
			this.node2 = at;
			this.node1 = chosenNode;
			this.edge = chosenEdge;
			this.position = this.edge.getLength();
		}
		this.blocked = false;

		if (Math.abs(remaining) < 1.0e-4) {
			return traveled;
		}
		double more = original.call(graph, remaining, trackSelector, signalListener, turnListener, portalListener);
		return traveled + more;
	}

	private TrackNode pickExistingContinuation(TrackGraph graph, TrackNode at, TrackNode back, boolean forward) {
		Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(at);
		if (connections == null || connections.isEmpty()) {
			return null;
		}
		for (Map.Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
			TrackNode next = entry.getKey();
			TrackEdge nextEdge = entry.getValue();
			if (next == back || nextEdge == null) {
				continue;
			}
			boolean ok = forward
					? (this.edge.canTravelTo(nextEdge) || CreateTrackGraphSeam.canTravelAcrossSeam(this.edge, nextEdge))
					: (nextEdge.canTravelTo(this.edge) || CreateTrackGraphSeam.canTravelAcrossSeam(nextEdge, this.edge));
			if (ok) {
				return next;
			}
		}
		return null;
	}
}
