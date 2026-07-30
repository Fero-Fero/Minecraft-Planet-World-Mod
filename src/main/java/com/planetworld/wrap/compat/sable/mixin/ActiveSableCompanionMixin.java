package com.planetworld.wrap.compat.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.planetworld.wrap.compat.sable.SableWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sable's one measuring point for parent-world distance.
 * <p>
 * Every {@code …WithSubLevels} overload first projects both operands out of whichever sub-level holds
 * them and then measures the result, so the final call is a plain parent-world comparison — exactly
 * the place a torus needs the short path and exactly the place where nothing is in sub-level local
 * space any more. Patching it here fixes every Sable query at once, including the
 * {@code Entity.distanceTo} / {@code distanceToSqr} bodies Sable {@code @Overwrite}s, instead of
 * fighting Sable for ownership of those methods.
 * <p>
 * Sable is reached by name because its {@code sable-companion} API only exists nested inside its own
 * jar, so its classes do not resolve at compile time.
 */
@Mixin(targets = "dev.ryanhcode.sable.ActiveSableCompanion", remap = false, priority = 2000)
public abstract class ActiveSableCompanionMixin {
	@WrapOperation(
			method = "distanceSquaredWithSubLevels",
			at = @At(value = "INVOKE", target = "Lorg/joml/Vector3dc;distanceSquared(Lorg/joml/Vector3dc;)D")
	)
	private double planetworld$shortestWorldDistanceSquared(
			Vector3dc from,
			Vector3dc to,
			Operation<Double> original,
			@Local(argsOnly = true) Level level
	) {
		return original.call(from, planetworld$shortestPath(level, from, to));
	}

	@WrapOperation(
			method = "rectilinearDistanceWithSubLevels",
			at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/ActiveSableCompanion;rectilinearDistance(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)D")
	)
	private double planetworld$shortestWorldRectilinearDistance(
			Vector3dc from,
			Vector3dc to,
			Operation<Double> original,
			@Local(argsOnly = true) Level level
	) {
		return original.call(from, planetworld$shortestPath(level, from, to));
	}

	@Unique
	private static Vector3dc planetworld$shortestPath(Level level, Vector3dc from, Vector3dc to) {
		DimensionTransformer transformer = SableWrapMath.worldTransformer(level);
		if (!SableWrapMath.needsUnwrap(transformer, from, to)) {
			return to;
		}
		return SableWrapMath.unwrapOnto(transformer, from, to);
	}
}
