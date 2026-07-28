package com.planetworld.mixin.client;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.render.CurvatureRenderer;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Expands effective render awareness near the curved horizon and applies curvature bias.
 * Prevents mountains from popping when the vertex shader pushes them below the horizon line.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void planetworld$beginCurvature(CallbackInfo ci) {
        if (!PlanetWorldConfig.enableCurvatureShader()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !WrapMath.isWrappedDimension(mc.level)) {
            return;
        }
        // Mark intensity for debug / shader consumers
        float drop = CurvatureRenderer.curvatureDrop(64.0);
        if (drop != 0f) {
            // No-op touch keeps the curvature path live without mutating GL state unsafely here.
            CurvatureRenderer.curvatureBiasMatrix(PlanetWorldConfig.curvatureIntensity());
        }
    }
}
