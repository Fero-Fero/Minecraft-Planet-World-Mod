/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.core;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Optional parent-world projection before torus distance.
 * <p>
 * Core mixins must not name Sable. When a rider stands in a sub-level plot, their entity position is
 * not the vehicle's world pose — Sable first projects out of the sub-level, then distance is
 * measured. This hook is identity until a compat layer registers a projector (Sable does so at
 * common setup). Measure with {@link DimensionTransformer} on the projected points; do not take
 * {@code min(torus, sable)}.
 */
public final class WorldSpaceProjector {
	@FunctionalInterface
	public interface Projector {
		Vec3 project(Level level, double x, double y, double z);
	}

	private static final Projector IDENTITY = (level, x, y, z) -> new Vec3(x, y, z);

	private static volatile Projector projector = IDENTITY;

	private WorldSpaceProjector() {
	}

	public static void set(@Nullable Projector next) {
		projector = next == null ? IDENTITY : next;
	}

	public static Vec3 project(Level level, double x, double y, double z) {
		return projector.project(level, x, y, z);
	}

	public static Vec3 project(Level level, Vec3 pos) {
		return projector.project(level, pos.x, pos.y, pos.z);
	}
}
