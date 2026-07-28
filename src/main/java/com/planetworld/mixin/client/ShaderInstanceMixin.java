package com.planetworld.mixin.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.planetworld.render.CurvatureRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uploads Planet World curvature uniforms into any core shader that declares them.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {
	@Inject(method = "apply", at = @At("HEAD"))
	private void planetworld$uploadCurvature(CallbackInfo ci) {
		ShaderInstance self = (ShaderInstance) (Object) this;
		Uniform curvature = self.getUniform("PlanetCurvature");
		if (curvature == null) {
			return;
		}
		curvature.set(CurvatureRenderer.shaderCurvatureFactor());
		Uniform wrapHalf = self.getUniform("PlanetWrapHalf");
		if (wrapHalf != null) {
			wrapHalf.set(CurvatureRenderer.shaderWrapHalf());
		}
	}
}
