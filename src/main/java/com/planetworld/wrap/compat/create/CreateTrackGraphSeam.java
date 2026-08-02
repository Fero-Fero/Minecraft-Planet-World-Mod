package com.planetworld.wrap.compat.create;

import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.storage.TransformerRequests;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Create-typed seam helpers kept out of {@link CreateWrapMath} so wrap-math self-checks
 * can run without Create on the runtime classpath.
 */
public final class CreateTrackGraphSeam {
	/** Create places graph nodes about every 16 blocks on straight track. */
	private static final double MAX_SEGMENT_UNWRAP = 16.5;
	/** Euclidean must beat unwrap by this much to count as a torus seam jump. */
	private static final double SEAM_JUMP_SLACK = 8.0;

	private CreateTrackGraphSeam() {
	}

	public static boolean locationsMatch(TrackNodeLocation a, TrackNodeLocation b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		if (!Objects.equals(a.dimension, b.dimension)) {
			return false;
		}
		if (CreateWrapMath.samePackedLocation(
				a.getX(), a.getY(), a.getZ(), a.yOffsetPixels,
				b.getX(), b.getY(), b.getZ(), b.yOffsetPixels
		)) {
			return true;
		}
		DimensionTransformer t = CreateWrapMath.transformer(a.dimension);
		if (!t.isWrapped()) {
			return false;
		}
		return CreateWrapMath.unwrapDistance(t, a.getLocation(), b.getLocation()) < 1.0e-3;
	}

	public static boolean isSeamLocation(TrackNodeLocation location) {
		return CreateWrapMath.isSeamVec(location.getLocation(), location.dimension);
	}

	private static boolean isSeamJump(double euclidean, double wrapped) {
		return wrapped >= 0.5
				&& wrapped <= MAX_SEGMENT_UNWRAP
				&& euclidean > wrapped + SEAM_JUMP_SLACK;
	}

	/**
	 * Connects graph nodes separated by the torus seam (short unwrap, long Euclidean).
	 * Covers both near-coincident duplicates and normal 16-block Create segments that
	 * straddle the cut.
	 * <p>
	 * Only nodes within one segment of a bound can take part, so the candidate set stays tiny even
	 * on a large rail network and the pairwise comparison never scales with total track.
	 *
	 * @return number of new edges created
	 */
	public static int stitchSeamEdges(@Nullable LevelAccessor reader, TrackGraph graph) {
		if (graph == null) {
			return 0;
		}
		DimensionTransformer t = resolveTransformer(reader);
		if (!t.isWrapped()) {
			return 0;
		}

		List<TrackNode> candidates = collectSeamNodes(graph, t);
		if (candidates.size() < 2) {
			return 0;
		}
		reader = resolveReader(reader, graph);
		if (reader == null) {
			return 0;
		}

		int connected = 0;
		for (int i = 0; i < candidates.size(); i++) {
			TrackNode a = candidates.get(i);
			Vec3 pa = a.getLocation().getLocation();
			for (int j = i + 1; j < candidates.size(); j++) {
				TrackNode b = candidates.get(j);
				if (!Objects.equals(a.getLocation().dimension, b.getLocation().dimension)) {
					continue;
				}
				Vec3 pb = b.getLocation().getLocation();
				double euclidean = pa.distanceTo(pb);
				double wrapped = CreateWrapMath.unwrapDistance(t, pa, pb);
				if (!isSeamJump(euclidean, wrapped)) {
					continue;
				}
				if (ensureConnected(reader, graph, a, b)) {
					connected++;
				}
			}
		}
		return connected;
	}

	/**
	 * Finds the best forward neighbor across the seam when travel is blocked at {@code at}.
	 */
	@Nullable
	public static TrackNode findForwardSeamNeighbor(
			TrackGraph graph,
			TrackNode at,
			@Nullable TrackNode back,
			@Nullable Vec3 travelDir
	) {
		if (graph == null || at == null) {
			return null;
		}
		DimensionTransformer t = CreateWrapMath.transformer(at.getLocation().dimension);
		if (!t.isWrapped()) {
			return null;
		}
		Vec3 pa = at.getLocation().getLocation();
		Vec3 dir = travelDir == null ? null : travelDir.normalize();
		TrackNode best = null;
		double bestDist = MAX_SEGMENT_UNWRAP + 1.0;
		for (TrackNode candidate : collectSeamNodes(graph, t)) {
			if (candidate == at || candidate == back) {
				continue;
			}
			if (!Objects.equals(at.getLocation().dimension, candidate.getLocation().dimension)) {
				continue;
			}
			Vec3 pb = candidate.getLocation().getLocation();
			double euclidean = pa.distanceTo(pb);
			double wrapped = CreateWrapMath.unwrapDistance(t, pa, pb);
			if (!isSeamJump(euclidean, wrapped) || wrapped >= bestDist) {
				continue;
			}
			if (dir != null) {
				Vec3 unwrapped = CreateWrapMath.unwrapRelative(t, pa, pb);
				Vec3 delta = unwrapped.subtract(pa);
				if (delta.lengthSqr() < 1.0e-8) {
					continue;
				}
				if (delta.normalize().dot(dir) < 0.25) {
					continue;
				}
			}
			bestDist = wrapped;
			best = candidate;
		}
		return best;
	}

	public static boolean ensureConnected(@Nullable LevelAccessor reader, TrackGraph graph, TrackNode a, TrackNode b) {
		if (graph == null || a == null || b == null || a == b) {
			return false;
		}
		if (graph.getConnection(Couple.create(a, b)) != null) {
			return false;
		}
		reader = resolveReader(reader, graph);
		if (reader == null) {
			return false;
		}
		TrackMaterial material = materialBetween(graph, a, b);
		TrackNodeLocation.DiscoveredLocation d1 = discovered(a, material);
		TrackNodeLocation.DiscoveredLocation d2 = discovered(b, material);
		if (graph.locateNode(d1) == null || graph.locateNode(d2) == null) {
			return false;
		}
		graph.connectNodes(reader, d1, d2, null);
		return true;
	}

	public static int stitchAllGraphs(@Nullable LevelAccessor reader) {
		if (!CreateWrapMath.wrappingActive()) {
			return 0;
		}
		MinecraftServer server = TransformerRequests.server;
		if (server == null) {
			return 0;
		}
		int total = 0;
		for (TrackGraph graph : com.simibubi.create.Create.RAILWAYS.trackNetworks.values()) {
			total += stitchSeamEdges(reader, graph);
		}
		return total;
	}

	/**
	 * Collects the nodes close enough to a bound to be part of a seam edge.
	 */
	private static List<TrackNode> collectSeamNodes(TrackGraph graph, DimensionTransformer t) {
		List<TrackNode> nodes = new ArrayList<>();
		for (TrackNodeLocation loc : graph.getNodes()) {
			if (!CreateWrapMath.withinOfSeam(t, loc.getLocation(), MAX_SEGMENT_UNWRAP)) {
				continue;
			}
			TrackNode node = graph.locateNode(loc);
			if (node != null) {
				nodes.add(node);
			}
		}
		return nodes;
	}

	private static DimensionTransformer resolveTransformer(@Nullable LevelAccessor reader) {
		DimensionTransformer t = CreateWrapMath.bestEffortTransformer();
		if (reader instanceof Level level) {
			DimensionTransformer levelT = CreateWrapMath.transformer(level);
			if (levelT.isWrapped()) {
				return levelT;
			}
		}
		Level ctx = CreateWrapContext.level();
		if (ctx != null) {
			DimensionTransformer levelT = CreateWrapMath.transformer(ctx);
			if (levelT.isWrapped()) {
				return levelT;
			}
		}
		return t;
	}

	@Nullable
	private static LevelAccessor resolveReader(@Nullable LevelAccessor reader, TrackGraph graph) {
		if (reader != null) {
			return reader;
		}
		Level ctx = CreateWrapContext.level();
		if (ctx != null) {
			return ctx;
		}
		MinecraftServer server = TransformerRequests.server;
		if (server == null) {
			return null;
		}
		for (TrackNodeLocation loc : graph.getNodes()) {
			if (loc.dimension != null) {
				ServerLevel dimLevel = server.getLevel(loc.dimension);
				if (dimLevel != null) {
					return dimLevel;
				}
			}
		}
		return null;
	}

	private static TrackMaterial materialBetween(TrackGraph graph, TrackNode a, TrackNode b) {
		Map<TrackNode, TrackEdge> fromA = graph.getConnectionsFrom(a);
		if (fromA != null) {
			for (TrackEdge edge : fromA.values()) {
				if (edge != null && edge.getTrackMaterial() != null) {
					return edge.getTrackMaterial();
				}
			}
		}
		Map<TrackNode, TrackEdge> fromB = graph.getConnectionsFrom(b);
		if (fromB != null) {
			for (TrackEdge edge : fromB.values()) {
				if (edge != null && edge.getTrackMaterial() != null) {
					return edge.getTrackMaterial();
				}
			}
		}
		return TrackMaterial.ANDESITE;
	}

	private static TrackNodeLocation.DiscoveredLocation discovered(TrackNode node, TrackMaterial material) {
		TrackNodeLocation loc = node.getLocation();
		return new TrackNodeLocation.DiscoveredLocation(loc.dimension, loc.getLocation())
				.withYOffset(loc.yOffsetPixels)
				.withNormal(node.getNormal())
				.materialA(material)
				.materialB(material)
				.forceNode();
	}

	/**
	 * Relaxed travel check used when vanilla direction dots fail across the seam.
	 */
	public static boolean canTravelAcrossSeam(TrackEdge from, TrackEdge to) {
		if (from == null || to == null) {
			return false;
		}
		DimensionTransformer t = CreateWrapMath.transformer(from.node1.getLocation().dimension);
		if (!t.isWrapped()) {
			return false;
		}
		Vec3 leaving = from.getDirection(false);
		Vec3 entering = to.getDirection(true);
		if (leaving != null && entering != null && leaving.dot(entering) > 0.5) {
			return true;
		}
		Vec3 a = from.node2.getLocation().getLocation();
		Vec3 b = to.node1.getLocation().getLocation();
		double wrapped = CreateWrapMath.unwrapDistance(t, a, b);
		double euclidean = a.distanceTo(b);
		return isSeamJump(euclidean, wrapped) || wrapped < 1.05 && euclidean > 2.0;
	}
}
