package com.planetworld.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.render.CurvatureRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Enables curvature uniforms only while the world is drawing (not GUI/hotbar).
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
}
