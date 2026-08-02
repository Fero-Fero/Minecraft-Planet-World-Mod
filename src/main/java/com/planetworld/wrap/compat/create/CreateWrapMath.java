package com.planetworld.wrap.compat.create;

import com.planetworld.wrap.client.storage.TransformersStorage;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrap-aware helpers for Create track placement, graph discovery, and edge geometry.
 * <p>
 * Critical invariant: Create {@code TrackNodeLocation}s must live in wrapped coordinate space
 * so opposite-edge track ends collapse to the same graph node (e.g. z=256 ≡ z=-256).
 * <p>
 * Keep this class free of Create types so {@code verifyCreateWrapMath} can run without Create on the classpath.
 */
public final class CreateWrapMath {
	private static final ThreadLocal<DimensionTransformer> OVERRIDE = new ThreadLocal<>();

	private CreateWrapMath() {
	}

	/** Test / forced context when no Level is available yet. */
	public static void pushOverride(DimensionTransformer transformer) {
		OVERRIDE.set(transformer);
	}

	public static void popOverride() {
		OVERRIDE.remove();
	}

	public static DimensionTransformer transformer(@Nullable BlockGetter world) {
		DimensionTransformer override = OVERRIDE.get();
		if (override != null) {
			return override;
		}
		if (world instanceof Level level) {
			DimensionTransformer t = level.getTransformer();
			return t != null ? t : DimensionTransformer.DISABLED;
		}
		return DimensionTransformer.DISABLED;
	}

	public static DimensionTransformer transformer(@Nullable ResourceKey<Level> dimension) {
		DimensionTransformer override = OVERRIDE.get();
		if (override != null) {
			return override;
		}
		if (dimension == null) {
			return bestEffortTransformer();
		}
		// End is never a PlanetWorld torus — never fall through to Overworld/Nether wrap.
		if (Level.END.equals(dimension)) {
			return transformerForLoadedDimension(dimension);
		}
		MinecraftServer server = TransformerRequests.server;
		if (server != null) {
			ServerLevel level = server.getLevel(dimension);
			if (level != null) {
				DimensionTransformer t = level.getTransformer();
				return t != null ? t : DimensionTransformer.DISABLED;
			}
		}
		DimensionTransformer client = TransformersStorage.getTransformer(dimension);
		if (client.isWrapped()) {
			return client;
		}
		return bestEffortTransformer();
	}

	private static DimensionTransformer transformerForLoadedDimension(ResourceKey<Level> dimension) {
		MinecraftServer server = TransformerRequests.server;
		if (server != null) {
			ServerLevel level = server.getLevel(dimension);
			if (level != null) {
				DimensionTransformer t = level.getTransformer();
				return t != null ? t : DimensionTransformer.DISABLED;
			}
		}
		return TransformersStorage.getTransformer(dimension);
	}

	/**
	 * Best-effort transformer when a dimension key is not yet available (node construction).
	 * Prefers an explicit {@link CreateWrapContext} level so End track never inherits Overworld wrap.
	 */
	public static DimensionTransformer bestEffortTransformer() {
		DimensionTransformer override = OVERRIDE.get();
		if (override != null) {
			return override;
		}
		Level ctx = CreateWrapContext.level();
		if (ctx != null) {
			if (Level.END.equals(ctx.dimension())) {
				DimensionTransformer end = ctx.getTransformer();
				return end != null ? end : DimensionTransformer.DISABLED;
			}
			DimensionTransformer t = ctx.getTransformer();
			if (t != null && t.isWrapped()) {
				return t;
			}
		}
		MinecraftServer server = TransformerRequests.server;
		if (server != null) {
			for (ServerLevel level : server.getAllLevels()) {
				if (Level.END.equals(level.dimension())) {
					continue;
				}
				DimensionTransformer t = level.getTransformer();
				if (t != null && t.isWrapped()) {
					return t;
				}
			}
		}
		ServerLevel noise = TransformerRequests.noiseLevel;
		if (noise != null && !Level.END.equals(noise.dimension())) {
			DimensionTransformer t = noise.getTransformer();
			if (t != null && t.isWrapped()) {
				return t;
			}
		}
		DimensionTransformer overworld = TransformersStorage.getTransformer(Level.OVERWORLD);
		if (overworld.isWrapped()) {
			return overworld;
		}
		DimensionTransformer nether = TransformersStorage.getTransformer(Level.NETHER);
		if (nether.isWrapped()) {
			return nether;
		}
		return DimensionTransformer.DISABLED;
	}

	public static boolean isWrapped(@Nullable BlockGetter world) {
		return transformer(world).isWrapped();
	}

	public static BlockPos wrapPos(@Nullable BlockGetter world, BlockPos pos) {
		DimensionTransformer t = transformer(world);
		return t.isWrapped() ? t.Block.wrap(pos) : pos;
	}

	public static double wrapX(double x) {
		DimensionTransformer t = bestEffortTransformer();
		return t.isWrapped() ? t.Coord.X.wrap(x) : x;
	}

	public static double wrapZ(double z) {
		DimensionTransformer t = bestEffortTransformer();
		return t.isWrapped() ? t.Coord.Z.wrap(z) : z;
	}

	public static Vec3 wrapVec(Vec3 vec) {
		DimensionTransformer t = bestEffortTransformer();
		return t.isWrapped() ? t.Vector3D.wrap(vec) : vec;
	}

	/**
	 * Create packs node locations as round(world*2). Canonicalize packed ints into wrapped space
	 * so z=256 and z=-256 share identity in the track graph.
	 */
	public static int canonicalPackedX(int packedX) {
		DimensionTransformer t = bestEffortTransformer();
		if (!t.isWrapped()) {
			return packedX;
		}
		double world = packedX / 2.0;
		return (int) Math.round(t.Coord.X.wrap(world) * 2.0);
	}

	public static int canonicalPackedZ(int packedZ) {
		DimensionTransformer t = bestEffortTransformer();
		if (!t.isWrapped()) {
			return packedZ;
		}
		double world = packedZ / 2.0;
		return (int) Math.round(t.Coord.Z.wrap(world) * 2.0);
	}

	/**
	 * True when packed coords are not already in canonical wrapped form.
	 */
	public static boolean needsPackedCanonicalization(int packedX, int packedZ) {
		DimensionTransformer t = bestEffortTransformer();
		if (!t.isWrapped()) {
			return false;
		}
		return canonicalPackedX(packedX) != packedX || canonicalPackedZ(packedZ) != packedZ;
	}

	public static boolean wrappingActive() {
		return bestEffortTransformer().isWrapped();
	}

	public static boolean samePackedLocation(int x1, int y1, int z1, int yo1, int x2, int y2, int z2, int yo2) {
		if (y1 != y2 || yo1 != yo2) {
			return false;
		}
		DimensionTransformer t = bestEffortTransformer();
		if (!t.isWrapped()) {
			return x1 == x2 && z1 == z2;
		}
		return canonicalPackedX(x1) == canonicalPackedX(x2) && canonicalPackedZ(z1) == canonicalPackedZ(z2);
	}

	public static int packedLocationHash(int packedX, int packedY, int packedZ, int yOffsetPixels, int dimensionHash) {
		int x = canonicalPackedX(packedX);
		int z = canonicalPackedZ(packedZ);
		return (((packedY + ((z + yOffsetPixels * 31) * 31 + dimensionHash) * 31) * 31) + x);
	}

	public static boolean nearSeamAxis(com.planetworld.wrap.core.CoordinateTransformers.CoordMethods axis, double coord) {
		return withinOfSeamAxis(axis, coord, 0.25);
	}

	/**
	 * True when {@code coord} is at most {@code margin} blocks from a bound on this axis.
	 * <p>
	 * Stepping the margin off the wrapped coordinate leaves the domain exactly when the bound is
	 * within reach, so this needs no knowledge of where the bounds are and stays correct for axes
	 * that are not wrapped at all.
	 */
	public static boolean withinOfSeamAxis(com.planetworld.wrap.core.CoordinateTransformers.CoordMethods axis, double coord, double margin) {
		if (axis.isOver(coord)) {
			return true;
		}
		double wrapped = axis.wrap(coord);
		return axis.isOver(wrapped - margin) || axis.isOver(wrapped + margin);
	}

	/**
	 * A seam edge can only exist between track nodes that are both within one graph segment of a
	 * bound, which is what makes stitching cheap enough to run on a live network.
	 */
	public static boolean withinOfSeam(DimensionTransformer t, Vec3 pos, double margin) {
		return withinOfSeamAxis(t.Coord.X, pos.x, margin) || withinOfSeamAxis(t.Coord.Z, pos.z, margin);
	}

	public static boolean isSeamVec(Vec3 v, @Nullable ResourceKey<Level> dimension) {
		DimensionTransformer t = transformer(dimension);
		if (!t.isWrapped()) {
			return false;
		}
		return nearSeamAxis(t.Coord.X, v.x) || nearSeamAxis(t.Coord.Z, v.z);
	}

	public static double unwrapDistSqr(@Nullable BlockGetter world, BlockPos a, Vec3i b) {
		DimensionTransformer t = transformer(world);
		if (!t.isWrapped()) {
			return a.distSqr(b);
		}
		BlockPos other = new BlockPos(b.getX(), b.getY(), b.getZ());
		BlockPos unwrapped = t.Block.unwrap(a, other);
		double dx = unwrapped.getX() - a.getX();
		double dy = unwrapped.getY() - a.getY();
		double dz = unwrapped.getZ() - a.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	public static double unwrapDistance(@Nullable BlockGetter world, Vec3 a, Vec3 b) {
		return unwrapDistance(transformer(world), a, b);
	}

	public static double unwrapDistance(DimensionTransformer t, Vec3 a, Vec3 b) {
		if (!t.isWrapped()) {
			return a.distanceTo(b);
		}
		return a.distanceTo(t.Vector3D.unwrap(a, b));
	}

	public static double unwrapDistanceSqr(DimensionTransformer t, Vec3 a, Vec3 b) {
		if (!t.isWrapped()) {
			return a.distanceToSqr(b);
		}
		return a.distanceToSqr(t.Vector3D.unwrap(a, b));
	}

	public static Vec3 unwrapRelative(DimensionTransformer t, Vec3 ref, Vec3 wrapped) {
		return t.isWrapped() ? t.Vector3D.unwrap(ref, wrapped) : wrapped;
	}

	/** Move {@code pos} onto the continuous representative nearest {@code ref}. */
	public static BlockPos unwrapBlock(DimensionTransformer t, BlockPos ref, BlockPos pos) {
		return t.isWrapped() ? t.Block.unwrap(ref, pos) : pos;
	}

	/**
	 * Relative block offset from {@code from} to {@code to} along the short torus path.
	 * Create stores station/signal targets as {@code selected.subtract(placePos)}; Euclidean
	 * subtract across a bound writes a ~world-width offset and breaks assemble / overlay.
	 */
	public static BlockPos shortestBlockOffset(DimensionTransformer t, BlockPos from, BlockPos to) {
		if (!t.isWrapped()) {
			return to.subtract(from);
		}
		return unwrapBlock(t, from, to).subtract(from);
	}

	/**
	 * Step from {@code from} to {@code to} along the shortest torus path.
	 * <p>
	 * Create derives rotations and per-tick motion from raw position subtraction, which reverses
	 * direction and reports a whole world width whenever the two ends sit on opposite sides of a bound.
	 */
	public static Vec3 shortestDelta(DimensionTransformer t, Vec3 from, Vec3 to) {
		return unwrapRelative(t, from, to).subtract(from);
	}

	/**
	 * Point halfway between two wrapped positions, measured the short way around.
	 * <p>
	 * Averaging wrapped coordinates directly puts the result at the center of the world whenever the
	 * inputs straddle a bound, which is what teleports carriages to spawn.
	 */
	public static Vec3 midpoint(DimensionTransformer t, Vec3 a, Vec3 b) {
		return lerpWrapped(t, 0.5f, a, b);
	}

	public static Vec3 lerpWrapped(DimensionTransformer t, float progress, Vec3 from, Vec3 to) {
		if (!t.isWrapped()) {
			return from.lerp(to, progress);
		}
		Vec3 unwrappedTo = t.Vector3D.unwrap(from, to);
		return t.Vector3D.wrap(from.lerp(unwrappedTo, progress));
	}

	/**
	 * Expands Create's local adjacency set with one-step torus mirrors so opposite-edge tracks are found.
	 */
	public static Collection<BlockPos> withWrappedAdjacents(@Nullable ResourceKey<Level> dimension, Collection<BlockPos> adjacent) {
		DimensionTransformer t = transformer(dimension);
		if (!t.isWrapped() || adjacent.isEmpty()) {
			return adjacent;
		}
		Set<BlockPos> out = new LinkedHashSet<>(adjacent.size() * 5);
		for (BlockPos pos : adjacent) {
			out.add(t.Block.wrap(pos));
			out.add(t.Block.wrap(pos.offset(1, 0, 0)));
			out.add(t.Block.wrap(pos.offset(-1, 0, 0)));
			out.add(t.Block.wrap(pos.offset(0, 0, 1)));
			out.add(t.Block.wrap(pos.offset(0, 0, -1)));
		}
		return out;
	}

	/**
	 * Seam stitching normally happens while Create discovers track, so a whole-network pass is only
	 * needed to heal graphs that were saved before this compat layer existed. Track which dimensions
	 * still owe that one pass instead of polling every network forever.
	 * <p>
	 * The session is identified by the running server through a weak reference, so a second world
	 * load in the same game gets its own heal pass without pinning the previous server in memory.
	 */
	private static final Set<ResourceKey<Level>> HEALED_DIMENSIONS = ConcurrentHashMap.newKeySet();
	private static WeakReference<MinecraftServer> healSession = new WeakReference<>(null);

	/** Clears one-shot seam-heal bookkeeping when the server session ends. */
	public static synchronized void clearHealSession() {
		HEALED_DIMENSIONS.clear();
		healSession = new WeakReference<>(null);
	}

	/** True the first time a dimension asks in a given server session. */
	public static synchronized boolean consumeSeamHeal(@Nullable ResourceKey<Level> dimension) {
		if (dimension == null) {
			return false;
		}
		MinecraftServer server = TransformerRequests.server;
		if (healSession.get() != server) {
			healSession = new WeakReference<>(server);
			HEALED_DIMENSIONS.clear();
		}
		return HEALED_DIMENSIONS.add(dimension);
	}

	/**
	 * Create's {@code TrackPropagator} aborts graph walks after a fixed 1000 steps.
	 * On large planets a single continuous loop can exceed that, so expand the budget
	 * from the active transformer's block-domain size (scale-independent).
	 */
	public static int graphWalkBudget(int vanillaBudget) {
		DimensionTransformer t = bestEffortTransformer();
		if (!t.isWrapped()) {
			return vanillaBudget;
		}
		int domain = Math.max(t.Coord.X.domainLength, t.Coord.Z.domainLength);
		// Four ends per block worst-case + padding; never shrink below vanilla.
		return Math.max(vanillaBudget, domain * 4 + 256);
	}
}
