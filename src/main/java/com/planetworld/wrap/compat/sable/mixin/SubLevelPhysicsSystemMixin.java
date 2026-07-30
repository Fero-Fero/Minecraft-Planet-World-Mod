package com.planetworld.wrap.compat.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.PlanetWorld;
import com.planetworld.wrap.compat.sable.SableWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Keeps a sub-level's parent-world pose inside the torus domain the same way {@code EntityMixin}
 * keeps entity positions: wrap the translation, shift every derived previous pose by the same
 * amount, and teleport the rigid body so the next physics read does not reintroduce an out-of-domain
 * sample.
 * <p>
 * Velocity is {@code pose - lastPose}. Without the shortest-path rebases below, a pose that hops a
 * bound looks like a world-width step and yeets the vehicle. Sable is reached by name; its companion
 * types are not on the compile classpath.
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", remap = false, priority = 2000)
public abstract class SubLevelPhysicsSystemMixin {
	@Shadow(remap = false)
	public abstract ServerLevel getLevel();

	@Unique
	private static volatile MethodHandle planetworld$logicalPose;
	@Unique
	private static volatile MethodHandle planetworld$lastPose;
	@Unique
	private static volatile MethodHandle planetworld$lastNetworkedPose;
	@Unique
	private static volatile MethodHandle planetworld$posePosition;
	@Unique
	private static volatile MethodHandle planetworld$poseOrientation;
	@Unique
	private static volatile MethodHandle planetworld$teleport;
	@Unique
	private static volatile Field planetworld$pipelineField;

	/**
	 * When the pipeline pose sits on the far side of a bound from {@code lastPose}, measure the
	 * short path so {@code latestLinearVelocity} is not the circumference.
	 */
	@WrapOperation(
			method = "updatePose",
			at = @At(
					value = "INVOKE",
					target = "Lorg/joml/Vector3d;sub(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
			)
	)
	private Vector3d planetworld$shortestLinearDelta(
			Vector3d posePosition,
			Vector3dc lastPosition,
			Vector3d dest,
			Operation<Vector3d> original
	) {
		DimensionTransformer transformer = SableWrapMath.worldTransformer(getLevel());
		if (!SableWrapMath.needsUnwrap(transformer, posePosition, lastPosition)) {
			return original.call(posePosition, lastPosition, dest);
		}
		return original.call(posePosition, SableWrapMath.unwrapOnto(transformer, posePosition, lastPosition), dest);
	}

	/**
	 * After velocity is stored, fold the world pose into the domain and keep history / physics in
	 * the same frame so the next tick does not undo the hop.
	 */
	@Inject(method = "updatePose", at = @At("RETURN"))
	private void planetworld$wrapWorldPose(Object subLevel, CallbackInfo ci) {
		DimensionTransformer transformer = SableWrapMath.worldTransformer(getLevel());
		if (!transformer.isWrapped()) {
			return;
		}

		try {
			Object logicalPose = planetworld$logicalPose().invoke(subLevel);
			Vector3d posePos = planetworld$mutablePosition(logicalPose);
			Vector3d shift = SableWrapMath.wrapTranslationShift(transformer, posePos);
			if (shift == null) {
				return;
			}

			planetworld$mutablePosition(planetworld$lastPose().invoke(subLevel)).add(shift);
			planetworld$mutablePosition(planetworld$lastNetworkedPose().invoke(subLevel)).add(shift);

			Quaterniondc orientation = (Quaterniondc) planetworld$poseOrientation().invoke(logicalPose);
			Object pipeline = planetworld$pipeline(this);
			planetworld$teleport().invoke(pipeline, subLevel, posePos, orientation);
		} catch (Throwable t) {
			PlanetWorld.LOGGER.error(
					"Planet World failed to keep a Sable sub-level pose inside the wrapped domain",
					t
			);
		}
	}

	@Unique
	private static Vector3d planetworld$mutablePosition(Object pose) throws Throwable {
		Object position = planetworld$posePosition().invoke(pose);
		if (position instanceof Vector3d mutable) {
			return mutable;
		}
		throw new IllegalStateException(
				"Expected a mutable JOML Vector3d from Pose3d.position(), got "
						+ (position == null ? "null" : position.getClass().getName())
		);
	}

	@Unique
	private static Object planetworld$pipeline(Object physicsSystem) throws ReflectiveOperationException {
		Field field = planetworld$pipelineField;
		if (field == null) {
			Class<?> type = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
			field = type.getDeclaredField("pipeline");
			field.setAccessible(true);
			planetworld$pipelineField = field;
		}
		return field.get(physicsSystem);
	}

	@Unique
	private static MethodHandle planetworld$logicalPose() throws Throwable {
		MethodHandle handle = planetworld$logicalPose;
		if (handle == null) {
			Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
			handle = MethodHandles.publicLookup().findVirtual(
					subLevel,
					"logicalPose",
					MethodType.methodType(Class.forName("dev.ryanhcode.sable.companion.math.Pose3d"))
			);
			planetworld$logicalPose = handle;
		}
		return handle;
	}

	@Unique
	private static MethodHandle planetworld$lastPose() throws Throwable {
		MethodHandle handle = planetworld$lastPose;
		if (handle == null) {
			Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
			handle = MethodHandles.publicLookup().findVirtual(
					subLevel,
					"lastPose",
					MethodType.methodType(Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc"))
			);
			planetworld$lastPose = handle;
		}
		return handle;
	}

	@Unique
	private static MethodHandle planetworld$lastNetworkedPose() throws Throwable {
		MethodHandle handle = planetworld$lastNetworkedPose;
		if (handle == null) {
			Class<?> serverSubLevel = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
			handle = MethodHandles.publicLookup().findVirtual(
					serverSubLevel,
					"lastNetworkedPose",
					MethodType.methodType(Class.forName("dev.ryanhcode.sable.companion.math.Pose3d"))
			);
			planetworld$lastNetworkedPose = handle;
		}
		return handle;
	}

	@Unique
	private static MethodHandle planetworld$posePosition() throws Throwable {
		MethodHandle handle = planetworld$posePosition;
		if (handle == null) {
			// Pose3dc.position() is what lastPose() exposes; Pose3d implements it with the mutable field.
			Class<?> pose3dc = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
			handle = MethodHandles.publicLookup().findVirtual(
					pose3dc,
					"position",
					MethodType.methodType(Vector3dc.class)
			);
			planetworld$posePosition = handle;
		}
		return handle;
	}

	@Unique
	private static MethodHandle planetworld$poseOrientation() throws Throwable {
		MethodHandle handle = planetworld$poseOrientation;
		if (handle == null) {
			Class<?> pose3d = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
			handle = MethodHandles.publicLookup().findVirtual(
					pose3d,
					"orientation",
					MethodType.methodType(org.joml.Quaterniond.class)
			);
			planetworld$poseOrientation = handle;
		}
		return handle;
	}

	@Unique
	private static MethodHandle planetworld$teleport() throws Throwable {
		MethodHandle handle = planetworld$teleport;
		if (handle == null) {
			Class<?> pipeline = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipeline");
			Class<?> body = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipelineBody");
			handle = MethodHandles.publicLookup().findVirtual(
					pipeline,
					"teleport",
					MethodType.methodType(void.class, body, Vector3dc.class, Quaterniondc.class)
			);
			planetworld$teleport = handle;
		}
		return handle;
	}
}
