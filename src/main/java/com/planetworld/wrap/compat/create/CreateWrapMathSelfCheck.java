package com.planetworld.wrap.compat.create;

import com.planetworld.config.PlanetSettings;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.options.DimensionWrappingSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Lightweight self-check for wrap math used by Create circumnavigation.
 * Invoked from the {@code verifyCreateWrapMath} Gradle task (no full game boot).
 * <p>
 * Runs against every official UI circumference step ({@link PlanetSettings#CIRCUMFERENCE_STEPS})
 * so seam identity/distance stay correct from 256 through 102400.
 */
public final class CreateWrapMathSelfCheck {
	private CreateWrapMathSelfCheck() {
	}

	public static void main(String[] args) {
		for (int halfPeriod : PlanetSettings.CIRCUMFERENCE_STEPS) {
			checkHalfPeriod(halfPeriod);
		}
		System.out.println("CreateWrapMathSelfCheck OK for circumferences "
				+ java.util.Arrays.toString(PlanetSettings.CIRCUMFERENCE_STEPS));
	}

	private static void checkHalfPeriod(int halfPeriodBlocks) {
		if (halfPeriodBlocks % 16 != 0) {
			fail("half-period must be a multiple of 16, got " + halfPeriodBlocks);
		}
		int halfChunks = halfPeriodBlocks / 16;
		DimensionTransformer t = new DimensionTransformer(
				new DimensionWrappingSettings(
						-halfChunks, halfChunks, -halfChunks, halfChunks,
						DimensionWrappingSettings.Axis.X, 0, true
				),
				false
		);
		if (!t.isWrapped()) {
			fail("transformer should be wrapped at half-period " + halfPeriodBlocks);
		}
		if (t.Coord.X.domainLength != halfPeriodBlocks * 2
				|| t.Coord.Z.domainLength != halfPeriodBlocks * 2) {
			fail("domainLength mismatch at half-period " + halfPeriodBlocks
					+ ": X=" + t.Coord.X.domainLength + " Z=" + t.Coord.Z.domainLength);
		}

		CreateWrapMath.pushOverride(t);
		try {
			int upper = halfPeriodBlocks;
			int lower = -halfPeriodBlocks;
			BlockPos nearMax = new BlockPos(0, 64, upper - 1);
			BlockPos nearMin = new BlockPos(0, 64, lower);
			BlockPos unwrappedMin = t.Block.unwrap(nearMax, nearMin);
			double distSqr = distSqr(nearMax, unwrappedMin);
			if (distSqr > 4.0) {
				fail("C=" + halfPeriodBlocks + ": wrap-adjacent Z posts should be ~1 block apart, got distSqr="
						+ distSqr + " unwrapped=" + unwrappedMin);
			}

			Vec3 a = new Vec3(0.5, 64.0, upper - 0.5);
			Vec3 b = new Vec3(0.5, 64.0, lower + 0.5);
			double edgeLen = CreateWrapMath.unwrapDistance(t, a, b);
			if (edgeLen < 0.5 || edgeLen > 3.0) {
				fail("C=" + halfPeriodBlocks + ": expected short seam edge length, got " + edgeLen);
			}

			Vec3 mid = CreateWrapMath.lerpWrapped(t, 0.5f, a, b);
			if (t.Coord.Z.isOver(mid.z)) {
				fail("C=" + halfPeriodBlocks + ": lerped midpoint should wrap into domain, got " + mid);
			}

			// Seam-spanning graph nodes must NOT Euclidean-lerp through world center (spawn).
			Vec3 beforeNode = new Vec3(0.5, 64.0, upper - 16.0);
			Vec3 afterNode = new Vec3(0.5, 64.0, lower + 16.0);
			Vec3 seamMid = CreateWrapMath.lerpWrapped(t, 0.5f, beforeNode, afterNode);
			if (Math.abs(seamMid.z) < halfPeriodBlocks * 0.25) {
				fail("C=" + halfPeriodBlocks + ": seam mid must stay near the cut, not spawn; got " + seamMid);
			}
			double vanillaMidZ = (beforeNode.z + afterNode.z) * 0.5;
			if (Math.abs(vanillaMidZ) < 1.0 && Math.abs(seamMid.z - vanillaMidZ) < 1.0) {
				fail("C=" + halfPeriodBlocks + ": wrap lerp collapsed to Euclidean spawn mid");
			}

			int packedUpper = upper * 2;
			int packedLower = lower * 2;
			if (CreateWrapMath.canonicalPackedZ(packedUpper) != CreateWrapMath.canonicalPackedZ(packedLower)) {
				fail("C=" + halfPeriodBlocks + ": canonical packed Z for " + upper + " and " + lower
						+ " must match: " + CreateWrapMath.canonicalPackedZ(packedUpper)
						+ " vs " + CreateWrapMath.canonicalPackedZ(packedLower));
			}
			if (!CreateWrapMath.samePackedLocation(0, 128, packedUpper, 0, 0, 128, packedLower, 0)) {
				fail("C=" + halfPeriodBlocks + ": samePackedLocation should treat packed Z "
						+ packedUpper + " and " + packedLower + " as identical");
			}
			if (!CreateWrapMath.needsPackedCanonicalization(0, packedUpper)) {
				fail("C=" + halfPeriodBlocks + ": packed Z " + packedUpper
						+ " (world " + upper + ") must need canonicalization");
			}

			BlockPos acrossSeam = t.Block.wrap(nearMax.offset(0, 0, 1));
			if (!acrossSeam.equals(nearMin)) {
				fail("C=" + halfPeriodBlocks + ": expected wrap(" + (upper - 1) + "+1) == " + lower
						+ ", got " + acrossSeam);
			}

			BlockPos wrappedOutside = t.Block.wrap(new BlockPos(0, 64, upper));
			if (!wrappedOutside.equals(nearMin)) {
				fail("C=" + halfPeriodBlocks + ": block " + upper + " should wrap to " + lower
						+ ", got " + wrappedOutside);
			}

			double endA = t.Coord.Z.wrap((double) upper);
			double endB = (double) lower;
			if (Math.abs(endA - endB) > 1.0e-6) {
				fail("C=" + halfPeriodBlocks + ": seam ends " + upper + " and " + lower
						+ " must wrap to the same coordinate, got " + endA + " vs " + endB);
			}

			if (!CreateWrapMath.isSeamVec(new Vec3(0.5, 64.0, lower), null)) {
				fail("C=" + halfPeriodBlocks + ": lower seam " + lower + " should be detected");
			}

			int walkBudget = CreateWrapMath.graphWalkBudget(1000);
			int minBudget = t.Coord.Z.domainLength * 4 + 256;
			if (walkBudget < minBudget) {
				fail("C=" + halfPeriodBlocks + ": graph walk budget " + walkBudget
						+ " too small for domain, need >= " + minBudget);
			}

			checkShortestDelta(t, halfPeriodBlocks);
			checkSeamMargin(t, halfPeriodBlocks);
			checkBogeyGeometry(t, halfPeriodBlocks);
			checkPlacementRebase(t, halfPeriodBlocks);

			// 16-block Create segment across the seam must be a short unwrap / long Euclidean pair
			Vec3 beforeSeam = new Vec3(0.5, 64.0, upper - 16.0);
			Vec3 afterSeam = new Vec3(0.5, 64.0, lower);
			double segUnwrap = CreateWrapMath.unwrapDistance(t, beforeSeam, afterSeam);
			double segEuclid = beforeSeam.distanceTo(afterSeam);
			if (segUnwrap > 16.5 || segEuclid < segUnwrap + 8.0) {
				fail("C=" + halfPeriodBlocks + ": expected seam segment unwrap<=16.5 and long Euclidean, got unwrap="
						+ segUnwrap + " euclid=" + segEuclid);
			}
		} finally {
			CreateWrapMath.popOverride();
		}
	}

	/**
	 * Track connect across a bound: wrapped +250 and continuous -252 must rebase to a ~10-block
	 * chord, and station TargetTrack offsets must stay short — not a full circumference.
	 */
	private static void checkPlacementRebase(DimensionTransformer t, int halfPeriodBlocks) {
		BlockPos wrappedOutside = new BlockPos(-234, 104, halfPeriodBlocks - 6); // e.g. +250 at C=256
		// User case: one end past the cut (continuous -262 ≡ wrapped +250), other at -252.
		BlockPos continuousPast = new BlockPos(-234, 104, -(halfPeriodBlocks + 6));
		BlockPos insideNear = new BlockPos(-242, 104, -(halfPeriodBlocks - 4));
		BlockPos rebased = CreateWrapMath.unwrapBlock(t, insideNear, t.Block.wrap(continuousPast));
		double chord = Math.sqrt(distSqr(rebased, insideNear));
		if (chord > 32.0) {
			fail("C=" + halfPeriodBlocks + ": rebased connect chord should be short, got " + chord
					+ " from " + rebased + " to " + insideNear);
		}
		BlockPos longWay = CreateWrapMath.shortestBlockOffset(t, insideNear, t.Block.wrap(continuousPast));
		if (Math.abs(longWay.getZ()) > 32 || Math.abs(longWay.getX()) > 32) {
			fail("C=" + halfPeriodBlocks + ": station relative offset must be short-path, got " + longWay);
		}
		// Sanity: wrappedOutside near +bound should unwrap next to insideNear when that is the ref.
		BlockPos fromWrapped = CreateWrapMath.unwrapBlock(t, insideNear, wrappedOutside);
		if (Math.sqrt(distSqr(fromWrapped, insideNear)) > 32.0) {
			fail("C=" + halfPeriodBlocks + ": unwrap(wrapped outside) should sit near insideNear, got "
					+ fromWrapped);
		}
	}

	/**
	 * A step over a bound must measure the step, not the world. This is the geometry behind movement
	 * statistics (hunger) and behind rebasing an entity's previous position when it wraps.
	 */
	private static void checkShortestDelta(DimensionTransformer t, int halfPeriodBlocks) {
		double before = halfPeriodBlocks - 0.5;
		double after = t.Coord.Z.wrap(halfPeriodBlocks + 0.5);
		double rawDelta = after - before;
		double shortest = t.Coord.Z.shortestDelta(rawDelta);
		if (Math.abs(shortest - 1.0) > 1.0e-9) {
			fail("C=" + halfPeriodBlocks + ": a 1-block step across the bound must measure 1, got "
					+ shortest + " (raw " + rawDelta + ")");
		}
		if (Math.abs(t.Coord.Z.sqrDistToBounds(rawDelta) - 1.0) > 1.0e-9) {
			fail("C=" + halfPeriodBlocks + ": squared seam distance must match the short step, got "
					+ t.Coord.Z.sqrDistToBounds(rawDelta));
		}

		// Rebasing a previous position by the wrap shift must preserve the intended step.
		double previous = before;
		double incoming = halfPeriodBlocks + 0.5;
		double shift = t.Coord.Z.wrap(incoming) - incoming;
		double rebasedStep = t.Coord.Z.wrap(incoming) - (previous + shift);
		if (Math.abs(rebasedStep - (incoming - previous)) > 1.0e-9) {
			fail("C=" + halfPeriodBlocks + ": rebasing the previous position changed the step: "
					+ rebasedStep + " vs " + (incoming - previous));
		}
	}

	/**
	 * A bogey mid-crossing has one wheel each side of a bound. Create averages and subtracts those
	 * two positions directly, which parks the carriage at the world center and reports a wheelbase of
	 * a whole world, so the torus versions have to hold no matter where the crossing happens.
	 */
	private static void checkBogeyGeometry(DimensionTransformer t, int halfPeriodBlocks) {
		double wheelSpacing = 1.0;
		Vec3 leadingWheel = new Vec3(0.5, 64.0, t.Coord.Z.wrap(halfPeriodBlocks + 0.5));
		Vec3 trailingWheel = new Vec3(0.5, 64.0, halfPeriodBlocks - 0.5);

		double wheelbase = CreateWrapMath.unwrapDistance(t, leadingWheel, trailingWheel);
		if (Math.abs(wheelbase - wheelSpacing) > 1.0e-9) {
			fail("C=" + halfPeriodBlocks + ": wheelbase across the bound must stay " + wheelSpacing
					+ ", got " + wheelbase);
		}
		double wheelbaseSqr = CreateWrapMath.unwrapDistanceSqr(t, leadingWheel, trailingWheel);
		if (Math.abs(wheelbaseSqr - wheelSpacing * wheelSpacing) > 1.0e-9) {
			fail("C=" + halfPeriodBlocks + ": squared wheelbase across the bound must stay "
					+ wheelSpacing * wheelSpacing + ", got " + wheelbaseSqr);
		}

		Vec3 delta = CreateWrapMath.shortestDelta(t, trailingWheel, leadingWheel);
		if (Math.abs(delta.length() - wheelSpacing) > 1.0e-9 || delta.z <= 0.0) {
			fail("C=" + halfPeriodBlocks + ": step to the wheel past the bound must be one block forward, got "
					+ delta);
		}

		Vec3 anchor = CreateWrapMath.midpoint(t, leadingWheel, trailingWheel);
		if (t.Coord.Z.isOver(anchor.z)) {
			fail("C=" + halfPeriodBlocks + ": bogey anchor must stay inside the domain, got " + anchor);
		}
		double gapToWheel = Math.abs(t.Coord.Z.shortestDelta(anchor.z - trailingWheel.z));
		if (gapToWheel > wheelSpacing) {
			fail("C=" + halfPeriodBlocks + ": bogey anchor must sit between its wheels, but is "
					+ gapToWheel + " blocks from one");
		}
		if (halfPeriodBlocks > 64 && Math.abs(anchor.z) < halfPeriodBlocks - 1.0) {
			fail("C=" + halfPeriodBlocks + ": bogey anchor drifted away from the bound to " + anchor
					+ " (averaging wrapped coordinates lands at the world center)");
		}
	}

	/**
	 * Seam stitching only looks at nodes near a bound, so that margin test must be sound.
	 */
	private static void checkSeamMargin(DimensionTransformer t, int halfPeriodBlocks) {
		double margin = 16.5;
		Vec3 nearBound = new Vec3(0.5, 64.0, halfPeriodBlocks - 8.0);
		if (!CreateWrapMath.withinOfSeam(t, nearBound, margin)) {
			fail("C=" + halfPeriodBlocks + ": " + nearBound.z + " should count as near the bound");
		}
		Vec3 justInside = new Vec3(0.5, 64.0, -halfPeriodBlocks + 8.0);
		if (!CreateWrapMath.withinOfSeam(t, justInside, margin)) {
			fail("C=" + halfPeriodBlocks + ": " + justInside.z + " should count as near the bound");
		}
		if (halfPeriodBlocks > 64) {
			Vec3 center = new Vec3(0.5, 64.0, 0.5);
			if (CreateWrapMath.withinOfSeam(t, center, margin)) {
				fail("C=" + halfPeriodBlocks + ": world center must not count as near the bound");
			}
		}
	}

	private static double distSqr(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	private static void fail(String message) {
		throw new IllegalStateException(message);
	}
}
