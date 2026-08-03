package com.planetworld.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Tracks continuous meridian Z across torus wraps so celestial tip does not flip
 * when the player crosses the polar seam ({@code +half ≡ −half}).
 * <p>
 * Wrapped {@code z/half} jumps from ≈+1 to ≈−1 at that seam (south→north in one step),
 * which used to reverse the sun/moon. Unwrapping + triangle-fold latitude behaves like
 * walking over a geographic pole: tip eases through the pole instead of teleporting.
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuousMeridian {
	private static double unwrapOffset;
	private static double lastWrappedZ = Double.NaN;

	private ContinuousMeridian() {
	}

	public static void reset() {
		unwrapOffset = 0.0;
		lastWrappedZ = Double.NaN;
	}

	/**
	 * Continuously unwrapped Z: when the entity teleports across the wrap by ≈±period,
	 * accumulate an offset so the meridian does not jump.
	 */
	public static double continuousZ(double wrappedZ, double period) {
		if (!(period > 1.0e-3) || Double.isNaN(wrappedZ)) {
			return wrappedZ;
		}
		if (Double.isNaN(lastWrappedZ)) {
			lastWrappedZ = wrappedZ;
			return wrappedZ + unwrapOffset;
		}
		double dz = wrappedZ - lastWrappedZ;
		double half = period * 0.5;
		if (dz > half) {
			unwrapOffset -= period;
		} else if (dz < -half) {
			unwrapOffset += period;
		}
		lastWrappedZ = wrappedZ;
		return wrappedZ + unwrapOffset;
	}

	/**
	 * Latitude in {@code [-1, 1]} along a continuous meridian.
	 * Equator at {@code 0}, south pole at {@code +1}, north at {@code -1};
	 * past a pole the value eases back toward the equator (Earth-like crossing).
	 */
	public static double latitude(double continuousZ, double halfPeriod) {
		if (!(halfPeriod > 1.0e-3)) {
			return 0.0;
		}
		return triangleLatitude(continuousZ / halfPeriod);
	}

	/**
	 * Period-4 triangle in units of half-period:
	 * {@code 0 → 1 → 0 → -1 → 0}.
	 */
	static double triangleLatitude(double x) {
		double t = x % 4.0;
		if (t < 0.0) {
			t += 4.0;
		}
		if (t <= 1.0) {
			return t;
		}
		if (t <= 3.0) {
			return 2.0 - t;
		}
		return t - 4.0;
	}
}
