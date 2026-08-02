package com.planetworld.wrap.compat.sable;

import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Torus geometry for the coordinate space Sable and Planet World share: the parent level.
 * <h2>Coordinate ownership</h2>
 * Sable moves a point through three frames, and only the first one belongs to Planet World:
 * <ul>
 *   <li><b>Parent world</b> — ordinary level coordinates, including the plot region a sub-level's
 *       blocks are stored in. Distances and deltas here must take the short way around the planet.</li>
 *   <li><b>Sub-level local</b> — offsets relative to a sub-level pose. A vehicle is a few dozen blocks
 *       across, so these never straddle a bound and wrapping them would tear the vehicle apart.</li>
 *   <li><b>Rapier physics</b> — the rigid-body frame behind the pose. Not reachable from level
 *       coordinates at all.</li>
 * </ul>
 * Every helper here therefore takes a pair of <em>parent world</em> points and only ever moves the
 * second one onto the representative nearest the first. Callers must resolve sub-level projection
 * before asking, which is why the compat mixins hook Sable <em>after</em> it has projected out of a
 * sub-level rather than before.
 * <p>
 * Keep this class free of Sable types. Sable's {@code sable-companion} API is shipped nested inside
 * its own jar and is not published anywhere this build resolves from, so the compat mixins reach
 * Sable by name; {@code -Psable_jar=<path>} is the hook for compiling against it directly.
 */
public final class SableWrapMath {
	private SableWrapMath() {
	}

	/**
	 * Wrapping applies to server geometry only: the client keeps continuous coordinates so cameras
	 * and interpolation do not jump when a vehicle crosses a bound.
	 */
	public static DimensionTransformer worldTransformer(@Nullable Level level) {
		if (level == null) {
			return DimensionTransformer.DISABLED;
		}
		DimensionTransformer transformer = level.getTransformer();
		return transformer == null ? DimensionTransformer.DISABLED : transformer.SSO();
	}

	public static boolean needsUnwrap(DimensionTransformer transformer, Vector3dc reference, Vector3dc point) {
		return transformer.isWrapped()
				&& (transformer.Coord.X.needsUnwrap(reference.x(), point.x())
				|| transformer.Coord.Z.needsUnwrap(reference.z(), point.z()));
	}

	/**
	 * The representative of {@code point} that sits on the short torus path from {@code reference},
	 * which may land outside the wrapped domain — that is what makes the following subtraction or
	 * distance measure the near way around instead of the far way.
	 */
	public static Vector3d unwrapOnto(DimensionTransformer transformer, Vector3dc reference, Vector3dc point) {
		return new Vector3d(
				transformer.Coord.X.unwrap(reference.x(), point.x()),
				point.y(),
				transformer.Coord.Z.unwrap(reference.z(), point.z())
		);
	}

	/**
	 * Folds a mutable parent-world translation into the domain and returns the (x, z) shift applied,
	 * or {@code null} when already inside. Callers must add the same shift to every previous pose
	 * derived from this translation ({@code lastPose}, {@code lastNetworkedPose}, …).
	 */
	@Nullable
	public static Vector3d wrapTranslationShift(DimensionTransformer transformer, Vector3d position) {
		if (!transformer.isWrapped()) {
			return null;
		}
		double x = position.x;
		double z = position.z;
		double wrappedX = transformer.Coord.X.wrap(x);
		double wrappedZ = transformer.Coord.Z.wrap(z);
		if (wrappedX == x && wrappedZ == z) {
			return null;
		}
		position.x = wrappedX;
		position.z = wrappedZ;
		return new Vector3d(wrappedX - x, 0.0, wrappedZ - z);
	}
}
