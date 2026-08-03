package com.planetworld.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.planetworld.compat.SodiumCompat;
import com.planetworld.render.CurvatureRenderer;
import com.planetworld.render.LocalSkyHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Curvature scope for world draw, plus latitude sky tilt so the sun arcs toward
 * the opposite pole as the player leaves the equator.
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

	@WrapOperation(
			method = "renderSky",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
					ordinal = 0
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
					1.0f,
					0.0f,
					0.0f
			)));
		}
		float sunScale = LocalSkyHandler.localSunScale();
		if (Math.abs(sunScale - 1.0f) > 0.01f) {
			poseStack.scale(sunScale, sunScale, sunScale);
		}
	}
}
