package com.planetworld.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.planetworld.compat.SodiumCompat;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.render.CurvatureRenderer;
import com.planetworld.render.LocalSkyHandler;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Curvature scope for world draw, plus latitude sky tip (+ fixed orbital obliquity).
 * <p>
 * Tip the polar axis (Z) <em>after</em> {@code YP(-90)} and <em>before</em>
 * {@code XP(time)} so the day cycle still spins the sun around a tipped axis.
 * Tip advances with unwrapped meridian progress (past ±90° onto the far face)
 * so N/S travel does not pendulum-fold. Far-face opposite day is tip ~180°, not θ+½.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@WrapMethod(method = "renderLevel")
	private void planetworld$curvatureScope(
			DeltaTracker deltaTracker,
			boolean renderBlockOutline,
			Camera camera,
			GameRenderer gameRenderer,
			LightTexture lightTexture,
			Matrix4f frustumMatrix,
			Matrix4f projectionMatrix,
			Operation<Void> original
	) {
		CurvatureRenderer.beginLevelRender();
		try {
			original.call(deltaTracker, renderBlockOutline, camera, gameRenderer, lightTexture, frustumMatrix, projectionMatrix);
		} finally {
			CurvatureRenderer.endLevelRender();
		}
	}

	/**
	 * Ordinal 0 in the rain-level slice is {@code YP(-90)}. Apply observer tip next;
	 * ordinal 1 ({@code XP(time)}) then spins around that tipped axis.
	 */
	@WrapOperation(
			method = "renderSky",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
					ordinal = 0
			),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"
					)
			)
	)
	private void planetworld$tiltCelestialSphere(PoseStack poseStack, Quaternionf rotation, Operation<Void> original) {
		original.call(poseStack, rotation);
		if (!SodiumCompat.shouldApplyVanillaSkyTilt()) {
			return;
		}
		float tilt = LocalSkyHandler.localCelestialTiltDegrees();
		if (Math.abs(tilt) >= 0.05f) {
			poseStack.mulPose(new Quaternionf(new AxisAngle4f(
					(float) Math.toRadians(tilt),
					0.0f,
					0.0f,
					1.0f
			)));
		}
	}

	/**
	 * Second {@code getTimeOfDay} in {@code renderSky} drives {@code XP(time)} disc spin.
	 * Keep that on the X-only clock; atmosphere already uses tip-aware time via
	 * {@link ClientLevelMixin}.
	 */
	@WrapOperation(
			method = "renderSky",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F",
					ordinal = 1
			)
	)
	private float planetworld$discSpinTimeOfDay(ClientLevel level, float partialTick, Operation<Float> original) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return original.call(level, partialTick);
		}
		return LocalSkyHandler.localCelestialAngle();
	}

	/** Scale sun quad (vanilla 30) — 50% default × season. */
	@ModifyConstant(
			method = "renderSky",
			constant = @Constant(floatValue = 30.0F),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"
					),
					to = @At(
							value = "FIELD",
							target = "Lnet/minecraft/client/renderer/LevelRenderer;MOON_LOCATION:Lnet/minecraft/resources/ResourceLocation;"
					)
			)
	)
	private float planetworld$seasonSunSize(float original) {
		return original * LocalSkyHandler.seasonSunScale();
	}

	/** Scale moon quad (vanilla 20) — 50% default. Assignment is before {@code MOON_LOCATION} bind. */
	@ModifyConstant(
			method = "renderSky",
			constant = @Constant(floatValue = 20.0F),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"
					),
					to = @At(
							value = "INVOKE",
							target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F"
					)
			)
	)
	private float planetworld$moonSize(float original) {
		return original * LocalSkyHandler.moonDiscScale();
	}
}
